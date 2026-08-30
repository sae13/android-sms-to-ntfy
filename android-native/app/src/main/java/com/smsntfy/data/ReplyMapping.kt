package com.smsntfy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reply_mappings",
    indices = [Index(value = ["replyId", "id"])]
)
data class ReplyMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val replyId: Int,
    val phoneNumber: String,
    val receivedAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)
