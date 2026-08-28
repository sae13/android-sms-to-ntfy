package com.smsntfy.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class NtfyClient(private val config: NtfyConfig, private val http: HttpClient = defaultHttpClient()) {
    suspend fun forwardSms(event: SmsEvent): Boolean = publish(NtfyMessage(config.topic, event.body, "${event.contact} <${event.sender}>", config.priority, listOf("sms", "inbox"), "sms:${event.sender}"))
    suspend fun sendTest(): Boolean = publish(NtfyMessage(config.topic, "Test message from SMS-to-ntfy KMP", "SMS-to-ntfy Test", 3, listOf("test")))
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun publish(message: NtfyMessage): Boolean {
        val response: HttpResponse = http.post(config.publishUrl) {
            contentType(ContentType.Application.Json); setBody(message)
            if (config.username.isNotBlank()) header(HttpHeaders.Authorization, "Basic " + Base64.encode("${config.username}:${config.password}".encodeToByteArray()))
        }
        return response.status.isSuccess()
    }
    fun close() = http.close()
}
fun defaultHttpClient() = HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) } }
