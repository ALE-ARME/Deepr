package com.yogeshpaliyal.deepr.caldav

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class CalDavSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams),
    KoinComponent {
    private val preferenceDataStore: AppPreferenceDataStore by inject()
    private val calDavSyncManager: CalDavSyncManager by inject()

    override suspend fun doWork(): Result {
        if (!calDavSyncManager.isAvailable) {
            return Result.success()
        }
        val enabled = preferenceDataStore.getCalDavEnabled.first()
        if (!enabled) {
            return Result.success()
        }

        val triggerName = inputData.getString(KEY_TRIGGER) ?: CalDavSyncTrigger.PERIODIC.name
        val trigger =
            runCatching { CalDavSyncTrigger.valueOf(triggerName) }
                .getOrDefault(CalDavSyncTrigger.PERIODIC)

        return when (calDavSyncManager.syncNow(trigger)) {
            is CalDavSyncResult.Success -> Result.success()
            is CalDavSyncResult.Error -> {
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "caldav_periodic_sync"
        private const val DEBOUNCED_WORK_NAME = "caldav_debounced_sync"
        private const val KEY_TRIGGER = "caldav_trigger"
        private const val DEBOUNCE_DELAY_SECONDS = 15L
        private const val MAX_RETRIES = 3

        fun schedulePeriodicSync(
            context: Context,
            intervalHours: Long,
        ) {
            val safeHours = intervalHours.coerceAtLeast(1L)
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<CalDavSyncWorker>(safeHours, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_TRIGGER to CalDavSyncTrigger.PERIODIC.name))
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun scheduleDebouncedSync(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<CalDavSyncWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS)
                    .setInputData(workDataOf(KEY_TRIGGER to CalDavSyncTrigger.LINK_CHANGE.name))
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    DEBOUNCED_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }

        fun cancelDebouncedSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(DEBOUNCED_WORK_NAME)
        }
    }
}
