package com.saebm.smsntfy.telegram

import android.content.Context
import android.util.Log
import com.saebm.smsntfy.SmsNtfyApplication
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

data class TelegramConfig(val enabled: Boolean, val botToken: String, val chatId: String) {
    fun isReady(): Boolean = enabled && TelegramBotClient.isValidBotToken(botToken) &&
        TelegramBotClient.isValidChatId(chatId)
    override fun toString() = "TelegramConfig(enabled=$enabled, botToken=***, chatId=$chatId)"
}

sealed interface TelegramSendResult {
    data class Sent(val messageId: Int) : TelegramSendResult
    data class RouteUnavailable(val reason: String) : TelegramSendResult
    data class Ambiguous(val reason: String) : TelegramSendResult
    data class Failed(val reason: String) : TelegramSendResult
}

/** Send-only Bot API client. It has no update/polling/reply API. */
class TelegramBotClient private constructor(
    private val configProvider: () -> TelegramConfig,
    private val proxyPortProvider: () -> Int? = { null },
    private val httpClientFactory: ((Int?) -> OkHttpClient)? = null,
    private val callFactory: Call.Factory? = null,
    private val logError: (String) -> Unit = { Log.e(TAG, it) }
) {
    constructor(context: Context) : this({
        val prefs = (context.applicationContext as SmsNtfyApplication).preferences
        TelegramConfig(prefs.telegramEnabled, prefs.telegramBotToken, prefs.telegramChatId)
    })
    constructor(configProvider: () -> TelegramConfig, proxyPortProvider: () -> Int? = { null }) :
        this(configProvider, proxyPortProvider, null, null)

    suspend fun sendSmsMessage(sender: String, contact: String, body: String, timestamp: Long = System.currentTimeMillis()) =
        sendMessage(configProvider().chatId, formatSmsMessage(sender, contact, body, timestamp))

    suspend fun sendMessage(chatId: String, text: String): TelegramSendResult {
        val config = configProvider()
        if (!config.isReady()) return TelegramSendResult.Failed("Telegram destination is not configured")
        if (!isValidChatId(chatId) || text.isBlank()) return TelegramSendResult.Failed("Telegram chat or message is invalid")

        return when (val outcome = request(
            config,
            "sendMessage",
            """{"chat_id":${jsonString(chatId)},"text":${jsonString(text)}}"""
        )) {
            is RequestOutcome.Success -> parseMessageId(outcome.body)?.let(TelegramSendResult::Sent)
                ?: TelegramSendResult.Failed("Telegram returned an invalid message response")
            is RequestOutcome.HttpFailure -> TelegramSendResult.Failed(
                parseTelegramError(outcome.body)?.description?.takeIf(String::isNotBlank)
                    ?: httpFailureReason(outcome.statusCode)
            )
            is RequestOutcome.TransportFailure -> if (outcome.requestMayHaveBeenSent) {
                TelegramSendResult.Ambiguous(safeErrorCategory(outcome.error))
            } else {
                TelegramSendResult.RouteUnavailable(safeErrorCategory(outcome.error))
            }
        }
    }

    suspend fun testConnection(): Boolean {
        val config = configProvider()
        if (!config.isReady()) return false
        val outcome = request(config, "getMe", "{}")
        return outcome is RequestOutcome.Success && runCatching {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter(TelegramResponse::class.java).fromJson(outcome.body)?.ok == true
        }.getOrDefault(false)
    }

    private suspend fun request(config: TelegramConfig, method: String, body: String): RequestOutcome {
        val requestStarted = AtomicBoolean(false)
        val request = Request.Builder().url(API_BASE + config.botToken + "/" + method)
            .post(body.toRequestBody(JSON)).build()
        val call = callFactory?.newCall(request) ?: run {
            val baseClient = httpClientFactory?.invoke(proxyPortProvider()) ?: buildClient(proxyPortProvider())
            baseClient.newBuilder().eventListener(object : EventListener() {
                override fun requestHeadersStart(call: Call) {
                    requestStarted.set(true)
                }

                override fun requestBodyStart(call: Call) {
                    requestStarted.set(true)
                }
            }).build().newCall(request)
        }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    logError("Telegram $method failed: ${safeErrorCategory(e)}")
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.success(
                                RequestOutcome.TransportFailure(
                                    error = e,
                                    requestMayHaveBeenSent = requestStarted.get()
                                )
                            )
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val outcome = if (it.isSuccessful) {
                            RequestOutcome.Success(it.body?.string().orEmpty())
                        } else {
                            RequestOutcome.HttpFailure(it.code, it.body?.string().orEmpty())
                        }
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(outcome))
                        }
                    }
                }
            })
        }
    }

    private sealed interface RequestOutcome {
        data class Success(val body: String) : RequestOutcome
        data class HttpFailure(val statusCode: Int, val body: String) : RequestOutcome
        data class TransportFailure(
            val error: IOException,
            val requestMayHaveBeenSent: Boolean
        ) : RequestOutcome
    }

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
    @JsonClass(generateAdapter = false)
    private data class TelegramError(
        val description: String? = null
    )

    private fun parseTelegramError(json: String): TelegramError? = runCatching {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(TelegramError::class.java).fromJson(json)
    }.getOrNull()

    companion object {
        private const val TAG = "TelegramBotClient"
        private const val API_BASE = "https://api.telegram.org/bot"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun isValidBotToken(token: String) = token.trim().matches(Regex("^\\d{5,}:[A-Za-z0-9_-]{20,}$"))
        fun isValidChatId(chatId: String) = chatId.trim().matches(Regex("^-?\\d{1,20}$"))
        fun botId(token: String): String? = token.trim().substringBefore(':')
            .takeIf { it.matches(Regex("^\\d{5,}$")) }
        fun redact(value: String) = if (value.length <= 8) "***" else value.take(4) + "…" + value.takeLast(4)
        internal fun redactedErrorForTest(error: Throwable) = safeErrorCategory(error)
        internal fun forTest(
            configProvider: () -> TelegramConfig,
            callFactory: Call.Factory
        ) = TelegramBotClient(configProvider, { null }, null, callFactory) {}
        private fun safeErrorCategory(error: Throwable) = when (error) {
            is java.net.SocketTimeoutException -> "Telegram request timed out"
            is java.net.UnknownHostException -> "Telegram proxy or host could not be resolved"
            is java.net.ConnectException -> "Telegram connection failed"
            else -> "Telegram request failed"
        }
        private fun httpFailureReason(statusCode: Int) =
            "Telegram rejected the request (HTTP $statusCode)"
        internal fun httpFailureReasonForTest(statusCode: Int) = httpFailureReason(statusCode)
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
