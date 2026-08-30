package com.smsntfy.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseReconnectPolicyTest {
    @Test
    fun unexpectedCloseOfCurrentConnectionReconnects() {
        assertTrue(SseReconnectPolicy.shouldReconnect(stopped = false, callbackIsCurrent = true))
    }

    @Test
    fun explicitStopAndStaleCallbacksNeverReconnect() {
        assertFalse(SseReconnectPolicy.shouldReconnect(stopped = true, callbackIsCurrent = true))
        assertFalse(SseReconnectPolicy.shouldReconnect(stopped = false, callbackIsCurrent = false))
    }
}
