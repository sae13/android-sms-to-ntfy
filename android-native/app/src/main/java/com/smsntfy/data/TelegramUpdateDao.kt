package com.smsntfy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TelegramUpdateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(update: TelegramUpdate): Long

    @Transaction
    suspend fun claim(updateId: Long, claimedAt: Long): Boolean =
        updateId >= 0 && insert(TelegramUpdate(updateId, claimedAt)) != -1L

    @Query("UPDATE telegram_updates SET outcome = :outcome, completedAt = :completedAt WHERE updateId = :updateId AND outcome = 'claimed'")
    suspend fun complete(updateId: Long, outcome: String, completedAt: Long): Int

    @Query("UPDATE telegram_updates SET outcome = 'failed', completedAt = :completedAt WHERE outcome = 'claimed' AND claimedAt < :claimedBefore")
    suspend fun finalizeStaleClaims(claimedBefore: Long, completedAt: Long): Int
}
