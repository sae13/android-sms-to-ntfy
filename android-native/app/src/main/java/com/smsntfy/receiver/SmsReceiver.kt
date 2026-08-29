package com.smsntfy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.smsntfy.service.SmsForwardingService
import com.smsntfy.util.WakeLockHelper

/**
 * BroadcastReceiver for incoming SMS messages.
 * Priority 999 ensures we receive SMS before other apps.
 * Starts the foreground service to process the SMS.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val SMS_ACTION = "android.provider.Telephony.SMS_RECEIVED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SMS received, action: ${intent.action}")

        // Only process if service is enabled
        val prefs = (context.applicationContext as com.smsntfy.SmsNtfyApplication).preferences
        if (!prefs.enableSms) {
            Log.d(TAG, "SMS forwarding disabled in settings")
            return
        }

        // Acquire WakeLock to prevent Doze mode from killing processing
        WakeLockHelper.acquireWakeLock(context, 60_000)

        try {
            // Parse SMS messages from intent
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) {
                Log.w(TAG, "No SMS messages found in intent")
                WakeLockHelper.releaseWakeLock()
                return
            }

            val senders = Array(messages.size) { index -> messages[index].originatingAddress.orEmpty() }
            val bodies = Array(messages.size) { index -> messages[index].messageBody.orEmpty() }
            val timestamps = LongArray(messages.size) { index -> messages[index].timestampMillis }

            // Start the service to handle SMS processing
            val serviceIntent = Intent(context, SmsForwardingService::class.java).apply {
                action = SmsForwardingService.ACTION_PROCESS_SMS
                putExtra(SmsForwardingService.EXTRA_SMS_SENDERS, senders)
                putExtra(SmsForwardingService.EXTRA_SMS_BODIES, bodies)
                putExtra(SmsForwardingService.EXTRA_SMS_TIMESTAMPS, timestamps)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (error: Exception) {
            WakeLockHelper.releaseWakeLock()
            Log.e(TAG, "Failed to hand SMS to forwarding service", error)
        } catch (error: LinkageError) {
            WakeLockHelper.releaseWakeLock()
            Log.e(TAG, "Failed to hand SMS to forwarding service", error)
        }

        // Don't abort broadcast - let other apps (like default SMS app) also receive it
        // abortBroadcast() // DO NOT use this - it prevents default SMS app from receiving
    }
}