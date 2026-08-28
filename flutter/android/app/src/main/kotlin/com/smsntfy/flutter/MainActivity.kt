package com.smsntfy.flutter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.smsntfy.flutter/service"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestPermissions" -> {
                    val permissions = buildList {
                        add(Manifest.permission.RECEIVE_SMS)
                        add(Manifest.permission.READ_SMS)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
                    if (permissions.isNotEmpty()) ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 42)
                    result.success(permissions.isEmpty())
                }
                "saveSettings" -> {
                    val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                    getSharedPreferences(Prefs.NAME, MODE_PRIVATE).edit().apply {
                        putString(Prefs.SERVER, args["server"] as? String ?: "https://ntfy.sh")
                        putString(Prefs.TOPIC, args["topic"] as? String ?: "sms-alerts")
                        putString(Prefs.REPLY_TOPIC, args["replyTopic"] as? String ?: "sms-replies")
                        putString(Prefs.USERNAME, args["username"] as? String ?: "")
                        putString(Prefs.PASSWORD, args["password"] as? String ?: "")
                        putInt(Prefs.PRIORITY, (args["priority"] as? Number)?.toInt() ?: 4)
                        putBoolean(Prefs.ENABLED, args["enabled"] as? Boolean ?: true)
                    }.apply()
                    result.success(null)
                }
                "getSettings" -> {
                    val p = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                    result.success(mapOf("server" to p.getString(Prefs.SERVER, "https://ntfy.sh"), "topic" to p.getString(Prefs.TOPIC, "sms-alerts"), "replyTopic" to p.getString(Prefs.REPLY_TOPIC, "sms-replies"), "username" to p.getString(Prefs.USERNAME, ""), "password" to p.getString(Prefs.PASSWORD, ""), "priority" to p.getInt(Prefs.PRIORITY, 4), "running" to p.getBoolean(Prefs.RUNNING, false)))
                }
                "startService" -> {
                    val intent = Intent(this, SmsForwardingService::class.java).setAction(SmsForwardingService.START)
                    ContextCompat.startForegroundService(this, intent); result.success(true)
                }
                "stopService" -> { startService(Intent(this, SmsForwardingService::class.java).setAction(SmsForwardingService.STOP)); result.success(true) }
                "sendTest" -> Thread {
                    val success = NtfyPublisher(this).publish("SMS-to-ntfy Test", "Test notification from Flutter", listOf("test"))
                    runOnUiThread { result.success(success) }
                }.start()
                else -> result.notImplemented()
            }
        }
    }
}

object Prefs {
    const val NAME = "sms_ntfy"
    const val SERVER = "server"; const val TOPIC = "topic"; const val REPLY_TOPIC = "reply_topic"
    const val USERNAME = "username"; const val PASSWORD = "password"; const val PRIORITY = "priority"
    const val ENABLED = "enabled"; const val RUNNING = "running"
}
