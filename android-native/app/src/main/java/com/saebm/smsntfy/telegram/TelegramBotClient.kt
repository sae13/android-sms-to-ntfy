package com.saebm.smsntfy.telegram

import android.content.Context
import android.util.Log
import com.saebm.smsntfy.SmsNtfyApplication
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TelegramConfig(val enabled: Boolean, val botToken: String, val chatId: String) {
    fun isReady(): Boolean = enabled && TelegramBotClient.isValidBotToken(botToken) &&
        TelegramBotClient.isValidChatId(chatId)
    override fun toString() = "TelegramConfig(enabled=$enabled, botToken=***, chatId=$chatId)"
}

sealed interface TelegramSendResult {
    data class Sent(val messageId: Int) : TelegramSendResult
    data class Failed(val reason: String) : TelegramSendResult
}

/** Send-only Bot API client. It has no update/polling/reply API. */
class TelegramBotClient private constructor(
    private val configProvider: () -> TelegramConfig,
    private val proxyPortProvider: () -> Int? = { null },
    private val httpClientFactory: ((Int?) -> OkHttpClient)? = null
) {
    constructor(context: Context) : this({
        val prefs = (context.applicationContext as SmsNtfyApplication).preferences
        TelegramConfig(prefs.telegramEnabled, prefs.telegramBotToken, prefs.telegramChatId)
    })
    constructor(configProvider: () -> TelegramConfig, proxyPortProvider: () -> Int? = { null }) :
        this(configProvider, proxyPortProvider, null)

    suspend fun sendSmsMessage(sender: String, contact: String, body: String, timestamp: Long = System.currentTimeMillis()) =
        sendMessage(configProvider().chatId, formatSmsMessage(sender, contact, body, timestamp))

    suspend fun sendMessage(chatId: String, text: String): TelegramSendResult = withContext(Dispatchers.IO) {
        val config = configProvider()
        if (!config.isReady()) return@withContext TelegramSendResult.Failed("Telegram destination is not configured")
        if (!isValidChatId(chatId) || text.isBlank()) return@withContext TelegramSendResult.Failed("Telegram chat or message is invalid")
        request(config, "sendMessage", """{"chat_id":${jsonString(chatId)},"text":${jsonString(text)}}""")
            .fold(
                { body -> parseMessageId(body)?.let(TelegramSendResult::Sent)
                    ?: TelegramSendResult.Failed("Telegram returned an invalid message response") },
                { TelegramSendResult.Failed(safeErrorCategory(it)) }
            )
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val config = configProvider()
        config.isReady() && request(config, "getMe", "{}").mapCatching { body ->
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter(TelegramResponse::class.java).fromJson(body)?.ok == true
        }.getOrDefault(false)
    }

    private fun request(config: TelegramConfig, method: String, body: String): Result<String> = runCatching {
        val client = httpClientFactory?.invoke(proxyPortProvider()) ?: buildClient(proxyPortProvider())
        val request = Request.Builder().url(API_BASE + config.botToken + "/" + method)
            .post(body.toRequestBody(JSON)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Telegram HTTP failure")
            response.body?.string().orEmpty()
        }
    }.onFailure { Log.e(TAG, "Telegram $method failed: ${safeErrorCategory(it)}") }

    private fun buildClient(port: Int?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
        .apply { if (port != null) proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))) }
        .build()

    private fun parseMessageId(json: String): Int? = runCatching {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(TelegramResponse::class.java).fromJson(json)
            ?.takeIf { it.ok }?.result?.message_id
    }.getOrNull()

    @JsonClass(generateAdapter = false)
    private data class TelegramResponse(val ok: Boolean, val result: TelegramMessage? = null)
    @JsonClass(generateAdapter = false)
    private data class TelegramMessage(val message_id: Int = 0)

    companion object {
        private const val TAG = "TelegramBotClient"
        private const val API_BASE = "https://api.telegram.org/bot"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun isValidBotToken(token: String) = token.trim().matches(Regex("^\\d{5,}:[A-Za-z0-9_-]{20,}$"))
        fun isValidChatId(chatId: String) = chatId.trim().matches(Regex("^-?\\d{1,20}$"))
        fun redact(value: String) = if (value.length <= 8) "***" else value.take(4) + "…" + value.takeLast(4)
        internal fun redactedErrorForTest(error: Throwable) = safeErrorCategory(error)
        private fun safeErrorCategory(error: Throwable) = when (error) {
            is java.net.SocketTimeoutException -> "Telegram request timed out"
            is java.net.UnknownHostException -> "Telegram proxy or host could not be resolved"
            is java.net.ConnectException -> "Telegram connection failed"
            else -> "Telegram request failed"
        }
        fun formatSmsMessage(sender: String, contact: String, body: String, timestamp: Long) = buildString {
            append("SMS from ").append(contact.ifBlank { "Unknown" }).append(" <").append(sender).append(">\n")
            append(body).append("\nReceived: ")
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(timestamp)))
        }
        private fun jsonString(value: String) = "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
    }
}
