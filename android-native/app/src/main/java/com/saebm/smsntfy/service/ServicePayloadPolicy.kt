package com.saebm.smsntfy.service

/**
 * Framework-independent SMS data transferred across the receiver/service boundary.
 */
data class SmsPayload(
    val sender: String,
    val body: String,
    val timestamp: Long
) {
    companion object {
        private const val MULTIPART_TIMESTAMP_WINDOW_MS = 5_000L

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
            val timestampsFitOneMessage = validParts.all {
                kotlin.math.abs(it.timestamp - first.timestamp) <= MULTIPART_TIMESTAMP_WINDOW_MS
            }
            return if (validParts.all { it.sender == first.sender } && timestampsFitOneMessage) {
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
        SmsForwardingService.ACTION_STOP_SERVICE
    )

    fun isKnown(action: String): Boolean = action in knownActions

    fun requiresImmediateForeground(action: String?): Boolean =
        action != null && action != SmsForwardingService.ACTION_STOP_SERVICE

    fun supportsTypedForeground(sdkInt: Int): Boolean = sdkInt >= 29

    fun foregroundServiceTypes(sdkInt: Int): Int =
        if (sdkInt >= 34) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

    fun requiresDependencies(action: String): Boolean =
        action != SmsForwardingService.ACTION_STOP_SERVICE

    fun persistedRunningStateBeforeDependencies(action: String): Boolean? =
        false.takeIf { action == SmsForwardingService.ACTION_STOP_SERVICE }

    fun actionForRestart(isPersistent: Boolean): String? =
        if (isPersistent) SmsForwardingService.ACTION_START_SERVICE else null

    fun shouldStopAfterEvent(isPersistent: Boolean): Boolean = !isPersistent

    fun startIdToStopAfterOneShot(isPersistent: Boolean, startId: Int): Int? =
        if (isPersistent) null else startId

    fun shouldStopRejectedStart(isPersistent: Boolean, hasActiveOneShots: Boolean): Boolean =
        !isPersistent && !hasActiveOneShots
}

class OneShotStartTracker {
    private val activeStartIds = mutableSetOf<Int>()
    private var latestStartId: Int? = null

    @Synchronized
    fun register(startId: Int) {
        activeStartIds += startId
        latestStartId = maxOf(latestStartId ?: startId, startId)
    }

    @Synchronized
    fun hasActiveStarts(): Boolean = activeStartIds.isNotEmpty()

    @Synchronized
    fun abandon(startId: Int) {
        activeStartIds -= startId
        if (activeStartIds.isEmpty()) latestStartId = null
    }

    @Synchronized
    fun complete(startId: Int, isPersistent: Boolean): Int? {
        activeStartIds -= startId
        if (isPersistent || activeStartIds.isNotEmpty()) return null
        return latestStartId.also { latestStartId = null }
    }
}
