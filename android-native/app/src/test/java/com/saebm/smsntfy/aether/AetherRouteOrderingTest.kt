package com.saebm.smsntfy.aether

import org.junit.Assert.assertEquals
import org.junit.Test

class AetherRouteOrderingTest {
    private val routes = AetherRoutes.supported

    @Test
    fun cachedRouteMovesOnlyItsExactVariantToTheFront() {
        assertEquals(
            listOf("masque-h2", "masque-h3", "wireguard", "gool"),
            AetherRouteOrdering.preferCached(routes, "masque-h2").map { it.id }
        )
    }

    @Test
    fun legacyProtocolCacheUsesTheDefaultVariant() {
        assertEquals(
            listOf("masque-h3", "masque-h2", "wireguard", "gool"),
            AetherRouteOrdering.preferCached(routes, "masque").map { it.id }
        )
    }
}
