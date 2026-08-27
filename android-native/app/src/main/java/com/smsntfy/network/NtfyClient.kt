package com.smsntfy.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.Preferences
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTTP client for sending messages to ntfy server.
 * Handles authentication, Base64 encoding, and proper headers.
 */
class NtfyClient(context: Context) {

    private val prefs = (context.applicationContext as SmsNtfyApplication).preferences
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "NtfyClient"
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
        private const val MEDIA_TYPE_TEXT = "text/plain; charset=utf-8"
    }

    /**
     * Sends an SMS notification to ntfy.
     */
    suspend fun sendSmsNotification(
        sender: String,
        contact: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val title = if (prefs.useBase64) {
                    Base64.encodeToString("$contact <$sender>".toByteArray(), Base64.NO_WRAP)
                } else {
                    "$contact <$sender>"
                }

                val body = NtfyMessage(
                    topic = prefs.ntfyTopic,
                    message = message,
                    title = title,
                    priority = prefs.ntfyPriority,
                    tags = arrayOf("sms", "inbox"),
                    click = "sms:$sender",
                    email = null,
                    actions = arrayOf(
                        NtfyAction(
                            action = "view",
                            label = "View",
                            url = "sms:$sender"
                        )
                    )
                )

                val jsonAdapter: JsonAdapter<NtfyMessage> = moshi.adapter(NtfyMessage::class.java)
                val json = jsonAdapter.toJson(body)

                val request = buildRequest(json)

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()

                Log.d(TAG, "SMS notification sent: $success - ${response.code()}")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS notification", e)
                false
            }
        }
    }

    /**
     * Sends a call notification to ntfy.
     */
    suspend fun sendCallNotification(
        callerNumber: String,
        callerName: String,
        callState: String, // "ringing", "missed", "answered"
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val title = if (prefs.useBase64) {
                    Base64.encodeToString("$callerName <$callerNumber>".toByteArray(), Base64.NO_WRAP)
                } else {
                    "$callerName <$callerNumber>"
                }

                val (messageText, tags) = when (callState) {
                    "ringing" -> "Incoming call from $callerName ($callerNumber)" to arrayOf("call", "ringing")
                    "missed" -> "Missed call from $callerName ($callerNumber)" to arrayOf("call", "missed")
                    "answered" -> "Call answered from $callerName ($callerNumber)" to arrayOf("call", "answered")
                    else -> "Call event: $callState" to arrayOf("call")
                }

                val body = NtfyMessage(
                    topic = prefs.ntfyTopic,
                    message = messageText,
                    title = title,
                    priority = prefs.ntfyPriority,
                    tags = tags,
                    click = "tel:$callerNumber",
                    email = null,
                    actions = arrayOf(
                        NtfyAction(
                            action = "view",
                            label = "Call Back",
                            url = "tel:$callerNumber"
                        )
                    )
                )

                val jsonAdapter: JsonAdapter<NtfyMessage> = moshi.adapter(NtfyMessage::class.java)
                val json = jsonAdapter.toJson(body)

                val request = buildRequest(json)

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()

                Log.d(TAG, "Call notification sent: $success - ${response.code()}")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send call notification", e)
                false
            }
        }
    }

    /**
     * Sends a test message to verify connection.
     */
    suspend fun sendTestMessage(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val body = NtfyMessage(
                    topic = prefs.ntfyTopic,
                    message = "Test message from SMS-to-Ntfy Android app",
                    title = "SMS-to-Ntfy Test",
                    priority = 3,
                    tags = arrayOf("test"),
                    click = null,
                    email = null,
                    actions = null
                )

                val jsonAdapter: JsonAdapter<NtfyMessage> = moshi.adapter(NtfyMessage::class.java)
                val json = jsonAdapter.toJson(body)

                val request = buildRequest(json)

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()

                Log.d(TAG, "Test message sent: $success - ${response.code()}")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send test message", e)
                false
            }
        }
    }

    /**
     * Builds the HTTP request with authentication.
     */
    private fun buildRequest(json: String): Request {
        val url = prefs.getNtfySendUrl()

        val requestBuilder = Request.Builder()
            .url(url)
            .post(json.toRequestBody(MEDIA_TYPE_JSON))

        // Add Basic Auth if credentials provided
        if (prefs.ntfyUsername.isNotEmpty() && prefs.ntfyPassword.isNotEmpty()) {
            val credentials = "${prefs.ntfyUsername}:${prefs.ntfyPassword}"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            requestBuilder.addHeader("Authorization", "Basic $encoded")
        }

        return requestBuilder.build()
    }

    /**
     * Data class for ntfy message payload.
     */
    private data class NtfyMessage(
        val topic: String,
        val message: String,
        val title: String,
        val priority: Int,
        val tags: Array<String>,
        val click: String?,
        val email: String?,
        val actions: Array<NtfyAction>?
    )

    private data class NtfyAction(
        val action: String,
        val label: String,
        val url: String
    )
}