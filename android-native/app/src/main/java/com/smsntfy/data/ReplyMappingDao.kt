package com.smsntfy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ReplyMappingDao {
    @Insert
    suspend fun insert(mapping: ReplyMapping): Long

    @Query("SELECT * FROM reply_mappings WHERE replyId = :replyId ORDER BY id DESC LIMIT 1")
    suspend fun findNewest(replyId: Int): ReplyMapping?

    @Query("SELECT nextReplyId FROM reply_sequence WHERE singleton = 1")
    suspend fun nextReplyId(): Int

    @Query("UPDATE reply_sequence SET nextReplyId = (nextReplyId + 1) % 1000 WHERE singleton = 1")
    suspend fun advanceReplyId()

    @Transaction
    suspend fun allocateAndInsert(phoneNumber: String, receivedAt: Long): ReplyMapping {
        val replyId = nextReplyId()
        advanceReplyId()
        val mapping = ReplyMapping(replyId = replyId, phoneNumber = phoneNumber, receivedAt = receivedAt)
        return mapping.copy(id = insert(mapping))
    }
}
