package com.smsntfy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Represents an event log entry (SMS received, call received, SSE message, SMS sent, errors).
 */
@Entity(tableName = "event_logs")
data class EventLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val type: String,           // "sms", "call", "sse", "sent", "error"
    val title: String,          // Short title
    val message: String,        // Full message body
    val sender: String = "",    // Phone number or sender
    val contact: String = "",   // Contact name if available
    val timestamp: Date = Date(),
    val success: Boolean = true // Whether the operation succeeded
)
