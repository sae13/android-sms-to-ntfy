package com.smsntfy.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.smsntfy.network.NtfyEndpointDefaults

/**
 * Centralized preferences storage for the app.
 * Uses SharedPreferences (no DataStore needed for simplicity, no Google deps).
 */
class Preferences(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_NTFY_SERVER = "ntfy_server_url"
        const val KEY_NTFY_TOPIC = "ntfy_topic"
        const val KEY_NTFY_USERNAME = "ntfy_username"
        const val KEY_NTFY_PASSWORD = "ntfy_password"
        const val KEY_REPLY_TOPIC = "reply_topic"
        const val KEY_ENABLE_SMS = "enable_sms"
        const val KEY_ENABLE_CALLS = "enable_calls"
        const val KEY_ENABLE_SSE = "enable_sse"
        const val KEY_SERVICE_RUNNING = "service_running"
        const val KEY_INITIAL_PERMISSION_REQUESTED = "initial_permission_requested"
        const val KEY_LAST_SENDER = "last_sender"
        const val KEY_LAST_CONTACT = "last_contact"
        const val KEY_USE_BASE64 = "use_base64"
        const val KEY_PRIORITY = "ntfy_priority"

    }

    var ntfyServer: String
        get() = prefs.getString(KEY_NTFY_SERVER, NtfyEndpointDefaults.serverUrl)
            ?: NtfyEndpointDefaults.serverUrl
        set(value) = prefs.edit().putString(KEY_NTFY_SERVER, value).apply()

    var ntfyTopic: String
        get() = prefs.getString(KEY_NTFY_TOPIC, "sms-alerts") ?: "sms-alerts"
        set(value) = prefs.edit().putString(KEY_NTFY_TOPIC, value).apply()

    var ntfyUsername: String
        get() = prefs.getString(KEY_NTFY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_USERNAME, value).apply()

    var ntfyPassword: String
        get() = prefs.getString(KEY_NTFY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NTFY_PASSWORD, value).apply()

    var replyTopic: String
        get() = prefs.getString(KEY_REPLY_TOPIC, "sms-replies") ?: "sms-replies"
        set(value) = prefs.edit().putString(KEY_REPLY_TOPIC, value).apply()

    var enableSms: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SMS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SMS, value).apply()

    var enableCalls: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_CALLS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_CALLS, value).apply()

    var enableSse: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SSE, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SSE, value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    var initialPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIAL_PERMISSION_REQUESTED, value).apply()

    var lastSender: String
        get() = prefs.getString(KEY_LAST_SENDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SENDER, value).apply()

    var lastContact: String
        get() = prefs.getString(KEY_LAST_CONTACT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CONTACT, value).apply()

    var useBase64: Boolean
        get() = prefs.getBoolean(KEY_USE_BASE64, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_BASE64, value).apply()

    var ntfyPriority: Int
        get() = prefs.getInt(KEY_PRIORITY, 4)
        set(value) = prefs.edit().putInt(KEY_PRIORITY, value).apply()



    /**
     * Returns the full ntfy topic URL for sending messages.
     */
    fun getNtfySendUrl(): String {
        val base = ntfyServer.trim().trimEnd('/')
        val topic = ntfyTopic.trim().trimStart('/')
        return "$base/$topic"
    }

    /**
     * Returns the full ntfy SSE URL for receiving messages (reply topic).
     */
    fun getNtfySseUrl(): String {
        val base = ntfyServer.trim().trimEnd('/')
        val topic = replyTopic.trim().trimStart('/')
        return "$base/$topic/sse"
    }
}
