package com.saebm.smsntfy.telegram

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramBotClientTest {
    @Test
    fun validatesConfigurationAndRedactsSecrets() {
        assertTrue(TelegramBotClient.isValidBotToken("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd"))
        assertFalse(TelegramBotClient.isValidBotToken("token"))
        assertTrue(TelegramBotClient.isValidChatId("-100123"))
        assertFalse(TelegramBotClient.isValidChatId("@channel"))
        assertFalse(TelegramBotClient.redact("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd").contains("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    }

    @Test
    fun formatsSmsWithoutEmbeddingCredentials() {
        val body = TelegramBotClient.formatSmsMessage("+155****4567", "Alice", "hello", 123)
        assertTrue(body.contains("Alice"))
        assertTrue(body.contains("hello"))
        assertFalse(body.contains("botToken"))
    }

    @Test
    fun `cancelling send cancels active HTTP call`() = runBlocking {
        val call = BlockingCall()
        val client = TelegramBotClient.forTest(
            configProvider = { TelegramConfig(true, VALID_TOKEN, "1") },
            callFactory = Call.Factory { call }
        )

        val send = async(start = CoroutineStart.UNDISPATCHED) { client.sendMessage("1", "hello") }
        assertTrue(call.awaitStarted())
        send.cancelAndJoin()

        assertTrue(call.isCanceled())
    }

    @Test
    fun `connection failure before request write is retryable`() = runBlocking {
        val client = TelegramBotClient.forTest(
            configProvider = { TelegramConfig(true, VALID_TOKEN, "1") },
            callFactory = Call.Factory { ImmediateFailureCall() }
        )

        assertTrue(client.sendMessage("1", "hello") is TelegramSendResult.RouteUnavailable)
    }

    @Test
    fun `HTTP rejection reason preserves the safe status code`() {
        assertEquals(
            "Telegram rejected the request (HTTP 403)",
            TelegramBotClient.httpFailureReasonForTest(403)
        )
    }

    @Test
    fun `HTTP rejection returned by Telegram is permanent and includes status`() = runBlocking {
        val client = TelegramBotClient.forTest(
            configProvider = { TelegramConfig(true, VALID_TOKEN, "1") },
            callFactory = Call.Factory { request ->
                ImmediateResponseCall(
                    request,
                    403,
                    """{"ok":false,"error_code":403,"description":"Forbidden: bot was blocked by the user"}"""
                )
            }
        )

        val result = client.sendMessage("1", "hello")

        assertTrue(result is TelegramSendResult.Failed)
        assertEquals(
            "Forbidden: bot was blocked by the user",
            (result as TelegramSendResult.Failed).reason
        )
    }

    private class BlockingCall : Call {
        private val started = java.util.concurrent.CountDownLatch(1)
        @Volatile private var canceled = false

        fun awaitStarted(): Boolean = started.await(1, TimeUnit.SECONDS)
        override fun enqueue(responseCallback: Callback) { started.countDown() }
        override fun cancel() { canceled = true }
        override fun isCanceled(): Boolean = canceled
        override fun request(): Request = Request.Builder().url("https://example.test/").build()
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun isExecuted(): Boolean = true
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
        override fun clone(): Call = this
    }

    private class ImmediateFailureCall : Call {
        override fun enqueue(responseCallback: Callback) = responseCallback.onFailure(this, IOException("connect failed"))
        override fun cancel() = Unit
        override fun isCanceled(): Boolean = false
        override fun request(): Request = Request.Builder().url("https://example.test/").build()
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun isExecuted(): Boolean = true
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
        override fun clone(): Call = this
    }

    private class ImmediateResponseCall(
        private val outgoingRequest: Request,
        private val statusCode: Int,
        private val responseBody: String
    ) : Call {
        override fun enqueue(responseCallback: Callback) {
            responseCallback.onResponse(
                this,
                Response.Builder()
                    .request(outgoingRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("rejected")
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
            )
        }
        override fun cancel() = Unit
        override fun isCanceled(): Boolean = false
        override fun request(): Request = outgoingRequest
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun isExecuted(): Boolean = true
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
        override fun clone(): Call = this
    }

    private companion object {
        const val VALID_TOKEN = "123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd"
    }
}