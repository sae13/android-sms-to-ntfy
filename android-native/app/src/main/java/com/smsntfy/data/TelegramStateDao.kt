package com.smsntfy.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TelegramStateDao {
    @Query("SELECT nextOffset FROM telegram_state WHERE singleton = 1")
    suspend fun nextOffset(): Long

    @Query("UPDATE telegram_state SET nextOffset = :offset WHERE singleton = 1")
    suspend fun saveOffset(offset: Long): Int

    @Transaction
    suspend fun advanceTo(offset: Long) {
        if (offset > nextOffset()) saveOffset(offset)
    }
}
