package com.yogeshpaliyal.deepr.caldav

data class CalDavCollection(
    val url: String,
    val displayName: String,
    val supportsVTodo: Boolean = true,
)

enum class CalDavSyncTrigger {
    STARTUP,
    LINK_CHANGE,
    PERIODIC,
    MANUAL,
}

data class CalDavConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val collectionUrl: String = "",
    val collectionName: String = "",
    val trustSelfSigned: Boolean = false,
    val syncPrivate: Boolean = false,
    val syncOnStartup: Boolean = true,
    val syncOnSave: Boolean = true,
    val syncIntervalHours: Long = 6L,
)

data class CalDavSyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val lastSyncMessage: String = "",
    val isError: Boolean = false,
)

sealed class CalDavConnectionTestResult {
    data class Success(
        val collections: List<CalDavCollection>,
        val resolvedBaseUrl: String,
    ) : CalDavConnectionTestResult()

    data class Error(
        val message: String,
    ) : CalDavConnectionTestResult()
}

sealed class CalDavSyncResult {
    data class Success(
        val pushedCount: Int = 0,
        val pulledCount: Int = 0,
        val deletedCount: Int = 0,
    ) : CalDavSyncResult()

    data class Error(
        val message: String,
    ) : CalDavSyncResult()
}
