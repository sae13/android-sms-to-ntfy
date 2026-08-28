package com.smsntfy.flutter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ENABLED, true)) return
        val pending = goAsync()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val parts = messages.groupBy { it.originatingAddress.orEmpty() }
        parts.forEach { (sender, segments) ->
            val body = segments.joinToString("") { it.messageBody.orEmpty() }
            val service = Intent(context, SmsForwardingService::class.java).apply {
                action = SmsForwardingService.FORWARD
                putExtra(SmsForwardingService.SENDER, sender)
                putExtra(SmsForwardingService.BODY, body)
            }
            ContextCompat.startForegroundService(context, service)
        }
        pending.finish()
    }
}
