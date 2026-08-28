package com.smsntfy.ui

import android.util.Log
import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.EventLog
import com.smsntfy.network.NtfyClient
import com.smsntfy.network.SseClient
import com.smsntfy.service.SmsForwardingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsNtfyApplication
    private val prefs = app.preferences
    private val ntfyClient = app.ntfyClient
    private val sseClient = app.sseClient
    private val database = app.database

    private val _uiState = MutableStateFlow(UiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val isServiceRunning: Boolean = false,
        val isLoading: Boolean = false,
        val connectionState: SseClient.ConnectionState = SseClient.ConnectionState.Disconnected,
        val lastEvent: EventLog? = null
    )

    init {
        refreshStatus()
        observeSseState()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val running = prefs.isServiceRunning
            val connState = sseClient.connectionState.value
            val lastEvent = database.eventLogDao().getRecentLogs(1).first().firstOrNull()

            _uiState.value = _uiState.value.copy(
                isServiceRunning = running,
                isLoading = false,
                connectionState = connState,
                lastEvent = lastEvent
            )
        }
    }

    fun toggleService() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val wasRunning = prefs.isServiceRunning
            if (wasRunning) {
                stopService()
            } else {
                startService()
            }

            refreshStatus()
        }
    }

    private fun startService() {
        val intent = android.content.Intent(app, SmsForwardingService::class.java).apply {
            action = SmsForwardingService.ACTION_START_SERVICE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    private fun stopService() {
        val intent = android.content.Intent(app, SmsForwardingService::class.java).apply {
            action = SmsForwardingService.ACTION_STOP_SERVICE
        }
        app.startService(intent)
    }

    fun testConnection() {
        viewModelScope.launch {
            val success = ntfyClient.sendTestMessage()
            withContext(Dispatchers.Main) {
                val msg = if (success) "Test message sent successfully!" else "Failed to send test message"
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }
    }

    private fun observeSseState() {
        viewModelScope.launch {
            sseClient.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
    }
}