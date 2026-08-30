package com.smsntfy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelegramReplyMappingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mapping: TelegramReplyMapping): Long

    @Query("SELECT * FROM telegram_reply_mappings WHERE chatId = :chatId AND telegramMessageId = :messageId LIMIT 1")
    suspend fun find(chatId: String, messageId: Int): TelegramReplyMapping?
}
