package com.saebm.smsntfy.nextcloud

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudTalkClientTest {

    private fun config(serverUrl: String) = NextcloudConfig(
        enabled = true,
        serverUrl = serverUrl,
        username = "user",
        appPassword = "secret",
        talkToken = "abcd1234"
    )

    @Test
    fun sendsMessageToTalkRoom() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ocs":{"meta":{"status":"ok"},"data":{}}}"""))
        server.start()
        try {
            val client = NextcloudTalkClient(OkHttpClient(), logWarning = {})
            val result = runBlocking { client.send(config(server.url("/").toString().trimEnd('/')), "hello") }
            assertTrue("expected Sent but was $result", result is NextcloudSendResult.Sent)
            val recorded = server.takeRequest()
            assertEquals("/ocs/v2.php/apps/spreed/api/v1/chat/abcd1234", recorded.path)
            assertEquals("POST", recorded.method)
            assertTrue(recorded.getHeader("Authorization")?.startsWith("Basic ") == true)
            assertFalse(recorded.body.readUtf8().contains("secret"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun nonOkResponseIsFailure() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        server.start()
        try {
            val client = NextcloudTalkClient(OkHttpClient(), logWarning = {})
            val result = runBlocking { client.send(config(server.url("/").toString().trimEnd('/')), "hello") }
            assertTrue(result is NextcloudSendResult.Failed)
            assertFalse((result as NextcloudSendResult.Failed).reason.contains("secret"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disabledConfigFailsClosed() {
        val client = NextcloudTalkClient(OkHttpClient(), logWarning = {})
        val result = runBlocking {
            client.send(config("https://x").copy(enabled = false), "hello")
        }
        assertTrue(result is NextcloudSendResult.Failed)
    }
}
