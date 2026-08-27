package com.smsntfy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.smsntfy.service.SmsForwardingService

/**
 * BroadcastReceiver for MY_PACKAGE_REPLACED.
 * Restarts the service after app update.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageReplacedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Package replaced: ${intent.action}")

        val prefs = (context.applicationContext as com.smsntfy.SmsNtfyApplication).preferences
        if (!prefs.isServiceRunning) {
            Log.d(TAG, "Service was not running, not restarting after update")
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

        Log.d(TAG, "Foreground service restarted after app update")
    }
}