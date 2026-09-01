package com.saebm.smsntfy.aether

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherSessionPolicyTest {
    @Test
    fun startupTimeoutCoversNativeScanStartupAndValidationBudgets() {
        val nativeWorstCaseMs = 45_000L + 25_000L + 10_000L

        assertTrue(AetherSessionManager.START_TIMEOUT_MS > nativeWorstCaseMs)
    }

    @Test
    fun discoveryTimeoutCoversAllRoutesAndStopOverhead() {
        val telegramProbeWorstCaseMs = 8_000L + 8_000L
        val failedRouteStopOverheadMs = 2 * AetherSessionManager.STOP_TIMEOUT_MS
        val worstCasePerRouteMs =
            AetherSessionManager.START_TIMEOUT_MS + telegramProbeWorstCaseMs + failedRouteStopOverheadMs
        val discoveryWorstCaseMs = AetherRoutes.supported.size * worstCasePerRouteMs

        assertTrue(AetherSessionManager.DISCOVERY_TIMEOUT_MS > discoveryWorstCaseMs)
    }

    @Test
    fun staleProcessIsNotReusableWithoutASelectedRoute() {
        assertFalse(
            AetherSessionPolicy.canReuse(
                processAlive = true,
                hasActiveRoute = false,
                endpointsMatch = true
            )
        )
    }

    @Test
    fun selectedRouteIsReusableOnlyForMatchingListenerMode() {
        assertTrue(
            AetherSessionPolicy.canReuse(
                processAlive = true,
                hasActiveRoute = true,
                endpointsMatch = true
            )
        )
        assertFalse(
            AetherSessionPolicy.canReuse(
                processAlive = true,
                hasActiveRoute = true,
                endpointsMatch = false
            )
        )
    }

    @Test
    fun keepAliveIsEnabledOnlyWhenBothSettingsAreEnabled() {
        assertTrue(AetherSessionPolicy.shouldKeepAlive(aetherEnabled = true, alwaysOn = true))
        assertFalse(AetherSessionPolicy.shouldKeepAlive(aetherEnabled = false, alwaysOn = true))
        assertFalse(AetherSessionPolicy.shouldKeepAlive(aetherEnabled = true, alwaysOn = false))
    }
}
