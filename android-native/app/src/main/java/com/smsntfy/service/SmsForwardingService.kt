package com.smsntfy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsntfy.R
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.EventLog
import com.smsntfy.network.NtfyClient
import com.smsntfy.network.SseClient
import com.smsntfy.receiver.CallReceiver
import com.smsntfy.sms.ContactHelper
import com.smsntfy.sms.SmsReplyHelper
import com.smsntfy.util.WakeLockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Main foreground service for SMS/Call forwarding.
 * - Receives SMS via BroadcastReceiver and forwards to ntfy
 * - Monitors call state changes and forwards to ntfy
 * - Maintains SSE connection for remote SMS replies
 * - Runs as foreground service with dataSync/specialUse type
 */
class SmsForwardingService : Service() {

    companion object {
        private const val TAG = "SmsForwardingService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sms_forwarding_service"

        // Actions
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_PROCESS_SMS = "PROCESS_SMS"
        const val ACTION_PROCESS_CALL = "PROCESS_CALL"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_SEND_REPLY = "SEND_REPLY"

        // Extras
        const val EXTRA_SMS_MESSAGES = "sms_messages"
        const val EXTRA_CALL_NUMBER = "call_number"
        const val EXTRA_CALL_STATE = "call_state"
        const val EXTRA_REPLY_NUMBER = "reply_number"
        const val EXTRA_REPLY_MESSAGE = "reply_message"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var ntfyClient: NtfyClient? = null
    private var sseClient: SseClient? = null
    private var sseJob: Job? = null
    private var database: com.smsntfy.data.AppDatabase? = null
    private var prefs: com.smsntfy.data.Preferences? = null
    private var callReceiver: CallReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        val app = application as SmsNtfyApplication
        ntfyClient = app.ntfyClient
        sseClient = app.sseClient
        database = app.database
        prefs = app.preferences
        callReceiver = CallReceiver()

        createNotificationChannel()
        startCallListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        Log.d(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_START_SERVICE -> startServiceInternal()
            ACTION_PROCESS_SMS -> processSmsIntent(intent!!)
            ACTION_PROCESS_CALL -> processCallIntent(intent!!)
            ACTION_STOP_SERVICE -> stopServiceInternal()
            ACTION_SEND_REPLY -> sendSmsReplyIntent(intent!!)
        }

        return START_STICKY
    }

    private fun startServiceInternal() {
        Log.d(TAG, "Starting foreground service")
        prefs?.isServiceRunning = true

        // Create and show notification
        val notification = createNotification(getString(R.string.notification_text_connecting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start SSE connection if enabled
        if (prefs?.enableSse == true) {
            startSseConnection()
        }

        // Update notification to show connected
        updateNotification(getString(R.string.notification_text))

        Log.d(TAG, "Foreground service started successfully")
    }

    private fun stopServiceInternal() {
        Log.d(TAG, "Stopping foreground service")
        prefs?.isServiceRunning = false

        stopSseConnection()
        stopCallListener()
        stopForeground(true)
        stopSelf()
    }

    private fun processSmsIntent(intent: Intent) {
        val messages = intent.getParcelableArrayListExtra<SmsMessage>(EXTRA_SMS_MESSAGES)
        if (messages == null || messages.isEmpty()) {
            Log.w(TAG, "No SMS messages to process")
            return
        }

        ioScope.launch {
            for (message in messages) {
                processSmsMessage(message)
            }
        }
    }

    private fun processSmsMessage(message: SmsMessage) {
        val sender = message.originatingAddress ?: ""
        val body = message.messageBody ?: ""
        val timestamp = message.timestampMillis

        if (sender.isEmpty() || body.isEmpty()) {
            Log.w(TAG, "Empty sender or body, skipping")
            return
        }

        Log.d(TAG, "Processing SMS from $sender: ${body.take(50)}")

        // Get contact name
        val contact = ContactHelper.getContactName(this, sender)

        // Store last sender for reply
        prefs?.lastSender = sender
        prefs?.lastContact = contact

        // Log event
        logEvent("sms", "SMS from $contact", body, sender, contact)

        // Forward to ntfy
        ntfyClient?.sendSmsNotification(sender, contact, body, timestamp)
            ?.also { success ->
                if (success) {
                    logEvent("sms", "SMS forwarded to ntfy", "From: $contact ($sender)", sender, contact)
                } else {
                    logEvent("error", "Failed to forward SMS", "From: $contact ($sender)", sender, contact, false)
                }
            }
    }

    private fun processCallIntent(intent: Intent) {
        val number = intent.getStringExtra(EXTRA_CALL_NUMBER) ?: ""
        val state = intent.getStringExtra(EXTRA_CALL_STATE) ?: ""

        if (number.isEmpty()) return

        ioScope.launch {
            val contact = ContactHelper.getContactName(this@SmsForwardingService, number)

            // Log event
            val stateText = when (state) {
                "ringing" -> "Incoming call"
                "answered" -> "Call answered"
                "missed" -> "Missed call"
                else -> "Call event"
            }
            logEvent("call", "$stateText from $contact", stateText, number, contact)

            // Forward to ntfy
            ntfyClient?.sendCallNotification(number, contact, state)
                ?.also { success ->
                    if (success) {
                        logEvent("call", "Call notification sent", stateText, number, contact)
                    } else {
                        logEvent("error", "Failed to send call notification", stateText, number, contact, false)
                    }
                }
        }
    }

    private fun sendSmsReplyIntent(intent: Intent) {
        val number = intent.getStringExtra(EXTRA_REPLY_NUMBER) ?: prefs?.lastSender ?: ""
        val message = intent.getStringExtra(EXTRA_REPLY_MESSAGE) ?: ""

        if (number.isEmpty() || message.isEmpty()) {
            Log.w(TAG, "Cannot send reply: empty number or message")
            return
        }

        ioScope.launch {
            val success = SmsReplyHelper.sendSmsReply(this@SmsForwardingService, number, message)

            if (success) {
                val contact = ContactHelper.getContactName(this@SmsForwardingService, number)
                logEvent("sent", "Reply sent to $contact", message, number, contact)
            } else {
                logEvent("error", "Failed to send reply", message, number, "", false)
            }
        }
    }

    private fun startSseConnection() {
        Log.d(TAG, "Starting SSE connection")
        sseClient?.start()

        sseJob = scope.launch {
            sseClient?.messages?.collect { sseMessage ->
                handleSseMessage(sseMessage)
            }
        }
    }

    private fun stopSseConnection() {
        Log.d(TAG, "Stopping SSE connection")
        sseClient?.stop()
        sseJob?.cancel()
        sseJob = null
    }

    private fun handleSseMessage(sseMessage: SseClient.SseMessage) {
        Log.d(TAG, "Received SSE message: ${sseMessage.data.take(100)}")

        // Parse the ntfy message
        val parsed = sseClient?.parseNtfyMessage(sseMessage.data)
        if (parsed == null) return

        logEvent("sse", "SSE message received", parsed.message, "", "")

        // Check if this is a reply message (contains "reply" tag or specific format)
        // Expected format: "REPLY:+1234567890:Your message here"
        // Or check if message starts with "REPLY:"
        if (parsed.message.startsWith("REPLY:") || parsed.tags.contains("reply")) {
            processReplyMessage(parsed.message)
        }
    }

    private fun processReplyMessage(message: String) {
        // Expected format: "REPLY:+1234567890:Your message here"
        // Or JSON format from ntfy
        val parts = message.split(":", limit = 3)
        if (parts.size >= 3 && parts[0] == "REPLY") {
            val number = parts[1]
            val replyText = parts[2]

            Log.d(TAG, "Processing reply to $number: $replyText")

            // Send the reply SMS
            val intent = Intent(this, SmsForwardingService::class.java).apply {
                action = ACTION_SEND_REPLY
                putExtra(EXTRA_REPLY_NUMBER, number)
                putExtra(EXTRA_REPLY_MESSAGE, replyText)
            }
            startService(intent)
        }
    }

    private fun startCallListener() {
        // CallReceiver handles its own listener registration
    }

    private fun stopCallListener() {
        callReceiver?.stopListening()
        callReceiver = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, com.smsntfy.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun logEvent(
        type: String,
        title: String,
        message: String,
        sender: String = "",
        contact: String = "",
        success: Boolean = true
    ) {
        ioScope.launch {
            val event = EventLog(
                type = type,
                title = title,
                message = message,
                sender = sender,
                contact = contact,
                success = success
            )
            database?.eventLogDao()?.insert(event)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopSseConnection()
        stopCallListener()
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
}