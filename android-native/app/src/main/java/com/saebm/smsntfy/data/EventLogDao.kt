package com.saebm.smsntfy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for event log entries.
 */
@Dao
interface EventLogDao {

    @Insert
    suspend fun insert(eventLog: EventLog): Long

    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<EventLog>>

    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<EventLog>

    @Query("DELETE FROM event_logs WHERE id NOT IN (SELECT id FROM event_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldLogs(keepCount: Int = 500)

    @Query("DELETE FROM event_logs")
    suspend fun clearAll()
}
