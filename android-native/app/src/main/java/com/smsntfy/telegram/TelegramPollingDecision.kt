package com.smsntfy.telegram

data class TelegramOffsetDecision(
    val advanceOffset: Boolean,
    val nextOffset: Long,
    val stopPolling: Boolean = false
)

object TelegramPollingDecision {
    /**
     * A claimed update follows the service's durable at-most-once policy even if
     * handling later fails, so the offset must move past it and never hot-loop.
     */
    fun afterUpdate(updateId: Long): TelegramOffsetDecision = when {
        updateId == Long.MAX_VALUE -> TelegramOffsetDecision(false, Long.MAX_VALUE, stopPolling = true)
        else -> TelegramOffsetDecision(true, updateId + 1)
    }
}
