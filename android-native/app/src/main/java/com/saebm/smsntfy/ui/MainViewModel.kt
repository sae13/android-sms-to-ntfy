package com.saebm.smsntfy.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saebm.smsntfy.SmsNtfyApplication
import com.saebm.smsntfy.data.EventLog
import com.saebm.smsntfy.service.SmsForwardingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SmsNtfyApplication
    private val prefs = app.preferences
    private val database = app.database

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    data class UiState(
        val isServiceRunning: Boolean = false,
        val isLoading: Boolean = false,
        val aetherStatus: String = "not-tested",
        val lastEvent: EventLog? = null
    )

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = UiState(
                isServiceRunning = prefs.isServiceRunning,
                isLoading = false,
                aetherStatus = prefs.aetherLastStatus,
                lastEvent = database.eventLogDao().getRecentLogs(1).first().firstOrNull()
            )
        }
    }

    fun toggleService() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            if (prefs.isServiceRunning) {
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
            val success = app.ntfyClient.sendTestMessage()
            withContext(Dispatchers.Main) {
                val message = if (success) {
                    "Test message sent successfully!"
                } else {
                    "Failed to send test message"
                }
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }
    }
}
