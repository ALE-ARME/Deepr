package com.yogeshpaliyal.deepr.caldav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoOpCalDavSyncManager : CalDavSyncManager {
    override val isAvailable: Boolean = false

    private val _syncStatus = MutableStateFlow(CalDavSyncStatus())
    override val syncStatus: StateFlow<CalDavSyncStatus> = _syncStatus.asStateFlow()

    override fun onAppStartup() {}

    override fun onLocalDataChanged() {}

    override fun schedulePeriodicSyncIfEnabled() {}

    override suspend fun syncNow(trigger: CalDavSyncTrigger): CalDavSyncResult =
        CalDavSyncResult.Error("CalDAV sync is only available in the GitHub build")

    override suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): CalDavConnectionTestResult = CalDavConnectionTestResult.Error("CalDAV sync is only available in the GitHub build")

    override suspend fun getDecryptedPassword(): String = ""

    override suspend fun saveSettings(config: CalDavConfig) {}
}
