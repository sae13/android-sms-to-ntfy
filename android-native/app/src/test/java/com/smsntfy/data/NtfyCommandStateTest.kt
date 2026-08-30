package com.smsntfy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfyCommandStateTest {
    @Test
    fun claimedCommandsCanOnlyFinalizeToDocumentedTerminalOutcomes() {
        assertTrue(NtfyCommandState.canComplete("claimed", "sent"))
        assertTrue(NtfyCommandState.canComplete("claimed", "failed"))
        assertTrue(NtfyCommandState.canComplete("claimed", "invalid"))
        assertFalse(NtfyCommandState.canComplete("sent", "failed"))
        assertFalse(NtfyCommandState.canComplete("claimed", "claimed"))
    }

    @Test
    fun processDeathRecoveryIsExplicitlyAtMostOnceAndNeverRetriesSms() {
        assertEquals("failed", NtfyCommandState.PROCESS_DEATH_OUTCOME)
        assertFalse(NtfyCommandState.isRetryable(NtfyCommandState.PROCESS_DEATH_OUTCOME))
        assertFalse(NtfyCommandState.isRetryable("claimed"))
    }
}
