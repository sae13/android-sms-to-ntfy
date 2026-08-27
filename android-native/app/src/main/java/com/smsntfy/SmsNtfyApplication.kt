package com.smsntfy

import android.app.Application
import android.util.Log
import com.smsntfy.data.AppDatabase
import com.smsntfy.data.Preferences
import com.smsntfy.network.NtfyClient
import com.smsntfy.network.SseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class for SMS-to-Ntfy.
 * Initializes global singletons: preferences, database, HTTP client, SSE client.
 * No Google Play Services dependencies are used.
 */
class SmsNtfyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val preferences by lazy { Preferences(this) }
    val database by lazy { AppDatabase.getDatabase(this) }
    val ntfyClient by lazy { NtfyClient(this) }
    val sseClient by lazy { SseClient(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SMS-to-Ntfy Application created")
    }

    companion object {
        private const val TAG = "SmsNtfyApp"
    }
}
