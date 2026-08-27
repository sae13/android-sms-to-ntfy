package com.smsntfy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.network.NtfyClient
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val app by lazy { SmsNtfyApplication() }
    private val ntfyClient = app.ntfyClient

    fun testConnection() {
        viewModelScope.launch {
            val success = ntfyClient.sendTestMessage()
            app.runOnUiThread {
                val msg = if (success) "Test message sent successfully!" else "Failed to send test message"
                android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}