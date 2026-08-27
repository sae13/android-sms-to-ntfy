package com.smsntfy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.smsntfy.service.SmsForwardingService

/**
 * BroadcastReceiver for BOOT_COMPLETED.
 * Automatically starts the foreground service on device boot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot completed: ${intent.action}")

        // Check if service was running before boot
        val prefs = (context.applicationContext as com.smsntfy.SmsNtfyApplication).preferences
        if (!prefs.isServiceRunning) {
            Log.d(TAG, "Service was not running before boot, not auto-starting")
            return
        }

        val serviceIntent = Intent(context, SmsForwardingService::class.java).apply {
            action = SmsForwardingService.ACTION_START_SERVICE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        Log.d(TAG, "Foreground service started on boot")
    }
}