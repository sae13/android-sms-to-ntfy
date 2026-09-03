package com.saebm.smsntfy.service

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
import com.saebm.smsntfy.R
import com.saebm.smsntfy.SmsNtfyApplication
import com.saebm.smsntfy.data.EventLog
import com.saebm.smsntfy.deltachat.DeltaChatDestinationPolicy
import com.saebm.smsntfy.deltachat.DeltaChatMessageFormatter
import com.saebm.smsntfy.network.NtfyClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.saebm.smsntfy.receiver.CallReceiver
import com.saebm.smsntfy.sms.ContactHelper
import com.saebm.smsntfy.telegram.TelegramBotClient
import com.saebm.smsntfy.telegram.TelegramSendResult
import com.saebm.smsntfy.aether.AetherSessionManager
import com.saebm.smsntfy.aether.AetherSessionPolicy
import com.saebm.smsntfy.util.WakeLockHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Main foreground service for SMS/Call forwarding.
 * - Receives SMS via BroadcastReceiver and forwards to ntfy
 * - Monitors call state changes and forwards to ntfy
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

        // Extras
        const val EXTRA_SMS_SENDERS = "sms_senders"
        const val EXTRA_SMS_BODIES = "sms_bodies"
        const val EXTRA_SMS_TIMESTAMPS = "sms_timestamps"
        const val EXTRA_CALL_NUMBER = "call_number"
        const val EXTRA_CALL_STATE = "call_state"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ntfyClient: NtfyClient? = null
    private var telegramClient: TelegramBotClient? = null
    private var aetherManager: AetherSessionManager? = null
    private var database: com.saebm.smsntfy.data.AppDatabase? = null
    private var prefs: com.saebm.smsntfy.data.Preferences? = null
    private var callReceiver: CallReceiver? = null
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

        if (action in setOf(ACTION_PROCESS_SMS, ACTION_PROCESS_CALL)) {
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
        }

        return if (action == ACTION_START_SERVICE) START_STICKY else START_NOT_STICKY
    }

    private fun ensureDependenciesInitialized(app: SmsNtfyApplication) {
        if (prefs != null && ntfyClient != null && telegramClient != null && database != null && aetherManager != null) return
        ntfyClient = app.ntfyClient
        telegramClient = app.telegramBotClient
        aetherManager = app.aetherSessionManager
        database = app.database
        prefs = app.preferences
        callReceiver = CallReceiver()
        startCallListener()
    }

    private fun startServiceInternal() {
        Log.d(TAG, "Starting foreground service")
        prefs?.isServiceRunning = true

        val currentPrefs = prefs
        if (currentPrefs?.telegramEnabled == true && AetherSessionPolicy.shouldKeepAlive(
                currentPrefs.aetherEnabled,
                currentPrefs.aetherAlwaysOn
            )
        ) {
            ioScope.launch {
                runCatching {
                    aetherManager?.startPersistent(
                        currentPrefs.telegramBotToken,
                        currentPrefs.aetherPublicProxy
                    )
                }
                    .onFailure { Log.e(TAG, "Persistent Aether startup failed") }
            }
        } else {
            ioScope.launch { aetherManager?.stopPersistent() }
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

        stopCallListener()
        ioScope.launch {
            try {
                withTimeoutOrNull(AetherSessionManager.STOP_TIMEOUT_MS + 500L) {
                    aetherManager?.shutdown()
                }
            } finally {
                stopForeground(true)
                stopSelf()
            }
        }
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

        val currentPrefs = prefs ?: return
        if (DeltaChatDestinationPolicy.isReady(currentPrefs.deltaChatEnabled, currentPrefs.deltaChatChatId)) {
            val text = DeltaChatMessageFormatter.sms(
                sender = sender,
                contact = contact,
                message = body,
                timestamp = formatTimestamp(timestamp)
            )
            val success = sendDeltaChat(currentPrefs.deltaChatChatId, text)
            if (success) {
                logEvent("sms", "SMS forwarded to Delta Chat", "Message queued", sender, contact)
            } else {
                logEvent("error", "Failed to forward SMS to Delta Chat", "Delta Chat rejected the message", success = false)
            }
        }

        // Telegram remains isolated from every other destination.
        if (currentPrefs.telegramEnabled) {
            var session: AetherSessionManager.Session? = null
            try {
                // 1. Always attempt without proxy first
                val directClient = telegramClient
                var result = directClient?.sendSmsMessage(sender, contact, body, timestamp)

                // 2. If it failed and Aether is enabled, attempt with the latest working proxy/discover a new one
                if (result is TelegramSendResult.Failed && currentPrefs.aetherEnabled) {
                    session = aetherManager?.acquire(
                        currentPrefs.telegramBotToken,
                        keepAlive = false,
                        publicProxy = currentPrefs.aetherPublicProxy
                    )
                    
                    if (session != null) {
                        val proxyClient = TelegramBotClient(
                            { com.saebm.smsntfy.telegram.TelegramConfig(true, currentPrefs.telegramBotToken, currentPrefs.telegramChatId) },
                            { session.port }
                        )
                        result = proxyClient.sendSmsMessage(sender, contact, body, timestamp)
                    }
                }

                when (result) {
                    is TelegramSendResult.Sent -> logEvent("sms", "SMS forwarded to Telegram", "Message sent", sender, contact)
                    is TelegramSendResult.Failed -> logEvent("error", "Failed to forward SMS to Telegram", result.reason, sender, contact, false)
                    null -> logEvent("error", "Failed to forward SMS to Telegram", "Telegram client unavailable", sender, contact, false)
                }
            } catch (_: java.util.concurrent.CancellationException) {
                throw _
            } catch (error: Exception) {
                Log.e(TAG, "Telegram forwarding failed", error)
                logEvent("error", "Failed to forward SMS to Telegram", "Telegram or Aether unavailable", sender, contact, false)
            } finally {
                session?.close()
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
                if (prefs?.enableCallNotifications != false) {
                    ntfyClient?.sendCallNotification(number, contact, state)
                        ?.also { success ->
                            if (success) {
                                logEvent("call", "Call notification sent", stateText, number, contact)
                            } else {
                                logEvent("error", "Failed to send call notification", stateText, number, contact, false)
                            }
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
        val intent = Intent(this, com.saebm.smsntfy.ui.MainActivity::class.java)
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
        stopCallListener()
        runBlocking {
            withTimeoutOrNull(AetherSessionManager.STOP_TIMEOUT_MS + 500L) {
                aetherManager?.shutdown()
            }
        }
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
}
