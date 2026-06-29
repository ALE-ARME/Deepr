package com.yogeshpaliyal.deepr.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.GetLinksForBackup
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import com.yogeshpaliyal.deepr.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AutoBackupWorker(
    val context: Context,
    val deeprQueries: DeeprQueries,
    val preferenceDataStore: AppPreferenceDataStore,
) {
    private val csvWriter by lazy {
        CsvWriter()
    }

    suspend fun doWork() {
        return withContext(Dispatchers.IO) {
            try {
                val enabled = preferenceDataStore.getAutoBackupEnabled.first()
                if (!enabled) {
                    return@withContext
                }

                val location = preferenceDataStore.getAutoBackupLocation.first()
                if (location.isEmpty()) {
                    return@withContext
                }

                val profilesToExport = deeprQueries.getProfilesForBackup().executeAsList()
                val linksToExport = deeprQueries.getLinksForBackup().executeAsList()

                if (profilesToExport.isEmpty() && linksToExport.isEmpty()) {
                    return@withContext
                }

                if (!location.startsWith("content://")) {
                    return@withContext
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

                val success =
                    saveToSelectedLocation(
                        location = location,
                        profiles = profilesToExport,
                        links = linksToExport,
                        settings = settings,
                    )

                if (success) {
                    // Record backup time on successful completion
                    preferenceDataStore.setLastBackupTime(System.currentTimeMillis())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun saveToSelectedLocation(
        location: String,
        fileName: String = "deepr_backup.csv",
        profiles: List<com.yogeshpaliyal.deepr.Profile>,
        links: List<GetLinksForBackup>,
        settings: Map<String, String> = emptyMap(),
    ): Boolean =
        try {
            // For content:// URIs from document picker, create a new document in that folder
            val locationUri = location.toUri()
            val directory = DocumentFile.fromTreeUri(context, locationUri)
            var docFile = directory?.findFile(fileName)
            if (docFile == null) {
                docFile =
                    DocumentFile.fromTreeUri(context, locationUri)?.createFile(
                        "text/csv",
                        fileName,
                    )
            }

            if (docFile != null) {
                context.contentResolver
                    .openOutputStream(docFile.uri, "wt")
                    ?.use { outputStream ->
                        csvWriter.writeToCsv(outputStream, profiles, links, settings)
                    }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
}
