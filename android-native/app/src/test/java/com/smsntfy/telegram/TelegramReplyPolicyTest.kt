package com.smsntfy.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramReplyPolicyTest {
    private val baseReply = TelegramUpdate(
        updateId = 7,
        chatId = "-1001234567890",
        messageId = 11,
        text = "reply text",
        replyToMessageId = 9,
        repliedMessageFromBot = true,
        fromBot = false
    )

    @Test
    fun validReplyRoutesExactTextToMappedPhone() {
        assertEquals(
            TelegramReplyDecision.Send("+15551234567", "reply text"),
            TelegramReplyPolicy.decide(baseReply, "-1001234567890", "+15551234567")
        )
    }

    @Test
    fun otherChatUnknownMessageAndMissingReplyAreRejected() {
        val otherChat = baseReply.copy(chatId = "-1009999999999")
        val unknownMessage = TelegramReplyPolicy.decide(baseReply, "-1001234567890", null)
        val noReply = TelegramReplyPolicy.decide(
            baseReply.copy(replyToMessageId = null),
            "-1001234567890",
            "+15551234567"
        )

        assertTrue(
            TelegramReplyPolicy.decide(otherChat, "-1001234567890", "+15551234567") is TelegramReplyDecision.Reject
        )
        assertTrue(unknownMessage is TelegramReplyDecision.Reject)
        assertTrue(noReply is TelegramReplyDecision.Reject)
    }

    @Test
    fun emptyEditedBotAndInvalidPhoneRepliesAreRejected() {
        listOf(
            baseReply.copy(text = " "),
            baseReply.copy(edited = true),
            baseReply.copy(fromBot = true),
            baseReply.copy(repliedMessageFromBot = false)
        ).forEach { update ->
            assertTrue(
                TelegramReplyPolicy.decide(update, "-1001234567890", "+15551234567") is TelegramReplyDecision.Reject
            )
        }
        assertTrue(
            TelegramReplyPolicy.decide(baseReply, "-1001234567890", "not-a-phone") is TelegramReplyDecision.Reject
        )
    }
}
