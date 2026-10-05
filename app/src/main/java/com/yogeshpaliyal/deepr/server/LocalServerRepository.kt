package com.yogeshpaliyal.deepr.server

import kotlinx.coroutines.flow.StateFlow

interface LocalServerRepository {
    val isRunning: StateFlow<Boolean>
    val serverUrl: StateFlow<String?>
    val serverPort: StateFlow<Int>
    val serverInactivityTimeoutMinutes: StateFlow<Int>
    val serverPassword: StateFlow<String>
    val qrCodeData: StateFlow<String?>

    suspend fun startServer(port: Int)

    fun stopServer()

    suspend fun setServerPort(port: Int)

    suspend fun setServerInactivityTimeoutMinutes(minutes: Int)

    suspend fun setServerPassword(password: String)

    suspend fun fetchAndImportFromSender(qrTransferInfo: QRTransferInfo): Result<Unit>
}
