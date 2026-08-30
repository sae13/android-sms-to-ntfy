package com.smsntfy.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBotClientTest {
    private val client = TelegramBotClient {
        TelegramConfig(
            enabled = true,
            botToken = "123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd",
            chatId = "-1001234567890"
        )
    }

    @Test
    fun validatesConfigurationAndRedactsSecrets() {
        assertTrue(TelegramBotClient.isValidBotToken("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd"))
        assertFalse(TelegramBotClient.isValidBotToken("token"))
        assertTrue(TelegramBotClient.isValidChatId("-100123"))
        assertFalse(TelegramBotClient.isValidChatId("@channel"))
        assertFalse(TelegramBotClient.redact("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd").contains("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    }

    @Test
    fun parsesOnlyRealRepliesAndMarksEditedMessages() {
        val updates = client.parseUpdates(
            """{"ok":true,"result":[
              {"update_id":7,"message":{"message_id":11,"from":{"id":4,"is_bot":false},"chat":{"id":-1001234567890},"text":"reply",
                "reply_to_message":{"message_id":9,"from":{"id":8,"is_bot":true},"chat":{"id":-1001234567890},"text":"SMS"}}},
              {"update_id":8,"edited_message":{"message_id":12,"from":{"id":4,"is_bot":false},"chat":{"id":-1001234567890},"text":"edited",
                "reply_to_message":{"message_id":9,"from":{"id":8,"is_bot":true},"chat":{"id":-1001234567890},"text":"SMS"}}}
            ]}"""
        )
        assertEquals(2, updates.size)
        assertEquals(7L, updates[0].updateId)
        assertEquals("-1001234567890", updates[0].chatId)
        assertEquals(9, updates[0].replyToMessageId)
        assertTrue(updates[0].repliedMessageFromBot)
        assertFalse(updates[0].fromBot)
        assertTrue(updates[1].edited)
    }

    @Test
    fun rejectedOrMalformedUpdatePayloadIsFailure() {
        assertTrue(client.parseUpdatesResultForTest("{\"ok\":false}") is TelegramUpdatesResult.Failed)
        assertTrue(client.parseUpdatesResultForTest("{\"ok\":true}") is TelegramUpdatesResult.Failed)
        assertTrue(client.parseUpdatesResultForTest("{\"ok\":true,\"result\":null}") is TelegramUpdatesResult.Failed)
        assertTrue(client.parseUpdatesResultForTest("not-json") is TelegramUpdatesResult.Failed)
    }

    @Test
    fun formatsSmsWithoutEmbeddingCredentials() {
        val body = TelegramBotClient.formatSmsMessage("+15551234567", "Alice", "hello", 123)
        assertTrue(body.contains("Alice"))
        assertTrue(body.contains("hello"))
        assertFalse(body.contains("botToken"))
    }
}
