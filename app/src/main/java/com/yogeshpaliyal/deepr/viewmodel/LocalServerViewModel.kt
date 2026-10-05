package com.yogeshpaliyal.deepr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yogeshpaliyal.deepr.server.LocalServerRepository
import kotlinx.coroutines.launch

class LocalServerViewModel(
    private val localServerRepository: LocalServerRepository,
) : ViewModel() {
    val isRunning = localServerRepository.isRunning
    val serverUrl = localServerRepository.serverUrl
    val serverPort = localServerRepository.serverPort
    val serverInactivityTimeoutMinutes = localServerRepository.serverInactivityTimeoutMinutes

    fun setServerPort(port: Int) {
        viewModelScope.launch {
            localServerRepository.setServerPort(port)
        }
    }

    fun setServerInactivityTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            localServerRepository.setServerInactivityTimeoutMinutes(minutes)
        }
    }
}
