package com.smsntfy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Singleton state used to persist Telegram getUpdates offset across restarts. */
@Entity(tableName = "telegram_state")
data class TelegramState(
    @PrimaryKey val singleton: Int = 1,
    val nextOffset: Long = 0
)
