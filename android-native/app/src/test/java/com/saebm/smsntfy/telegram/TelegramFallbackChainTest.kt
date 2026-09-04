package com.saebm.smsntfy.telegram

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramFallbackChainTest {
    @Test
    fun `direct success skips Aether`() = runBlocking {
        val attempts = mutableListOf<String>()

        val result = TelegramFallbackChain.send(
            direct = {
                attempts += "direct"
                TelegramSendResult.Sent(1)
            },
            aether = {
                attempts += "aether"
                TelegramSendResult.Sent(2)
            }
        )

        assertEquals(listOf("direct"), attempts)
        assertEquals(TelegramSendResult.Sent(1), result)
    }

    @Test
    fun `Aether runs after definite direct route failure`() = runBlocking {
        val attempts = mutableListOf<String>()

        val result = TelegramFallbackChain.send(
            direct = {
                attempts += "direct"
                TelegramSendResult.RouteUnavailable("direct failed")
            },
            aether = {
                attempts += "aether"
                TelegramSendResult.Sent(2)
            }
        )

        assertEquals(listOf("direct", "aether"), attempts)
        assertEquals(TelegramSendResult.Sent(2), result)
    }

    @Test
    fun `direct route exception still falls back to Aether`() = runBlocking {
        val attempts = mutableListOf<String>()

        val result = TelegramFallbackChain.send(
            direct = {
                attempts += "direct"
                throw IllegalStateException("resolver failed")
            },
            aether = {
                attempts += "aether"
                TelegramSendResult.Sent(2)
            }
        )

        assertEquals(listOf("direct", "aether"), attempts)
        assertEquals(TelegramSendResult.Sent(2), result)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation stops fallback immediately`() {
        runBlocking {
            TelegramFallbackChain.send(
                direct = { throw CancellationException("stopped") },
                aether = { throw AssertionError("Aether must not run after cancellation") }
            )
        }
    }

    @Test
    fun `ambiguous send result stops fallback to avoid duplicate SMS`() = runBlocking {
        val attempts = mutableListOf<String>()

        val result = TelegramFallbackChain.send(
            direct = {
                attempts += "direct"
                TelegramSendResult.Ambiguous("Telegram response was not received")
            },
            aether = {
                attempts += "aether"
                TelegramSendResult.Sent(2)
            }
        )

        assertEquals(listOf("direct"), attempts)
        assertTrue(result is TelegramSendResult.Ambiguous)
    }

    @Test
    fun `permanent API failure stops fallback`() = runBlocking {
        val attempts = mutableListOf<String>()

        val result = TelegramFallbackChain.send(
            direct = {
                attempts += "direct"
                TelegramSendResult.Failed("Telegram rejected the request")
            },
            aether = {
                attempts += "aether"
                TelegramSendResult.Sent(2)
            }
        )

        assertEquals(listOf("direct"), attempts)
        assertTrue(result is TelegramSendResult.Failed)
    }
}
