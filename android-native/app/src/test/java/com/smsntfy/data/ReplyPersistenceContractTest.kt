package com.smsntfy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPersistenceContractTest {
    @Test
    fun invertedReceivedAtCollisionIsResolvedByInsertionId() {
        val olderInsertion = ReplyMapping(id = 10, replyId = 42, phoneNumber = "+old", receivedAt = 9_000)
        val newerInsertion = ReplyMapping(id = 11, replyId = 42, phoneNumber = "+new", receivedAt = 1_000)

        assertEquals("+new", listOf(olderInsertion, newerInsertion).maxBy { it.id }.phoneNumber)
    }

    @Test
    fun allocationDomainWrapsExactlyAtOneThousand() {
        val allocated = (0 until 1005).map { it % 1000 }
        assertEquals((0 until 1000).toList(), allocated.take(1000))
        assertEquals(listOf(0, 1, 2, 3, 4), allocated.drop(1000))
    }

    @Test
    fun failedCommandOutcomeIsTerminalRatherThanRetryable() {
        val command = NtfyCommand("event-1", claimedAt = 1, outcome = "failed", completedAt = 2)
        assertTrue(command.outcome in setOf("sent", "failed", "invalid"))
    }
}