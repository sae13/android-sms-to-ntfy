package com.smsntfy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ntfy_commands")
data class NtfyCommand(
    @PrimaryKey val eventId: String,
    val claimedAt: Long,
    val outcome: String = "claimed",
    val completedAt: Long? = null
)