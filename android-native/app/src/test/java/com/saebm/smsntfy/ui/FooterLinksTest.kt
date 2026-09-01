package com.saebm.smsntfy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FooterLinksTest {
    @Test
    fun footerDestinationsAreExactAndSafe() {
        assertEquals(
            "https://github.com/sae13/android-sms-to-ntfy",
            FooterLinks.github
        )
        assertEquals("https://t.me/saeb_m", FooterLinks.telegram)
        assertEquals("mailto:ntfy-sms@00989133917225.ir", FooterLinks.email)
    }
}
