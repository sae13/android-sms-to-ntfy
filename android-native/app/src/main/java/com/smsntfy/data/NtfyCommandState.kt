package com.smsntfy.data

/**
 * At-most-once SMS policy: once an event is claimed it is never dispatched again.
 * Claims left by process death are finalized as failed on the next service startup,
 * making the loss window observable without risking a duplicate external SMS.
 */
object NtfyCommandState {
    const val PROCESS_DEATH_OUTCOME = "failed"
    val terminalOutcomes = setOf("sent", "failed", "invalid")

    fun canComplete(current: String, requested: String): Boolean =
        current == "claimed" && requested in terminalOutcomes

    fun isRetryable(@Suppress("UNUSED_PARAMETER") outcome: String): Boolean = false
}
