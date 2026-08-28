package com.smsntfy.flutter

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class NtfyPublisher(context: Context) {
    private val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    fun publish(title: String, message: String, tags: List<String>, click: String? = null): Boolean = try {
        val server = prefs.getString(Prefs.SERVER, "https://ntfy.sh")!!.trimEnd('/')
        val topic = prefs.getString(Prefs.TOPIC, "sms-alerts")!!.trimStart('/')
        val payload = JSONObject().put("topic", topic).put("title", title).put("message", message).put("priority", prefs.getInt(Prefs.PRIORITY, 4)).put("tags", JSONArray(tags)).apply { if (click != null) put("click", click) }.toString()
        val request = Request.Builder().url("$server/$topic").post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).apply {
            val username = prefs.getString(Prefs.USERNAME, "").orEmpty(); val password = prefs.getString(Prefs.PASSWORD, "").orEmpty()
            if (username.isNotBlank()) header("Authorization", "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP))
        }.build()
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) { false }
}
