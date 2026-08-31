package com.smsntfy

import android.app.Application
import android.util.Log
import com.smsntfy.data.AppDatabase
import com.smsntfy.data.Preferences
import com.smsntfy.deltachat.DeltaChatClient
import com.smsntfy.deltachat.NativeDeltaChatCore
import com.smsntfy.network.NtfyClient
import com.smsntfy.telegram.TelegramBotClient
import com.smsntfy.aether.AetherSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class for SMS-to-Ntfy.
 * Initializes global singletons for preferences, storage, destinations, and Aether.
 * No Google Play Services dependencies are used.
 */
class SmsNtfyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val preferences by lazy { Preferences(this) }
    val database by lazy { AppDatabase.getDatabase(this) }
    val ntfyClient by lazy { NtfyClient(this) }
    val telegramBotClient by lazy { TelegramBotClient(this) }
    val aetherSessionManager by lazy { AetherSessionManager(this, preferences) }
    val deltaChatClient by lazy {
        DeltaChatClient(
            core = NativeDeltaChatCore(this),
            loadAccountId = { preferences.deltaChatAccountId },
            saveDestination = { accountId, chatId ->
                check(preferences.saveDeltaChatDestination(accountId, chatId)) {
                    "Delta Chat destination could not be saved"
                }
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SMS-to-Ntfy Application created")
    }

    companion object {
        private const val TAG = "SmsNtfyApp"
    }
}
