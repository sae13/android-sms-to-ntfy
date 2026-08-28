package com.smsntfy.shared

import kotlinx.serialization.Serializable

@Serializable
data class NtfyConfig(val server: String = "https://ntfy.sh", val topic: String = "sms-alerts", val replyTopic: String = "sms-replies", val username: String = "", val password: String = "", val priority: Int = 4) {
    val publishUrl get() = "${server.trimEnd('/')}/${topic.trimStart('/')}"
    val sseUrl get() = "${server.trimEnd('/')}/${replyTopic.trimStart('/')}/sse"
}

@Serializable
data class NtfyMessage(val topic: String, val message: String, val title: String, val priority: Int = 4, val tags: List<String> = listOf("sms", "inbox"), val click: String? = null)

data class SmsEvent(val sender: String, val contact: String = sender, val body: String, val timestamp: Long)
