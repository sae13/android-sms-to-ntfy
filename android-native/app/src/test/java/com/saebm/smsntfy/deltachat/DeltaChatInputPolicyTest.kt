package com.saebm.smsntfy.deltachat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaChatInputPolicyTest {
    @Test
    fun acceptsDeltaChatLoginCodeCaseInsensitively() {
        assertTrue(DeltaChatInputPolicy.isLoginCode("  DCLOGIN:user@example.org/?p=secret  "))
    }

    @Test
    fun rejectsOtherSchemesAsLoginCode() {
        assertFalse(DeltaChatInputPolicy.isLoginCode("https://example.org"))
        assertFalse(DeltaChatInputPolicy.isLoginCode("dcaccount:https://example.org"))
    }

    @Test
    fun acceptsDeltaChatInviteFragment() {
        assertTrue(DeltaChatInputPolicy.isInvite("https://i.delta.chat/#fingerprint&v=3"))
        assertTrue(DeltaChatInputPolicy.isInvite("OPENPGP4FPR:fingerprint#a=peer@example.org"))
    }

    @Test
    fun rejectsBlankOrUnrelatedInvite() {
        assertFalse(DeltaChatInputPolicy.isInvite(""))
        assertFalse(DeltaChatInputPolicy.isInvite("https://example.org/#fingerprint"))
    }
}
