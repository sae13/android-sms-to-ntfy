package com.smsntfy.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class NtfyClientTest {
    @Test fun buildsUrls() { val c=NtfyConfig("https://ntfy.sh/", "/alerts", "/replies"); assertEquals("https://ntfy.sh/alerts",c.publishUrl); assertEquals("https://ntfy.sh/replies/sse",c.sseUrl) }
    @Test fun forwardsSms() = runTest {
        var body=""; val engine=MockEngine { request -> body=request.body.toByteArray().decodeToString(); respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json")) }
        val http=HttpClient(engine){ install(ContentNegotiation){ json(Json) } }
        assertTrue(NtfyClient(NtfyConfig(topic="alerts"),http).forwardSms(SmsEvent("+123","Alice","hello",0))); assertContains(body,"hello"); assertContains(body,"sms:+123")
    }
}
