package com.smsntfy.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSettingsPolicyTest {
    @Test
    fun validEnabledSettingsAreNormalizedAndAccepted() {
        val result = TelegramSettingsPolicy.validate(
            requestedEnabled = true,
            botToken = " 123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd ",
            chatId = " -1001234567890 ",
            proxy = "  "
        )

        assertTrue(result is TelegramSettingsValidation.Valid)
        result as TelegramSettingsValidation.Valid
        assertTrue(result.config.enabled)
        assertEquals("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcd", result.config.botToken)
        assertEquals("-1001234567890", result.config.chatId)
        assertEquals("", result.config.proxy)
    }

    @Test
    fun invalidEnabledSettingsAreRejectedInsteadOfSilentlyDisabled() {
        val result = TelegramSettingsPolicy.validate(
            requestedEnabled = true,
            botToken = "bad",
            chatId = "-1001234567890",
            proxy = ""
        )

        assertTrue(result is TelegramSettingsValidation.Invalid)
        assertEquals(TelegramSettingsField.BOT_TOKEN, (result as TelegramSettingsValidation.Invalid).field)
    }

    @Test
    fun disabledSettingsMayBeSavedWithoutCredentials() {
        val result = TelegramSettingsPolicy.validate(false, "", "", "")

        assertTrue(result is TelegramSettingsValidation.Valid)
        assertFalse((result as TelegramSettingsValidation.Valid).config.enabled)
    }
}
