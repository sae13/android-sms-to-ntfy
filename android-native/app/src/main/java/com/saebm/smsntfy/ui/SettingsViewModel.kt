package com.saebm.smsntfy.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saebm.smsntfy.SmsNtfyApplication
import com.saebm.smsntfy.deltachat.DeltaChatSetupResult
import com.saebm.smsntfy.deltachat.DeltaChatSendResult
import com.saebm.smsntfy.network.NtfyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
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

    fun findFastestAetherRoute(
        botToken: String,
        publicProxy: Boolean,
        onAttempt: (com.saebm.smsntfy.aether.AetherRoute, com.saebm.smsntfy.aether.AetherRouteAttemptStage) -> Unit,
        onComplete: (com.saebm.smsntfy.aether.AetherRoute?) -> Unit
    ) {
        viewModelScope.launch {
            val selected = try {
                app.aetherSessionManager.findFastestRoute(
                    botToken = botToken,
                    publicProxy = publicProxy,
                    onAttempt = onAttempt
                )
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            }
            onComplete(selected)
        }
    }

    fun testTelegram(
        config: com.saebm.smsntfy.telegram.TelegramConfig,
        useAether: Boolean,
        publicProxy: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var session: com.saebm.smsntfy.aether.AetherSessionManager.Session? = null
            val success = try {
                val proxyPort = if (useAether) {
                    session = app.aetherSessionManager.acquire(
                        config.botToken,
                        keepAlive = false,
                        publicProxy = publicProxy
                    )
                    session.port
                } else null
                com.saebm.smsntfy.telegram.TelegramBotClient({ config }, { proxyPort }).testConnection()
            } catch (_: Exception) {
                false
            } catch (_: LinkageError) {
                false
            } finally {
                withContext(NonCancellable) { session?.close() }
            }
            onComplete(success)
        }
    }
}
