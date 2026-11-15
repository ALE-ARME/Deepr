package com.yogeshpaliyal.deepr.caldav

import android.content.Context
import android.util.Log
import com.yogeshpaliyal.deepr.DeeprQueries
import com.yogeshpaliyal.deepr.R
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class CalDavSyncManagerImpl(
    private val context: Context,
    private val deeprQueries: DeeprQueries,
    private val preferenceDataStore: AppPreferenceDataStore,
    private val appScope: CoroutineScope,
    private val client: CalDavClient = CalDavClient(),
) : CalDavSyncManager {
    override val isAvailable: Boolean = true

    private val syncMutex = Mutex()
    private val startupTriggered = AtomicBoolean(false)
    private var debouncedSyncJob: Job? = null

    private val localStore by lazy { SqlDelightCalDavLocalStore(deeprQueries) }
    private val syncEngine by lazy { CalDavSyncEngine(localStore, client) }

    private val _syncStatus = MutableStateFlow(CalDavSyncStatus())
    override val syncStatus: StateFlow<CalDavSyncStatus> = _syncStatus.asStateFlow()

    init {
        appScope.launch(Dispatchers.IO) {
            val lastTime = preferenceDataStore.getCalDavLastSyncTime.first()
            val lastStatus = preferenceDataStore.getCalDavLastSyncStatus.first()
            _syncStatus.update {
                it.copy(
                    lastSyncTime = lastTime,
                    lastSyncMessage = lastStatus,
                )
            }
            schedulePeriodicSyncIfEnabled()
        }
    }

    override fun onAppStartup() {
        if (!startupTriggered.compareAndSet(false, true)) return
        appScope.launch(Dispatchers.IO) {
            val enabled = preferenceDataStore.getCalDavEnabled.first()
            val syncOnStartup = preferenceDataStore.getCalDavSyncOnStartup.first()
            if (enabled && syncOnStartup) {
                syncNow(CalDavSyncTrigger.STARTUP)
            }
        }
    }

    override fun onLocalDataChanged() {
        appScope.launch(Dispatchers.IO) {
            val enabled = preferenceDataStore.getCalDavEnabled.first()
            val syncOnSave = preferenceDataStore.getCalDavSyncOnSave.first()
            if (!enabled || !syncOnSave) return@launch

            // Schedule persistent WorkManager backup in case app is killed quickly
            CalDavSyncWorker.scheduleDebouncedSync(context)

            // Also run an in-process debounced sync for instant multi-device feedback
            debouncedSyncJob?.cancel()
            debouncedSyncJob =
                appScope.launch(Dispatchers.IO) {
                    delay(IN_APP_DEBOUNCE_MS)
                    syncNow(CalDavSyncTrigger.LINK_CHANGE)
                }
        }
    }

    override fun schedulePeriodicSyncIfEnabled() {
        appScope.launch(Dispatchers.IO) {
            val enabled = preferenceDataStore.getCalDavEnabled.first()
            val intervalHours = preferenceDataStore.getCalDavSyncIntervalHours.first()
            if (enabled && intervalHours > 0L) {
                CalDavSyncWorker.schedulePeriodicSync(context, intervalHours)
            } else {
                CalDavSyncWorker.cancelPeriodicSync(context)
            }
        }
    }

    override suspend fun syncNow(trigger: CalDavSyncTrigger): CalDavSyncResult =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val config = loadConfig()
                if (!config.enabled) {
                    return@withLock CalDavSyncResult.Error(context.getString(R.string.caldav_not_enabled))
                }
                if (config.serverUrl.isBlank()) {
                    val msg = context.getString(R.string.caldav_error_missing_url)
                    recordError(msg)
                    return@withLock CalDavSyncResult.Error(msg)
                }

                _syncStatus.update { it.copy(isSyncing = true, isError = false) }
                try {
                    val result = syncEngine.sync(config)
                    val now = System.currentTimeMillis()
                    when (result) {
                        is CalDavSyncResult.Success -> {
                            val statusMsg =
                                context.getString(
                                    R.string.caldav_sync_success_summary,
                                    result.pushedCount,
                                    result.pulledCount,
                                    result.deletedCount,
                                )
                            preferenceDataStore.setCalDavLastSyncInfo(now, statusMsg)
                            _syncStatus.value =
                                CalDavSyncStatus(
                                    isSyncing = false,
                                    lastSyncTime = now,
                                    lastSyncMessage = statusMsg,
                                    isError = false,
                                )
                        }
                        is CalDavSyncResult.Error -> {
                            recordError(result.message)
                        }
                    }
                    result
                } catch (e: CancellationException) {
                    _syncStatus.update { it.copy(isSyncing = false) }
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "CalDAV sync failed", e)
                    val errorMsg = formatExceptionMessage(e)
                    recordError(errorMsg)
                    CalDavSyncResult.Error(errorMsg)
                }
            }
        }

    override suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): CalDavConnectionTestResult =
        withContext(Dispatchers.IO) {
            if (serverUrl.isBlank()) {
                return@withContext CalDavConnectionTestResult.Error(
                    context.getString(R.string.caldav_error_missing_url),
                )
            }
            try {
                val (resolvedBase, collections) =
                    client.discoverCollections(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        trustSelfSigned = trustSelfSigned,
                    )
                if (collections.isEmpty()) {
                    CalDavConnectionTestResult.Error(
                        context.getString(R.string.caldav_error_no_collections),
                    )
                } else {
                    CalDavConnectionTestResult.Success(
                        collections = collections,
                        resolvedBaseUrl = resolvedBase,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "CalDAV connection test failed", e)
                CalDavConnectionTestResult.Error(formatExceptionMessage(e))
            }
        }

    override suspend fun getDecryptedPassword(): String =
        withContext(Dispatchers.IO) {
            val encrypted = preferenceDataStore.getCalDavEncryptedPassword.first()
            CalDavPasswordCipher.decrypt(encrypted)
        }

    override suspend fun saveSettings(config: CalDavConfig) {
        withContext(Dispatchers.IO) {
            preferenceDataStore.setCalDavEnabled(config.enabled)
            preferenceDataStore.setCalDavServerUrl(config.serverUrl.trim())
            preferenceDataStore.setCalDavUsername(config.username.trim())
            preferenceDataStore.setCalDavEncryptedPassword(CalDavPasswordCipher.encrypt(config.password))
            preferenceDataStore.setCalDavCollectionUrl(config.collectionUrl.trim())
            preferenceDataStore.setCalDavCollectionName(config.collectionName.trim())
            preferenceDataStore.setCalDavTrustSelfSigned(config.trustSelfSigned)
            preferenceDataStore.setCalDavSyncPrivate(config.syncPrivate)
            preferenceDataStore.setCalDavSyncOnStartup(config.syncOnStartup)
            preferenceDataStore.setCalDavSyncOnSave(config.syncOnSave)
            preferenceDataStore.setCalDavSyncIntervalHours(config.syncIntervalHours)

            if (config.enabled) {
                CalDavSyncWorker.schedulePeriodicSync(context, config.syncIntervalHours)
            } else {
                CalDavSyncWorker.cancelPeriodicSync(context)
                CalDavSyncWorker.cancelDebouncedSync(context)
            }
        }
    }

    private suspend fun loadConfig(): CalDavConfig {
        val encryptedPwd = preferenceDataStore.getCalDavEncryptedPassword.first()
        return CalDavConfig(
            enabled = preferenceDataStore.getCalDavEnabled.first(),
            serverUrl = preferenceDataStore.getCalDavServerUrl.first(),
            username = preferenceDataStore.getCalDavUsername.first(),
            password = CalDavPasswordCipher.decrypt(encryptedPwd),
            collectionUrl = preferenceDataStore.getCalDavCollectionUrl.first(),
            collectionName = preferenceDataStore.getCalDavCollectionName.first(),
            trustSelfSigned = preferenceDataStore.getCalDavTrustSelfSigned.first(),
            syncPrivate = preferenceDataStore.getCalDavSyncPrivate.first(),
            syncOnStartup = preferenceDataStore.getCalDavSyncOnStartup.first(),
            syncOnSave = preferenceDataStore.getCalDavSyncOnSave.first(),
            syncIntervalHours = preferenceDataStore.getCalDavSyncIntervalHours.first(),
        )
    }

    private suspend fun recordError(message: String) {
        val previousTime = preferenceDataStore.getCalDavLastSyncTime.first()
        preferenceDataStore.setCalDavLastSyncInfo(previousTime, message)
        _syncStatus.value =
            CalDavSyncStatus(
                isSyncing = false,
                lastSyncTime = previousTime,
                lastSyncMessage = message,
                isError = true,
            )
    }

    private fun formatExceptionMessage(e: Exception): String =
        when (e) {
            is CalDavAuthException -> context.getString(R.string.caldav_error_auth)
            else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        }

    companion object {
        private const val TAG = "CalDavSyncManager"
        private const val IN_APP_DEBOUNCE_MS = 2_500L
    }
}
