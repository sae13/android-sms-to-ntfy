package com.saebm.smsntfy.aether

data class AetherUsageState(val users: Int = 0, val persistent: Boolean = false) {
    fun acquire(keepAlive: Boolean): AetherUsageState = copy(
        users = users + 1,
        persistent = persistent || keepAlive
    )

    fun release(): AetherUsageState = copy(users = (users - 1).coerceAtLeast(0))
    fun clearPersistent(): AetherUsageState = copy(persistent = false)
    fun shouldRun(): Boolean = users > 0 || persistent
}
