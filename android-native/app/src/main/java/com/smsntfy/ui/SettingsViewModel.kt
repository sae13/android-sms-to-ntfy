package com.smsntfy.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.network.NtfyClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsNtfyApplication
    private val ntfyClient = app.ntfyClient

    fun testConnection() {
        viewModelScope.launch {
            val success = ntfyClient.sendTestMessage()
            withContext(Dispatchers.Main) {
                val msg = if (success) "Test message sent successfully!" else "Failed to send test message"
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}