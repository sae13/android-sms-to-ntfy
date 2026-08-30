package com.smsntfy.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPollingDecisionTest {
    @Test
    fun completedUpdateAdvancesOffset() {
        val decision = TelegramPollingDecision.afterUpdate(41L)

        assertTrue(decision.advanceOffset)
        assertEquals(42L, decision.nextOffset)
    }

    @Test
    fun claimedUpdateAdvancesOffsetUnderAtMostOncePolicy() {
        val decision = TelegramPollingDecision.afterUpdate(41L)

        assertTrue(decision.advanceOffset)
        assertEquals(42L, decision.nextOffset)
    }

    @Test
    fun maxUpdateIdDoesNotOverflowOffset() {
        val decision = TelegramPollingDecision.afterUpdate(Long.MAX_VALUE)

        assertFalse(decision.advanceOffset)
        assertTrue(decision.stopPolling)
        assertEquals(Long.MAX_VALUE, decision.nextOffset)
    }
}
