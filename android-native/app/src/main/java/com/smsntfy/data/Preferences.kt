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

    init {
        prefs.edit()
            .remove("reply_topic")
            .remove("enable_sse")
            .remove("telegram_proxy")
            .remove("last_sender")
            .remove("last_contact")
            .apply()
    }

    companion object {
        const val KEY_NTFY_SERVER = "ntfy_server_url"
        const val KEY_NTFY_TOPIC = "ntfy_topic"
        const val KEY_NTFY_USERNAME = "ntfy_username"
        const val KEY_NTFY_PASSWORD = "ntfy_password"
        const val KEY_ENABLE_SMS = "enable_sms"
        const val KEY_ENABLE_CALLS = "enable_calls"
        const val KEY_SERVICE_RUNNING = "service_running"
        const val KEY_INITIAL_PERMISSION_REQUESTED = "initial_permission_requested"
        const val KEY_USE_BASE64 = "use_base64"
        const val KEY_PRIORITY = "ntfy_priority"
        const val KEY_DELTACHAT_ENABLED = "deltachat_enabled"
        const val KEY_DELTACHAT_ACCOUNT_ID = "deltachat_account_id"
        const val KEY_DELTACHAT_CHAT_ID = "deltachat_chat_id"
        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val KEY_AETHER_ENABLED = "aether_enabled"
        const val KEY_AETHER_ALWAYS_ON = "aether_always_on"
        const val KEY_AETHER_PUBLIC_PROXY = "aether_public_proxy"
        const val KEY_AETHER_LAST_ROUTE = "aether_last_route"
        const val KEY_AETHER_LAST_STATUS = "aether_last_status"

        // Deliberately no login-code key: credentials belong only in Delta Chat's private database.
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

    var enableSms: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SMS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SMS, value).apply()

    var enableCalls: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_CALLS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_CALLS, value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    var initialPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIAL_PERMISSION_REQUESTED, value).apply()

    var useBase64: Boolean
        get() = prefs.getBoolean(KEY_USE_BASE64, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_BASE64, value).apply()

    var ntfyPriority: Int
        get() = prefs.getInt(KEY_PRIORITY, 4)
        set(value) = prefs.edit().putInt(KEY_PRIORITY, value).apply()

    var deltaChatEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELTACHAT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DELTACHAT_ENABLED, value).apply()

    var deltaChatAccountId: Int
        get() = prefs.getInt(KEY_DELTACHAT_ACCOUNT_ID, 0)
        set(value) = prefs.edit().putInt(KEY_DELTACHAT_ACCOUNT_ID, value).apply()

    var deltaChatChatId: Int
        get() = prefs.getInt(KEY_DELTACHAT_CHAT_ID, 0)
        set(value) = prefs.edit().putInt(KEY_DELTACHAT_CHAT_ID, value).apply()

    /** Telegram is deliberately disabled until the user opts in. */
    var telegramEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TELEGRAM_ENABLED, value).apply()

    /**
     * Telegram bot credentials are kept only in the app's private preferences.
     * They are never copied into Room mappings or message bodies.
     */
    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value).apply()

    var aetherEnabled: Boolean
        get() = prefs.getBoolean(KEY_AETHER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AETHER_ENABLED, value).apply()

    var aetherAlwaysOn: Boolean
        get() = prefs.getBoolean(KEY_AETHER_ALWAYS_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_AETHER_ALWAYS_ON, value).apply()

    var aetherPublicProxy: Boolean
        get() = prefs.getBoolean(KEY_AETHER_PUBLIC_PROXY, false)
        set(value) = prefs.edit().putBoolean(KEY_AETHER_PUBLIC_PROXY, value).apply()

    var aetherLastRoute: String
        get() = prefs.getString(KEY_AETHER_LAST_ROUTE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AETHER_LAST_ROUTE, value).apply()

    var aetherLastStatus: String
        get() = prefs.getString(KEY_AETHER_LAST_STATUS, "not-tested") ?: "not-tested"
        set(value) = prefs.edit().putString(KEY_AETHER_LAST_STATUS, value).apply()

    fun saveTelegramSettings(
        enabled: Boolean,
        botToken: String,
        chatId: String,
        aetherEnabled: Boolean,
        aetherAlwaysOn: Boolean,
        aetherPublicProxy: Boolean
    ): Boolean = prefs.edit()
        .putBoolean(KEY_TELEGRAM_ENABLED, enabled)
        .putString(KEY_TELEGRAM_BOT_TOKEN, botToken)
        .putString(KEY_TELEGRAM_CHAT_ID, chatId)
        .putBoolean(KEY_AETHER_ENABLED, aetherEnabled)
        .putBoolean(KEY_AETHER_ALWAYS_ON, aetherEnabled && aetherAlwaysOn)
        .putBoolean(KEY_AETHER_PUBLIC_PROXY, aetherEnabled && aetherPublicProxy)
        .commit()

    fun saveDeltaChatDestination(accountId: Int, chatId: Int): Boolean =
        prefs.edit()
            .putInt(KEY_DELTACHAT_ACCOUNT_ID, accountId)
            .putInt(KEY_DELTACHAT_CHAT_ID, chatId)
            .commit()

    /**
     * Returns the full ntfy topic URL for sending messages.
     */
    fun getNtfySendUrl(): String {
        val base = ntfyServer.trim().trimEnd('/')
        val topic = ntfyTopic.trim().trimStart('/')
        return "$base/$topic"
    }
}
