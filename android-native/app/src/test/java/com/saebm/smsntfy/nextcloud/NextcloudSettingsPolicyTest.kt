package com.saebm.smsntfy.nextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudSettingsPolicyTest {

    @Test
    fun validEnabledSettingsAreNormalized() {
        val result = NextcloudSettingsPolicy.validate(
            requestedEnabled = true,
            serverUrl = " https://cloud.example.com ",
            username = " user ",
            appPassword = "tok",
            talkToken = " abcd1234 "
        )
        assertTrue(result is NextcloudSettingsValidation.Valid)
        val config = (result as NextcloudSettingsValidation.Valid).config
        assertEquals("https://cloud.example.com", config.serverUrl)
        assertEquals("user", config.username)
        assertEquals("abcd1234", config.talkToken)
    }

    @Test
    fun disabledRequiresNothing() {
        val result = NextcloudSettingsPolicy.validate(false, "", "", "", "")
        assertTrue(result is NextcloudSettingsValidation.Valid)
        assertFalse((result as NextcloudSettingsValidation.Valid).config.enabled)
    }

    @Test
    fun nonHttpsServerIsInvalid() {
        val result = NextcloudSettingsPolicy.validate(true, "http://cloud.example.com", "u", "t", "abcd1234")
        assertEquals(NextcloudSettingsField.SERVER_URL, (result as NextcloudSettingsValidation.Invalid).field)
    }

    @Test
    fun missingCredentialsAreInvalid() {
        assertEquals(
            NextcloudSettingsField.USERNAME,
            fieldOf(serverUrl = "https://c.io", username = " ", appPassword = "t", talkToken = "abcd1234")
        )
        assertEquals(
            NextcloudSettingsField.APP_PASSWORD,
            fieldOf(serverUrl = "https://c.io", username = "u", appPassword = "", talkToken = "abcd1234")
        )
        assertEquals(
            NextcloudSettingsField.TALK_TOKEN,
            fieldOf(serverUrl = "https://c.io", username = "u", appPassword = "t", talkToken = "no")
        )
    }

    private fun fieldOf(
        serverUrl: String, username: String, appPassword: String, talkToken: String
    ): NextcloudSettingsField = when (
        val r = NextcloudSettingsPolicy.validate(true, serverUrl, username, appPassword, talkToken)
    ) {
        is NextcloudSettingsValidation.Invalid -> r.field
        is NextcloudSettingsValidation.Valid -> error("expected invalid")
    }
}
