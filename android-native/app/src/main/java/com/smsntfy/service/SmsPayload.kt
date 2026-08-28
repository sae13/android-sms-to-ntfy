package com.smsntfy.service

/**
 * Framework-independent SMS data transferred across the receiver/service boundary.
 */
data class SmsPayload(
    val sender: String,
    val body: String,
    val timestamp: Long
) {
    companion object {
        /**
         * Rebuilds complete messages from the primitive arrays carried by an Intent.
         * Parts from one Android SMS broadcast are merged when they belong to the same sender;
         * Android supplies one broadcast per received message and preserves multipart order.
         */
        fun fromParts(
            senders: Array<String>?,
            bodies: Array<String>?,
            timestamps: LongArray?
        ): List<SmsPayload> {
            if (
                senders == null || bodies == null || timestamps == null ||
                senders.isEmpty() ||
                senders.size != bodies.size || senders.size != timestamps.size
            ) {
                return emptyList()
            }

            val parts = senders.indices.map { index ->
                SmsPayload(senders[index], bodies[index], timestamps[index])
            }
            val validParts = parts.filter { it.sender.isNotBlank() && it.body.isNotEmpty() }
            if (validParts.isEmpty()) return emptyList()

            val first = validParts.first()
            return if (validParts.all { it.sender == first.sender }) {
                listOf(first.copy(body = validParts.joinToString(separator = "") { it.body }))
            } else {
                validParts
            }
        }
    }
}

/** Rules shared by service entry points and their unit tests. */
object ServiceStartPolicy {
    private val knownActions = setOf(
        SmsForwardingService.ACTION_START_SERVICE,
        SmsForwardingService.ACTION_PROCESS_SMS,
        SmsForwardingService.ACTION_PROCESS_CALL,
        SmsForwardingService.ACTION_STOP_SERVICE,
        SmsForwardingService.ACTION_SEND_REPLY
    )

    fun isKnown(action: String): Boolean = action in knownActions

    fun requiresImmediateForeground(action: String): Boolean =
        action != SmsForwardingService.ACTION_STOP_SERVICE

    fun supportsTypedForeground(sdkInt: Int): Boolean = sdkInt >= 29

    fun actionForRestart(isPersistent: Boolean): String? =
        if (isPersistent) SmsForwardingService.ACTION_START_SERVICE else null
}
