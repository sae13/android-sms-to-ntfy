package com.saebm.smsntfy.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramErrorRedactionTest {
    @Test
    fun `proxy credentials and mtproto secret are never returned`() {
        val sensitive = "socks5://alice:s3cr3t@[bad:1080 secret=ee5f7ce28a2c4816"
        val redacted = TelegramBotClient.redactedErrorForTest(IllegalArgumentException(sensitive))

        assertFalse(redacted.contains("alice"))
        assertFalse(redacted.contains("s3cr3t"))
        assertFalse(redacted.contains("ee5f7ce28a2c4816"))
        assertTrue(redacted.contains("Telegram"))
    }
}
