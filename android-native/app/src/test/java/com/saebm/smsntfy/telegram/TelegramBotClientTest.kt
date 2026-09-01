package com.saebm.smsntfy.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}