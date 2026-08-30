package com.smsntfy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsntfy.R
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.EventLog
import com.smsntfy.deltachat.DeltaChatDestinationPolicy
import com.smsntfy.deltachat.DeltaChatMessageFormatter
import com.smsntfy.network.NtfyClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.smsntfy.network.NtfyEventParser
import com.smsntfy.network.NtfyParseResult
import com.smsntfy.network.SseClient
import com.smsntfy.receiver.CallReceiver
import com.smsntfy.sms.ContactHelper
import com.smsntfy.sms.SmsReplyHelper
import com.smsntfy.util.WakeLockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        const val EXTRA_SMS_SENDERS = "sms_senders"
        const val EXTRA_SMS_BODIES = "sms_bodies"
        const val EXTRA_SMS_TIMESTAMPS = "sms_timestamps"
        const val EXTRA_CALL_NUMBER = "call_number"
        const val EXTRA_CALL_STATE = "call_state"
        const val EXTRA_REPLY_NUMBER = "reply_number"
        const val EXTRA_REPLY_MESSAGE = "reply_message"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ntfyClient: NtfyClient? = null
    private var sseClient: SseClient? = null
    private var sseJob: Job? = null
    private var database: com.smsntfy.data.AppDatabase? = null
    private var prefs: com.smsntfy.data.Preferences? = null
    private var callReceiver: CallReceiver? = null
    private var staleClaimsChecked = false
    private val oneShotStarts = OneShotStartTracker()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as SmsNtfyApplication
        val action = intent?.action ?: ServiceStartPolicy.actionForRestart(
            isPersistent = app.preferences.isServiceRunning
        )
        if (action == null) {
            Log.w(TAG, "Ignoring service restart without an active persistent service")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        Log.d(TAG, "onStartCommand: action=$action")

        if (!ServiceStartPolicy.isKnown(action)) {
            Log.w(TAG, "Unknown service action: $action")
            if (
                ServiceStartPolicy.shouldStopRejectedStart(
                    app.preferences.isServiceRunning,
                    oneShotStarts.hasActiveStarts()
                )
            ) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        if (ServiceStartPolicy.requiresImmediateForeground(action)) {
            try {
                ensureForegroundStarted()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to promote forwarding service", error)
                stopSelf(startId)
                return START_NOT_STICKY
            } catch (error: LinkageError) {
                Log.e(TAG, "Failed to promote forwarding service", error)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        if (action in setOf(ACTION_PROCESS_SMS, ACTION_PROCESS_CALL, ACTION_SEND_REPLY)) {
            oneShotStarts.register(startId)
        }

        ServiceStartPolicy.persistedRunningStateBeforeDependencies(action)?.let { isRunning ->
            app.preferences.isServiceRunning = isRunning
        }

        if (ServiceStartPolicy.requiresDependencies(action)) {
            try {
                ensureDependenciesInitialized(app)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to initialize forwarding service", error)
                oneShotStarts.abandon(startId)
                stopSelf(startId)
                return START_NOT_STICKY
            } catch (error: LinkageError) {
                Log.e(TAG, "Failed to initialize forwarding service", error)
                oneShotStarts.abandon(startId)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        when (action) {
            ACTION_START_SERVICE -> startServiceInternal()
            ACTION_PROCESS_SMS -> processSmsIntent(intent!!, startId)
            ACTION_PROCESS_CALL -> processCallIntent(intent!!, startId)
            ACTION_STOP_SERVICE -> stopServiceInternal()
            ACTION_SEND_REPLY -> sendSmsReplyIntent(intent!!, startId)
        }

        return if (action == ACTION_START_SERVICE) START_STICKY else START_NOT_STICKY
    }

    private fun ensureDependenciesInitialized(app: SmsNtfyApplication) {
        if (prefs != null && ntfyClient != null && sseClient != null && database != null) return

        ntfyClient = app.ntfyClient
        sseClient = app.sseClient
        database = app.database
        prefs = app.preferences
        callReceiver = CallReceiver()
        startCallListener()
        if (!staleClaimsChecked) {
            staleClaimsChecked = true
            val startupCutoff = System.currentTimeMillis()
            ioScope.launch {
                val count = app.database.ntfyCommandDao().finalizeStaleClaims(startupCutoff, System.currentTimeMillis())
                if (count > 0) {
                    Log.e(TAG, "$count stale reply claim(s) finalized as failed; at-most-once policy prevents retry")
                    logEvent(
                        "error",
                        "Interrupted replies finalized as failed",
                        "$count claimed command(s) were interrupted before completion and will not be retried",
                        success = false
                    )
                }
            }
        }
    }

    private fun startServiceInternal() {
        Log.d(TAG, "Starting foreground service")
        prefs?.isServiceRunning = true

        // Start SSE connection if enabled
        if (prefs?.enableSse == true) {
            startSseConnection()
        }

        // Update notification to show connected
        updateNotification(getString(R.string.notification_text))

        Log.d(TAG, "Foreground service started successfully")
    }

    private fun ensureForegroundStarted() {
        val notification = createNotification(getString(R.string.notification_text_connecting))
        if (ServiceStartPolicy.supportsTypedForeground(Build.VERSION.SDK_INT)) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceStartPolicy.foregroundServiceTypes(Build.VERSION.SDK_INT)
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopServiceInternal() {
        Log.d(TAG, "Stopping foreground service")
        prefs?.isServiceRunning = false

        stopSseConnection()
        stopCallListener()
        stopForeground(true)
        stopSelf()
    }

    private fun processSmsIntent(intent: Intent, startId: Int) {
        val messages = SmsPayload.fromParts(
            senders = intent.getStringArrayExtra(EXTRA_SMS_SENDERS),
            bodies = intent.getStringArrayExtra(EXTRA_SMS_BODIES),
            timestamps = intent.getLongArrayExtra(EXTRA_SMS_TIMESTAMPS)
        )
        if (messages.isEmpty()) {
            Log.w(TAG, "No SMS messages to process")
            WakeLockHelper.releaseWakeLock()
            stopOneShotIfNotPersistent(startId)
            return
        }

        ioScope.launch {
            try {
                for (message in messages) {
                    try {
                        processSmsMessage(message)
                    } catch (error: Exception) {
                        Log.e(TAG, "Failed while processing SMS from ${message.sender}", error)
                    } catch (error: LinkageError) {
                        Log.e(TAG, "Failed while processing SMS from ${message.sender}", error)
                    }
                }
            } finally {
                WakeLockHelper.releaseWakeLock()
                stopOneShotIfNotPersistent(startId)
            }
        }
    }

    private suspend fun processSmsMessage(message: SmsPayload) {
        val sender = message.sender
        val body = message.body
        val timestamp = message.timestamp

        if (sender.isBlank() || body.isEmpty()) {
            Log.w(TAG, "Empty sender or body, skipping")
            return
        }

        Log.d(TAG, "Processing SMS from $sender: ${body.take(50)}")

        // Get contact name
        val contact = ContactHelper.getContactName(this, sender)

        val mapping = database?.replyMappingDao()?.allocateAndInsert(sender, timestamp) ?: return
        val replyId = mapping.replyId

        // Log event
        logEvent("sms", "SMS from $contact", body, sender, contact)

        // Forward to ntfy
        ntfyClient?.sendSmsNotification(sender, contact, body, ReplyPolicy.formatId(replyId), timestamp)
            ?.also { success ->
                if (success) {
                    logEvent("sms", "SMS forwarded to ntfy", "From: $contact ($sender)", sender, contact)
                } else {
                    logEvent("error", "Failed to forward SMS", "From: $contact ($sender)", sender, contact, false)
                }
            }

        val currentPrefs = prefs ?: return
        if (DeltaChatDestinationPolicy.isReady(currentPrefs.deltaChatEnabled, currentPrefs.deltaChatChatId)) {
            val text = DeltaChatMessageFormatter.sms(
                sender = sender,
                contact = contact,
                message = body,
                replyId = ReplyPolicy.formatId(replyId),
                timestamp = formatTimestamp(timestamp)
            )
            val success = sendDeltaChat(currentPrefs.deltaChatChatId, text)
            if (success) {
                logEvent("sms", "SMS forwarded to Delta Chat", "Message queued", sender, contact)
            } else {
                logEvent("error", "Failed to forward SMS to Delta Chat", "Delta Chat rejected the message", success = false)
            }
        }
    }

    private fun processCallIntent(intent: Intent, startId: Int) {
        val number = intent.getStringExtra(EXTRA_CALL_NUMBER) ?: ""
        val state = intent.getStringExtra(EXTRA_CALL_STATE) ?: ""

        if (number.isEmpty()) {
            stopOneShotIfNotPersistent(startId)
            return
        }

        ioScope.launch {
            try {
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

                val currentPrefs = prefs
                if (currentPrefs != null && DeltaChatDestinationPolicy.isReady(
                        currentPrefs.deltaChatEnabled,
                        currentPrefs.deltaChatChatId
                    )
                ) {
                    val text = DeltaChatMessageFormatter.call(
                        callerNumber = number,
                        callerName = contact,
                        callState = stateText,
                        timestamp = formatTimestamp(System.currentTimeMillis())
                    )
                    if (sendDeltaChat(currentPrefs.deltaChatChatId, text)) {
                        logEvent("call", "Call forwarded to Delta Chat", "Message queued", number, contact)
                    } else {
                        logEvent("error", "Failed to forward call to Delta Chat", "Delta Chat rejected the message", success = false)
                    }
                }
            } finally {
                stopOneShotIfNotPersistent(startId)
            }
        }
    }

    private fun stopOneShotIfNotPersistent(startId: Int) {
        val isPersistent = (application as SmsNtfyApplication).preferences.isServiceRunning
        val startIdToStop = oneShotStarts.complete(startId, isPersistent) ?: return
        if (stopSelfResult(startIdToStop)) {
            stopForeground(true)
        }
    }

    private fun sendSmsReplyIntent(intent: Intent, startId: Int) {
        val number = intent.getStringExtra(EXTRA_REPLY_NUMBER) ?: prefs?.lastSender ?: ""
        val message = intent.getStringExtra(EXTRA_REPLY_MESSAGE) ?: ""

        if (number.isEmpty() || message.isEmpty()) {
            Log.w(TAG, "Cannot send reply: empty number or message")
            stopAfterReplyIfNotPersistent(startId)
            return
        }

        ioScope.launch {
            try {
                val success = SmsReplyHelper.sendSmsReply(this@SmsForwardingService, number, message)

                if (success) {
                    val contact = ContactHelper.getContactName(this@SmsForwardingService, number)
                    logEvent("sent", "Reply sent to $contact", message, number, contact)
                } else {
                    logEvent("error", "Failed to send reply", message, number, "", false)
                }
            } finally {
                stopAfterReplyIfNotPersistent(startId)
            }
        }
    }

    private fun stopAfterReplyIfNotPersistent(startId: Int) {
        val isPersistent = (application as SmsNtfyApplication).preferences.isServiceRunning
        val startIdToStop = oneShotStarts.complete(startId, isPersistent) ?: return
        if (stopSelfResult(startIdToStop)) {
            stopForeground(true)
        }
    }

    private fun startSseConnection() {
        Log.d(TAG, "Starting SSE connection")
        sseClient?.start()

        sseJob = scope.launch {
            sseClient?.messages?.collect { sseMessage ->
                try {
                    handleSseMessage(sseMessage)
                } catch (error: Exception) {
                    Log.e(TAG, "Unhandled reply event failure; continuing SSE collection", error)
                    logEvent("error", "Reply event processing failed", error.message ?: error.javaClass.simpleName, success = false)
                } catch (error: LinkageError) {
                    Log.e(TAG, "Unhandled reply event linkage failure; continuing SSE collection", error)
                    logEvent("error", "Reply event processing failed", error.message ?: error.javaClass.simpleName, success = false)
                }
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
        when (val parsed = NtfyEventParser.parseResult(sseMessage.data)) {
            is NtfyParseResult.Malformed -> {
                Log.e(TAG, "Malformed SSE JSON: ${parsed.reason}")
                logEvent("error", "Malformed SSE JSON", parsed.reason, success = false)
            }
            is NtfyParseResult.Success -> {
                if (parsed.data.event != "message") return
                logEvent("sse", "SSE message received", parsed.data.message, "", "")
                processReplyMessage(parsed.data.id.ifBlank { sseMessage.id }, parsed.data.message)
            }
        }
    }

    private fun processReplyMessage(eventId: String, message: String) {
        val route = ReplyRouting.route(eventId, message)
        if (route !is ReplyRoute.Command) {
            val title = if (route == ReplyRoute.InvalidEventId) "Missing ntfy event id" else "Invalid reply command"
            Log.e(TAG, "$title; reply rejected")
            logEvent("error", title, message, success = false)
            return
        }
        ioScope.launch {
            var claimedEventId: String? = null
            try {
                val db = database ?: throw IllegalStateException("Database unavailable")
                if (!db.ntfyCommandDao().claim(route.eventId, System.currentTimeMillis())) {
                    Log.w(TAG, "Duplicate ntfy command ignored: ${route.eventId}")
                    return@launch
                }
                claimedEventId = route.eventId
                val mapping = db.replyMappingDao().findNewest(route.command.id)
                if (mapping == null) {
                    db.ntfyCommandDao().complete(route.eventId, "invalid", System.currentTimeMillis())
                    logEvent("error", "Unknown reply id", ReplyPolicy.formatId(route.command.id), success = false)
                    return@launch
                }
                val success = SmsReplyHelper.sendSmsReply(
                    this@SmsForwardingService, mapping.phoneNumber, route.command.message
                )
                val outcome = if (success) "sent" else "failed"
                db.ntfyCommandDao().complete(route.eventId, outcome, System.currentTimeMillis())
                val contact = ContactHelper.getContactName(this@SmsForwardingService, mapping.phoneNumber)
                if (success) {
                    logEvent("sent", "Reply sent to $contact", route.command.message, mapping.phoneNumber, contact)
                } else {
                    logEvent("error", "Failed to send reply; event will not be retried", route.command.message, mapping.phoneNumber, contact, false)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Reply handling failed; event will not be retried if already claimed", error)
                finalizeClaimFailure(claimedEventId, error)
                logEvent("error", "Reply handling failed", error.message ?: error.javaClass.simpleName, success = false)
            } catch (error: LinkageError) {
                Log.e(TAG, "Reply handling linkage failure", error)
                finalizeClaimFailure(claimedEventId, error)
                logEvent("error", "Reply handling failed", error.message ?: error.javaClass.simpleName, success = false)
            }
        }
    }

    private suspend fun finalizeClaimFailure(eventId: String?, error: Throwable) {
        if (eventId == null) return
        try {
            val updated = database?.ntfyCommandDao()?.complete(eventId, "failed", System.currentTimeMillis()) ?: 0
            if (updated == 0) Log.e(TAG, "Claim $eventId was not finalized after failure", error)
            else Log.e(TAG, "Claim $eventId durably finalized as failed; at-most-once policy prevents retry", error)
        } catch (persistenceError: Exception) {
            Log.e(TAG, "CRITICAL: failed to durably finalize claim $eventId", persistenceError)
        }
    }

    private suspend fun sendDeltaChat(chatId: Int, text: String): Boolean {
        return try {
            (application as SmsNtfyApplication).deltaChatClient.sendText(chatId, text)
        } catch (error: Exception) {
            Log.e(TAG, "Delta Chat send failed", error)
            false
        } catch (error: LinkageError) {
            Log.e(TAG, "Delta Chat core unavailable", error)
            false
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestamp))
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
            try {
                val event = EventLog(
                    type = type,
                    title = title,
                    message = message,
                    sender = sender,
                    contact = contact,
                    success = success
                )
                database?.eventLogDao()?.insert(event)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to persist event log", error)
            }
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