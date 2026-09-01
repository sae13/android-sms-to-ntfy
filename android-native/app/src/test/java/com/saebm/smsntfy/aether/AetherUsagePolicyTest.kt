package com.saebm.smsntfy.aether

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherUsagePolicyTest {
    @Test fun temporaryStopsAfterLastConcurrentUser() {
        var state = AetherUsageState().acquire(false).acquire(false)
        assertTrue(state.shouldRun())
        state = state.release(); assertTrue(state.shouldRun())
        state = state.release(); assertFalse(state.shouldRun())
    }

    @Test fun persistentSurvivesLastReleaseAndStopsWhenCleared() {
        var state = AetherUsageState().acquire(true).release()
        assertTrue(state.shouldRun())
        state = state.clearPersistent()
        assertFalse(state.shouldRun())
    }
}
