package com.smsntfy.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * SSE client for receiving remote replies from ntfy server.
 * Uses OkHttp's SSE support (no Google dependencies).
 * Automatically reconnects with exponential backoff.
 */
class SseClient(context: Context) {

    private val prefs = (context.applicationContext as SmsNtfyApplication).preferences

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for SSE
        .build()

    private var eventSource: EventSource? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<SseMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<SseMessage> = _messages.asSharedFlow()

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class SseMessage(
        val id: String = "",
        val event: String = "message",
        val data: String = "",
        val time: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "SseClient"
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }

    private var retryDelayMs = 1_000L

    fun start() {
        if (eventSource != null) {
            Log.d(TAG, "SSE already running")
            return
        }
        Log.d(TAG, "Starting SSE connection")
        connect()
    }

    fun stop() {
        Log.d(TAG, "Stopping SSE connection")
        eventSource?.cancel()
        eventSource = null
        _connectionState.value = ConnectionState.Disconnected
        scope.cancel()
    }

    private fun connect() {
        val url = prefs.getNtfySseUrl()
        Log.d(TAG, "Connecting to SSE: $url")

        _connectionState.value = ConnectionState.Connecting

        val requestBuilder = Request.Builder().url(url)

        if (prefs.ntfyUsername.isNotEmpty() && prefs.ntfyPassword.isNotEmpty()) {
            val credentials = "${prefs.ntfyUsername}:${prefs.ntfyPassword}"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            requestBuilder.addHeader("Authorization", "Basic $encoded")
        }

        val request = requestBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE connection opened")
                retryDelayMs = 1_000L
                _connectionState.value = ConnectionState.Connected
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d(TAG, "SSE event received: type=$type, id=$id")
                val sseMessage = SseMessage(
                    id = id ?: "",
                    event = type ?: "message",
                    data = data,
                    time = System.currentTimeMillis()
                )
                _messages.tryEmit(sseMessage)
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE connection closed")
                _connectionState.value = ConnectionState.Disconnected
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = t?.message ?: "Unknown error"
                Log.e(TAG, "SSE connection failed: $errorMsg", t)
                _connectionState.value = ConnectionState.Error(errorMsg)
                eventSource.cancel()
                scheduleReconnect()
            }
        }

        eventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }

    private fun scheduleReconnect() {
        scope.launch {
            Log.d(TAG, "Scheduling reconnect in ${retryDelayMs}ms")
            delay(retryDelayMs)

            // Exponential backoff
            retryDelayMs = minOf(retryDelayMs * 2, MAX_RETRY_DELAY_MS)

            if (eventSource == null) {
                // Already stopped
                return@launch
            }

            connect()
        }
    }

    /**
     * Parses an SSE message's data field (JSON) into a ntfy message object.
     */
    fun parseNtfyMessage(data: String): NtfySseData? {
        return try {
            // Simple JSON parsing without external dependencies
            val idRegex = """"id"\s*:\s*"([^"]*)"""".toRegex()
            val messageRegex = """"message"\s*:\s*"([^"]*)"""".toRegex()
            val titleRegex = """"title"\s*:\s*"([^"]*)"""".toRegex()
            val priorityRegex = """"priority"\s*:\s*(\d+)""".toRegex()
            val tagsRegex = """"tags"\s*:\s*\[([^\]]*)\]""".toRegex()

            val id = idRegex.find(data)?.groupValues?.getOrNull(1) ?: ""
            val message = messageRegex.find(data)?.groupValues?.getOrNull(1) ?: ""
            val title = titleRegex.find(data)?.groupValues?.getOrNull(1) ?: ""
            val priority = priorityRegex.find(data)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 3
            val tagsStr = tagsRegex.find(data)?.groupValues?.getOrNull(1) ?: ""
            val tags = if (tagsStr.isEmpty()) emptyList() else tagsStr.split(",").map { it.trim().trim('"') }

            NtfySseData(id, message, title, priority, tags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE message", e)
            null
        }
    }

    data class NtfySseData(
        val id: String,
        val message: String,
        val title: String,
        val priority: Int,
        val tags: List<String>
    )
}