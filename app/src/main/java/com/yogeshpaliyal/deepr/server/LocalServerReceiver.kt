package com.yogeshpaliyal.deepr.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yogeshpaliyal.deepr.preference.AppPreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Broadcast receiver that allows external automation apps (MacroDroid, Tasker, ADB) to start,
 * stop or toggle the local network server.
 *
 * Supported actions:
 * - [ACTION_START_SERVER]: starts the server with the saved port (or the `port` extra if provided)
 * - [ACTION_STOP_SERVER]: stops the running server
 * - [ACTION_TOGGLE_SERVER]: starts the server if stopped, stops it if running
 */
class LocalServerReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val localServerRepository: LocalServerRepository by inject()
    private val preferenceDataStore: AppPreferenceDataStore by inject()

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_START_SERVER -> {
                        val configuredPort = preferenceDataStore.getServerPort.first().toIntOrNull() ?: 8080
                        val port = intent.getIntExtra(PORT, configuredPort)
                        LocalServerService.startService(context, port)
                    }

                    ACTION_STOP_SERVER -> {
                        LocalServerService.stopService(context)
                    }

                    ACTION_TOGGLE_SERVER -> {
                        val isRunning = localServerRepository.isRunning.first()
                        if (isRunning) {
                            LocalServerService.stopService(context)
                        } else {
                            val configuredPort = preferenceDataStore.getServerPort.first().toIntOrNull() ?: 8080
                            val port = intent.getIntExtra(PORT, configuredPort)
                            LocalServerService.startService(context, port)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error handling broadcast action: $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LocalServerReceiver"

        const val ACTION_START_SERVER = "com.yogeshpaliyal.deepr.ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "com.yogeshpaliyal.deepr.ACTION_STOP_SERVER"
        const val ACTION_TOGGLE_SERVER = "com.yogeshpaliyal.deepr.ACTION_TOGGLE_SERVER"
    }
}
