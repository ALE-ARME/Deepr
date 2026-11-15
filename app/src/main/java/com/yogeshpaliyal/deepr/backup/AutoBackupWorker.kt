package com.yogeshpaliyal.deepr.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.GetLinksForBackup
import com.yogeshpaliyal.deepr.R
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.util.Constants
import com.yogeshpaliyal.deepr.util.RequestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    val context: Context,
    val deeprQueries: DeeprQueries,
    val preferenceDataStore: AppPreferenceDataStore,
) {
    private val csvWriter by lazy {
        CsvWriter()
    }

    suspend fun doWork(): RequestResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val enabled = preferenceDataStore.getAutoBackupEnabled.first()
                if (!enabled) {
                    return@withContext RequestResult.Error(context.getString(R.string.auto_backup_disabled))
                }

                val location = preferenceDataStore.getAutoBackupLocation.first()
                if (location.isEmpty()) {
                    return@withContext recordError(context.getString(R.string.backup_location_not_selected))
                }

                if (!location.startsWith("content://")) {
                    return@withContext recordError(context.getString(R.string.backup_invalid_location))
                }

                if (!SafPermissionHelper.hasPersistedWritePermission(context, location)) {
                    return@withContext recordError(context.getString(R.string.backup_permission_expired))
                }

                val profilesToExport = deeprQueries.getProfilesForBackup().executeAsList()
                val linksToExport = deeprQueries.getLinksForBackup().executeAsList()

                if (profilesToExport.isEmpty() && linksToExport.isEmpty()) {
                    return@withContext RequestResult.Error(context.getString(R.string.no_data_to_export))
                }

                val settings = preferenceDataStore.collectExportableSettings().toMutableMap()

                // Resolve default and silent profiles names
                val defaultProfileId = preferenceDataStore.getDefaultProfileId.first()
                val defaultProfileName = defaultProfileId?.let { deeprQueries.getProfileById(it).executeAsOneOrNull()?.name }
                if (defaultProfileName != null) {
                    settings[Constants.Settings.DEFAULT_PROFILE_NAME] = defaultProfileName
                }

                val silentSaveProfileId = preferenceDataStore.getSilentSaveProfileId.first()
                val silentSaveProfileName = deeprQueries.getProfileById(silentSaveProfileId).executeAsOneOrNull()?.name
                if (silentSaveProfileName != null) {
                    settings[Constants.Settings.SILENT_SAVE_PROFILE_NAME] = silentSaveProfileName
                }

                val savedName =
                    saveToSelectedLocation(
                        location = location,
                        profiles = profilesToExport,
                        links = linksToExport,
                        settings = settings,
                    )

                preferenceDataStore.setLastBackupTime(System.currentTimeMillis())
                preferenceDataStore.setLastBackupError("")
                RequestResult.Success(context.getString(R.string.auto_backup_success, savedName))
            } catch (e: SecurityException) {
                val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                recordError(context.getString(R.string.backup_permission_denied, detail))
            } catch (e: Exception) {
                val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                recordError(context.getString(R.string.auto_backup_failed, detail))
            }
        }

    private suspend fun recordError(message: String): RequestResult.Error {
        preferenceDataStore.setLastBackupError(message)
        return RequestResult.Error(message)
    }

    private fun saveToSelectedLocation(
        location: String,
        fileName: String = BACKUP_FILE_NAME,
        profiles: List<com.yogeshpaliyal.deepr.Profile>,
        links: List<GetLinksForBackup>,
        settings: Map<String, String> = emptyMap(),
    ): String {
        val locationUri = location.toUri()
        val directory =
            DocumentFile.fromTreeUri(context, locationUri)
                ?: throw IOException(context.getString(R.string.backup_invalid_location))

        if (!directory.exists() || !directory.canWrite()) {
            throw SecurityException(context.getString(R.string.backup_permission_expired))
        }

        val existingFile = directory.findFile(fileName)
        val docFile =
            if (existingFile != null) {
                if (!existingFile.canWrite()) {
                    throw SecurityException(context.getString(R.string.backup_permission_expired))
                }
                existingFile
            } else {
                directory.createFile("text/csv", fileName)
                    ?: throw IOException("Could not create $fileName in selected folder")
            }

        val outputStream =
            SafPermissionHelper.openWritableSafStream(context, docFile.uri)
                ?: throw IOException("Failed to open output stream for ${docFile.uri}")

        outputStream.use { stream ->
            csvWriter.writeToCsv(stream, profiles, links, settings)
        }
        return docFile.name ?: fileName
    }

    companion object {
        private const val WORK_NAME = "local_csv_auto_backup"
        private const val DEBOUNCE_DELAY_SECONDS = 15L
        private const val BACKUP_FILE_NAME = "deepr_backup.csv"

        fun scheduleBackup(context: Context) {
            val workRequest =
                OneTimeWorkRequestBuilder<LocalAutoBackupCoroutineWorker>()
                    .setInitialDelay(DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )
        }

        fun cancelBackup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

class LocalAutoBackupCoroutineWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams),
    KoinComponent {
    private val preferenceDataStore: AppPreferenceDataStore by inject()
    private val autoBackupWorker: AutoBackupWorker by inject()

    override suspend fun doWork(): Result {
        val enabled = preferenceDataStore.getAutoBackupEnabled.first()
        if (!enabled) {
            return Result.success()
        }
        return when (autoBackupWorker.doWork()) {
            is RequestResult.Success -> Result.success()
            is RequestResult.Error -> Result.success()
        }
    }
}
