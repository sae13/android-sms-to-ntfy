package com.saebm.smsntfy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

/**
 * Represents an event log entry for received and forwarded events or errors.
 */
@Entity(tableName = "event_logs")
@TypeConverters(Converters::class)
data class EventLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val type: String,           // "sms", "call", "sent", "error"
    val title: String,          // Short title
    val message: String,        // Full message body
    val sender: String = "",    // Phone number or sender
    val contact: String = "",   // Contact name if available
    val timestamp: Date = Date(),
    val success: Boolean = true // Whether the operation succeeded
)
