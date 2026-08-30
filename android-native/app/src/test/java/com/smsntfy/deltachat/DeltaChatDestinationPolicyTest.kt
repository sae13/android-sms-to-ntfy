package com.smsntfy.deltachat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaChatDestinationPolicyTest {
    @Test
    fun upgradedInstallKeepsDeltaChatDisabled() {
        assertFalse(DeltaChatDestinationPolicy.isReady(enabled = false, chatId = 7))
    }

    @Test
    fun enabledDestinationRequiresPersistedChat() {
        assertFalse(DeltaChatDestinationPolicy.isReady(enabled = true, chatId = 0))
        assertTrue(DeltaChatDestinationPolicy.isReady(enabled = true, chatId = 7))
    }
}
