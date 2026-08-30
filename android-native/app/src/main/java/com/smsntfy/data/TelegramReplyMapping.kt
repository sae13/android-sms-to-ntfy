package com.smsntfy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable mapping from a Telegram message sent by this bot to the SMS number. */
@Entity(
    tableName = "telegram_reply_mappings",
    indices = [Index(value = ["chatId", "telegramMessageId"], unique = true)]
)
data class TelegramReplyMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val telegramMessageId: Int,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)
