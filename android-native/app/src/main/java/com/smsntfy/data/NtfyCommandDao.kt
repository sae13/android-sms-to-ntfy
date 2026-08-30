package com.smsntfy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NtfyCommandDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(command: NtfyCommand): Long

    @Transaction
    suspend fun claim(eventId: String, claimedAt: Long): Boolean {
        if (eventId.isBlank()) return false
        return insert(NtfyCommand(eventId = eventId, claimedAt = claimedAt)) != -1L
    }

    @Query("UPDATE ntfy_commands SET outcome = :outcome, completedAt = :completedAt WHERE eventId = :eventId AND outcome = 'claimed'")
    suspend fun complete(eventId: String, outcome: String, completedAt: Long): Int

    @Query("UPDATE ntfy_commands SET outcome = 'failed', completedAt = :completedAt WHERE outcome = 'claimed' AND claimedAt < :claimedBefore")
    suspend fun finalizeStaleClaims(claimedBefore: Long, completedAt: Long): Int
}