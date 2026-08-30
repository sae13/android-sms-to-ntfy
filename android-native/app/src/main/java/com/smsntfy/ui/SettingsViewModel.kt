package com.smsntfy.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.deltachat.DeltaChatSetupResult
import com.smsntfy.deltachat.DeltaChatSendResult
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

    fun setupDeltaChat(loginCode: String, invite: String, onComplete: (DeltaChatSetupResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                app.deltaChatClient.setup(loginCode, invite)
            } catch (_: Exception) {
                DeltaChatSetupResult.Failed("Delta Chat is unavailable")
            } catch (_: LinkageError) {
                DeltaChatSetupResult.Failed("Delta Chat is unavailable on this device")
            }
            if (result is DeltaChatSetupResult.Ready) {
                app.preferences.deltaChatEnabled = true
            }
            onComplete(result)
        }
    }

    fun testDeltaChat(onComplete: (DeltaChatSendResult) -> Unit) {
        viewModelScope.launch {
            val prefs = app.preferences
            val result = if (prefs.deltaChatEnabled && prefs.deltaChatChatId > 0) {
                try {
                    app.deltaChatClient.sendTextWithResult(
                        prefs.deltaChatChatId,
                        "SMS-to-Ntfy Delta Chat test"
                    )
                } catch (_: Exception) {
                    DeltaChatSendResult.Failed("Delta Chat test failed")
                } catch (_: LinkageError) {
                    DeltaChatSendResult.Failed("Delta Chat is unavailable on this device")
                }
            } else {
                DeltaChatSendResult.Failed("Delta Chat destination is not configured")
            }
            onComplete(result)
        }
    }
}