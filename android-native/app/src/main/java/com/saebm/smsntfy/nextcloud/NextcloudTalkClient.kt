package com.saebm.smsntfy.nextcloud

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface NextcloudSendResult {
    data object Sent : NextcloudSendResult
    data class Failed(val reason: String) : NextcloudSendResult
}

/**
 * Minimal Nextcloud Talk bot client: posts a chat message to a room token
 * via the OCS spreed API using app-password basic auth. Fail-closed, no retries.
 */
class NextcloudTalkClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val logWarning: (String) -> Unit = { Log.w(TAG, it) }
) {

    suspend fun send(config: NextcloudConfig, message: String): NextcloudSendResult =
        withContext(Dispatchers.IO) {
            if (!config.enabled) return@withContext NextcloudSendResult.Failed("Nextcloud destination is not configured")
            try {
                val body = FormBody.Builder()
                    .add("message", message)
                    .build()
                val request = Request.Builder()
                    .url("${config.serverUrl}/ocs/v2.php/apps/spreed/api/v1/chat/${config.talkToken}")
                    .header("Authorization", Credentials.basic(config.username, config.appPassword))
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        NextcloudSendResult.Sent
                    } else {
                        logWarning("Nextcloud Talk rejected message: HTTP ${response.code}")
                        NextcloudSendResult.Failed("Nextcloud rejected message")
                    }
                }
            } catch (error: Exception) {
                logWarning("Nextcloud send failed: ${error.javaClass.simpleName}")
                NextcloudSendResult.Failed(safeCategory(error))
            } catch (error: LinkageError) {
                logWarning("Nextcloud send linkage failure: ${error.javaClass.simpleName}")
                NextcloudSendResult.Failed("Nextcloud transport unavailable")
            }
        }

    private fun safeCategory(error: Throwable): String = when (error) {
        is java.net.UnknownHostException -> "Nextcloud host not found"
        is java.io.IOException -> "Nextcloud connection failed"
        else -> "Nextcloud send failed"
    }

    private companion object {
        const val TAG = "SmsNtfyNextcloud"
    }
}
