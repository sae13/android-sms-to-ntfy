package com.smsntfy.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.Preferences
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTTP client for sending messages to ntfy server.
 * Handles authentication, Base64 encoding, and proper headers.
 */
class NtfyClient(context: Context) {

    private val prefs = (context.applicationContext as SmsNtfyApplication).preferences

    private val client = OkHttpClient.Builder()
        .dns(PinnedDns(NtfyEndpointDefaults.host, NtfyEndpointDefaults.address))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "NtfyClient"
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

                val request = buildRequest(
                    body = NtfyPayloadFormatter.sms(
                        sender, contact, NtfyPayloadFormatter.timestamp(timestamp), message
                    ),
                    metadata = NtfyPublishMetadata(
                        title = title,
                        priority = prefs.ntfyPriority,
                        tags = listOf("sms", "inbox"),
                        click = "sms:$sender",
                        actions = listOf(NtfyPublishAction("view", "View", "sms:$sender"))
                    )
                )

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                val statusCode = response.code
                response.close()

                Log.d(TAG, "SMS notification sent: $success - $statusCode")
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

                val tags = when (callState) {
                    "ringing" -> arrayOf("call", "ringing")
                    "missed" -> arrayOf("call", "missed")
                    "answered" -> arrayOf("call", "answered")
                    else -> arrayOf("call")
                }
                val messageText = NtfyPayloadFormatter.call(
                    callerNumber, callerName, callState, NtfyPayloadFormatter.timestamp(timestamp)
                )

                val request = buildRequest(
                    body = messageText,
                    metadata = NtfyPublishMetadata(
                        title = title,
                        priority = prefs.ntfyPriority,
                        tags = tags.toList(),
                        click = "tel:$callerNumber",
                        actions = listOf(NtfyPublishAction("view", "Call Back", "tel:$callerNumber"))
                    )
                )

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                val statusCode = response.code
                response.close()

                Log.d(TAG, "Call notification sent: $success - $statusCode")
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
                val request = buildRequest(
                    body = "Test message from SMS-to-Ntfy Android app",
                    metadata = NtfyPublishMetadata("SMS-to-Ntfy Test", 3, listOf("test"), null, emptyList())
                )

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                val statusCode = response.code
                response.close()

                Log.d(TAG, "Test message sent: $success - $statusCode")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send test message", e)
                false
            }
        }
    }

    /** Builds a raw text publish request with ntfy metadata headers and optional Basic auth. */
    private fun buildRequest(body: String, metadata: NtfyPublishMetadata): okhttp3.Request {
        val authorization = if (prefs.ntfyUsername.isNotEmpty() && prefs.ntfyPassword.isNotEmpty()) {
            val credentials = "${prefs.ntfyUsername}:${prefs.ntfyPassword}"
            "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"
        } else null
        return NtfyPublishRequest.build(prefs.getNtfySendUrl(), body, metadata, authorization)
    }

}