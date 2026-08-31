package com.smsntfy.aether

object AetherSessionPolicy {
    fun canReuse(
        processAlive: Boolean,
        hasActiveRoute: Boolean,
        endpointsMatch: Boolean
    ): Boolean = processAlive && hasActiveRoute && endpointsMatch

    fun shouldKeepAlive(aetherEnabled: Boolean, alwaysOn: Boolean): Boolean =
        aetherEnabled && alwaysOn
}
