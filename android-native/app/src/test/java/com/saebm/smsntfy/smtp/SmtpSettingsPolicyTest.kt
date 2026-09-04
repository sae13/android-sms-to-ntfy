package com.saebm.smsntfy.smtp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpSettingsPolicyTest {

    @Test
    fun validEnabledSettingsAreNormalized() {
        val result = SmtpSettingsPolicy.validate(
            requestedEnabled = true,
            host = "  mail.example.com ",
            port = "587",
            username = " user@example.com ",
            password = "secret",
            from = " from@example.com ",
            recipient = " to@example.com "
        )
        assertTrue(result is SmtpSettingsValidation.Valid)
        val config = (result as SmtpSettingsValidation.Valid).config
        assertEquals("mail.example.com", config.host)
        assertEquals(587, config.port)
        assertEquals("user@example.com", config.username)
        assertEquals("from@example.com", config.from)
        assertEquals("to@example.com", config.recipient)
    }

    @Test
    fun disabledRequiresNothing() {
        val result = SmtpSettingsPolicy.validate(
            requestedEnabled = false, host = "", port = "", username = "", password = "", from = "", recipient = ""
        )
        assertTrue(result is SmtpSettingsValidation.Valid)
        assertFalse((result as SmtpSettingsValidation.Valid).config.enabled)
    }

    @Test
    fun missingHostIsInvalid() {
        val result = validate(host = " ")
        assertEquals(SmtpSettingsField.HOST, result)
    }

    @Test
    fun invalidPortIsInvalid() {
        assertEquals(SmtpSettingsField.PORT, validate(port = "abc"))
        assertEquals(SmtpSettingsField.PORT, validate(port = "0"))
        assertEquals(SmtpSettingsField.PORT, validate(port = "70000"))
    }

    @Test
    fun missingRecipientIsInvalid() {
        assertEquals(SmtpSettingsField.RECIPIENT, validate(recipient = "not-an-email"))
    }

    private fun validate(
        host: String = "mail.example.com",
        port: String = "587",
        username: String = "user@example.com",
        password: String = "secret",
        from: String = "from@example.com",
        recipient: String = "to@example.com"
    ): SmtpSettingsField? = when (
        val r = SmtpSettingsPolicy.validate(true, host, port, username, password, from, recipient)
    ) {
        is SmtpSettingsValidation.Valid -> null
        is SmtpSettingsValidation.Invalid -> r.field
    }
}
