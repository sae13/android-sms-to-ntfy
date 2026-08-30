package com.smsntfy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Claim/outcome ledger for Telegram updates. Insertion is the dedupe barrier. */
@Entity(tableName = "telegram_updates")
data class TelegramUpdate(
    @PrimaryKey val updateId: Long,
    val claimedAt: Long,
    val outcome: String = "claimed",
    val completedAt: Long? = null
)
