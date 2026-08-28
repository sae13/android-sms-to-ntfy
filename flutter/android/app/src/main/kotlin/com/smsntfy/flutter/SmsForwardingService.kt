package com.smsntfy.flutter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsForwardingService : Service() {
    companion object { const val START="start"; const val STOP="stop"; const val FORWARD="forward"; const val SENDER="sender"; const val BODY="body"; private const val CHANNEL="sms_forwarder" }
    override fun onCreate() { super.onCreate(); (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(CHANNEL, "SMS forwarding", NotificationManager.IMPORTANCE_LOW)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("SMS → ntfy").setContentText("Forwarding is active").setOngoing(true).build()
        startForeground(1001, notification)
        when (intent?.action) {
            STOP -> { getSharedPreferences(Prefs.NAME, MODE_PRIVATE).edit().putBoolean(Prefs.RUNNING, false).apply(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            FORWARD -> {
                val sender = intent.getStringExtra(SENDER).orEmpty(); val body = intent.getStringExtra(BODY).orEmpty()
                Thread { if (sender.isNotBlank() && body.isNotBlank()) NtfyPublisher(this).publish("SMS <$sender>", body, listOf("sms", "inbox"), "sms:$sender"); stopSelfResult(startId) }.start()
            }
            else -> getSharedPreferences(Prefs.NAME, MODE_PRIVATE).edit().putBoolean(Prefs.RUNNING, true).apply()
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
