package com.yogeshpaliyal.deepr.caldav

import kotlinx.coroutines.flow.StateFlow

interface CalDavSyncManager {
    val isAvailable: Boolean

    val syncStatus: StateFlow<CalDavSyncStatus>

    fun onAppStartup()

    fun onLocalDataChanged()

    fun schedulePeriodicSyncIfEnabled()

    suspend fun syncNow(trigger: CalDavSyncTrigger = CalDavSyncTrigger.MANUAL): CalDavSyncResult

    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): CalDavConnectionTestResult

    suspend fun getDecryptedPassword(): String

    suspend fun saveSettings(config: CalDavConfig)
}
