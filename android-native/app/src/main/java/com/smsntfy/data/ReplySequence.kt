package com.smsntfy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reply_sequence")
data class ReplySequence(
    @PrimaryKey val singleton: Int = 1,
    val nextReplyId: Int = 0
)