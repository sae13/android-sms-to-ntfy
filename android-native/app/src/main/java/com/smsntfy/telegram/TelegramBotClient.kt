package com.smsntfy.telegram

import android.content.Context
import android.util.Log
import com.smsntfy.SmsNtfyApplication
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TelegramConfig(
    val enabled: Boolean,
    val botToken: String,
    val chatId: String,
    val proxy: String = ""
) {
    fun isReady(): Boolean =
        enabled && TelegramBotClient.isValidBotToken(botToken) && TelegramBotClient.isValidChatId(chatId)

    override fun toString(): String =
        "TelegramConfig(enabled=$enabled, botToken=***, chatId=$chatId, proxyConfigured=${proxy.isNotBlank()})"
}

data class TelegramSentMessage(val messageId: Int)

data class TelegramUpdate(
    val updateId: Long,
    val chatId: String?,
    val messageId: Int?,
    val text: String?,
    val replyToMessageId: Int?,
    val repliedMessageFromBot: Boolean,
    val fromBot: Boolean,
    val edited: Boolean = false
)

sealed interface TelegramSendResult {
    data class Sent(val messageId: Int) : TelegramSendResult
    data class Failed(val reason: String) : TelegramSendResult
}

sealed interface TelegramUpdatesResult {
    data class Success(val updates: List<TelegramUpdate>) : TelegramUpdatesResult
    data class Failed(val reason: String) : TelegramUpdatesResult
}

/**
 * Small Bot API client. It intentionally exposes redacted errors only.
 * The token is used in the request URL but is never included in logs/errors.
 */
class TelegramBotClient private constructor(
    private val configProvider: () -> TelegramConfig,
    private val httpClientFactory: ((TelegramConfig) -> OkHttpClient)? = null
) {
    constructor(context: Context) : this({
        val prefs = (context.applicationContext as SmsNtfyApplication).preferences
        TelegramConfig(prefs.telegramEnabled, prefs.telegramBotToken, prefs.telegramChatId, prefs.telegramProxy)
    })

    constructor(configProvider: () -> TelegramConfig) : this(configProvider, null)

    companion object {
        private const val TAG = "TelegramBotClient"
        private const val API_BASE = "https://api.telegram.org/bot"
        fun isValidBotToken(token: String): Boolean =
            token.trim().matches(Regex("^\\d{5,}:[A-Za-z0-9_-]{20,}$"))

        fun isValidChatId(chatId: String): Boolean =
            chatId.trim().matches(Regex("^-?\\d{1,20}$"))

        fun redact(value: String): String =
            when {
                value.length <= 8 -> "***"
                else -> value.take(4) + "…" + value.takeLast(4)
            }

        internal fun redactedErrorForTest(error: Throwable): String =
            safeErrorCategory(error)

        private fun safeErrorCategory(error: Throwable): String = when (error) {
            is java.net.SocketTimeoutException -> "Telegram request timed out"
            is java.net.UnknownHostException -> "Telegram proxy or host could not be resolved"
            is java.net.ConnectException -> "Telegram connection failed"
            else -> "Telegram request failed"
        }

        fun formatSmsMessage(sender: String, contact: String, body: String, timestamp: Long): String =
            buildString {
                append("SMS from ")
                append(contact.ifBlank { "Unknown" })
                append(" <").append(sender).append(">\n")
                append(body)
                append("\nReceived: ")
                append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(Date(timestamp)))
                append("\n\nReply to this message to respond.")
            }
    }

    suspend fun sendSmsMessage(
        sender: String,
        contact: String,
        body: String,
        timestamp: Long = System.currentTimeMillis()
    ): TelegramSendResult = sendMessage(
        configProvider().chatId,
        formatSmsMessage(sender, contact, body, timestamp)
    )

    suspend fun sendMessage(chatId: String, text: String): TelegramSendResult = withContext(Dispatchers.IO) {
        val config = configProvider()
        if (!config.isReady()) return@withContext TelegramSendResult.Failed("Telegram destination is not configured")
        if (!isValidChatId(chatId) || text.isBlank()) {
            return@withContext TelegramSendResult.Failed("Telegram chat or message is invalid")
        }
        request(config, "sendMessage", """{"chat_id":${jsonString(chatId)},"text":${jsonString(text)}}""")
            .fold(
                onSuccess = { body ->
                    val parsed = parseSendResponse(body)
                    if (parsed != null) TelegramSendResult.Sent(parsed)
                    else TelegramSendResult.Failed("Telegram returned an invalid message response")
                },
                onFailure = { TelegramSendResult.Failed(redactedError(it)) }
            )
    }

    suspend fun getUpdates(offset: Long, timeoutSeconds: Int = 30): TelegramUpdatesResult = withContext(Dispatchers.IO) {
        val config = configProvider()
        if (!config.isReady()) return@withContext TelegramUpdatesResult.Failed("Telegram destination is not configured")
        val timeout = timeoutSeconds.coerceIn(0, 50)
        val body = """{"offset":$offset,"timeout":$timeout,"allowed_updates":["message","edited_message"]}"""
        request(config, "getUpdates", body).fold(
            onSuccess = { TelegramUpdatesResult.Success(parseUpdates(it)) },
            onFailure = { TelegramUpdatesResult.Failed(redactedError(it)) }
        )
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val config = configProvider()
        if (!config.isReady()) return@withContext false
        request(config, "getMe", "{}")
            .mapCatching { json ->
                Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()
                    .adapter(TelegramApiResponse::class.java)
                    .fromJson(json)
                    ?.ok == true
            }
            .getOrDefault(false)
    }

    /** Parses a Bot API response without logging the payload (which may contain user text). */
    fun parseUpdates(json: String): List<TelegramUpdate> {
        return runCatching {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val root = moshi.adapter(TelegramApiResponse::class.java).fromJson(json)
                ?: return@runCatching emptyList()
            if (!root.ok) return@runCatching emptyList()
            root.result?.mapNotNull { it.toPublic() } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun parseSendResponse(json: String): Int? {
        return runCatching {
            val root = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(TelegramSendResponse::class.java)
                .fromJson(json)
            if (root?.ok == true) root.result?.messageId else null
        }.getOrNull()
    }

    private fun request(config: TelegramConfig, method: String, body: String): Result<String> {
        return runCatching {
            val proxy = TelegramProxy.parse(config.proxy).getOrThrow()
            val client = httpClientFactory?.invoke(config) ?: buildClient(proxy)
            val url = API_BASE + config.botToken + "/" + method
            val request = Request.Builder().url(url)
                .post(body.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Telegram HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }.onFailure { Log.e(TAG, "Telegram $method failed: ${redactedError(it)}") }
    }

    private fun buildClient(proxy: TelegramProxy): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(65, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        val socks = when (proxy) {
            TelegramProxy.Direct -> null
            is TelegramProxy.Socks5 -> proxy
            is TelegramProxy.MtProto -> proxy.bridgeEndpoint()
                ?: error("MTProto requires a local SOCKS bridge")
        }
        if (socks != null) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(socks.host, socks.port)))
        }
        return builder.build()
    }

    private fun redactedError(error: Throwable): String =
        safeErrorCategory(error)

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

    @JsonClass(generateAdapter = false)
    private data class TelegramApiResponse(val ok: Boolean, val result: List<TelegramApiUpdate>? = null)

    @JsonClass(generateAdapter = false)
    private data class TelegramSendResponse(val ok: Boolean, val result: TelegramApiMessage? = null)

    @JsonClass(generateAdapter = false)
    private data class TelegramApiUpdate(
        val update_id: Long,
        val message: TelegramApiMessage? = null,
        val edited_message: TelegramApiMessage? = null
    ) {
        fun toPublic(): TelegramUpdate {
            val editedMessage = edited_message
            val messageValue = message ?: editedMessage
            val reply = messageValue?.reply_to_message
            return TelegramUpdate(
                updateId = update_id,
                chatId = messageValue?.chat?.id?.toString(),
                messageId = messageValue?.message_id,
                text = messageValue?.text,
                replyToMessageId = reply?.message_id,
                repliedMessageFromBot = reply?.from?.is_bot == true,
                fromBot = messageValue?.from?.is_bot == true,
                edited = editedMessage != null
            )
        }
    }

    @JsonClass(generateAdapter = false)
    private data class TelegramApiMessage(
        val message_id: Int,
        val text: String? = null,
        val chat: TelegramApiChat? = null,
        val from: TelegramApiUser? = null,
        val reply_to_message: TelegramApiMessage? = null
    ) {
        val messageId: Int get() = message_id
    }

    @JsonClass(generateAdapter = false)
    private data class TelegramApiChat(val id: Long)

    @JsonClass(generateAdapter = false)
    private data class TelegramApiUser(val is_bot: Boolean = false)

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
