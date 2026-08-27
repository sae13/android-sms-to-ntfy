package com.smsntfy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.smsntfy.service.SmsForwardingService

/**
 * BroadcastReceiver for phone call state changes.
 * Monitors: RINGING, OFFHOOK (answered), IDLE (missed/ended).
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        const val PHONE_STATE_ACTION = "android.intent.action.PHONE_STATE"
        private var listener: PhoneStateListener? = null
        private var telephonyManager: TelephonyManager? = null
        private var contextRef: Context? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Phone state changed: ${intent.action}")

        val prefs = (context.applicationContext as com.smsntfy.SmsNtfyApplication).preferences
        if (!prefs.enableCalls) {
            Log.d(TAG, "Call forwarding disabled in settings")
            return
        }

        contextRef = context.applicationContext

        // Get telephony manager and start listening if not already
        if (telephonyManager == null) {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }

        if (listener == null) {
            listener = object : PhoneStateListener() {
                private var lastState = TelephonyManager.CALL_STATE_IDLE
                private var incomingNumber = ""

                override fun onCallStateChanged(state: Int, phoneNumber: String) {
                    super.onCallStateChanged(state, phoneNumber)

                    val prefs = (contextRef?.applicationContext as? com.smsntfy.SmsNtfyApplication)?.preferences
                        ?: return

                    if (!prefs.enableCalls) return

                    Log.d(TAG, "Call state: $state, number: $phoneNumber, last: $lastState")

                    when {
                        state == TelephonyManager.CALL_STATE_RINGING -> {
                            // Incoming call
                            incomingNumber = phoneNumber
                            val serviceIntent = Intent(contextRef!!, SmsForwardingService::class.java).apply {
                                action = SmsForwardingService.ACTION_PROCESS_CALL
                                putExtra(SmsForwardingService.EXTRA_CALL_NUMBER, phoneNumber)
                                putExtra(SmsForwardingService.EXTRA_CALL_STATE, "ringing")
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                contextRef!!.startForegroundService(serviceIntent)
                            } else {
                                contextRef!!.startService(serviceIntent)
                            }
                        }
                        state == TelephonyManager.CALL_STATE_OFFHOOK -> {
                            // Call answered (outgoing or incoming)
                            if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                                // Incoming call was answered
                                val serviceIntent = Intent(contextRef!!, SmsForwardingService::class.java).apply {
                                    action = SmsForwardingService.ACTION_PROCESS_CALL
                                    putExtra(SmsForwardingService.EXTRA_CALL_NUMBER, incomingNumber)
                                    putExtra(SmsForwardingService.EXTRA_CALL_STATE, "answered")
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    contextRef!!.startForegroundService(serviceIntent)
                                } else {
                                    contextRef!!.startService(serviceIntent)
                                }
                            }
                        }
                        state == TelephonyManager.CALL_STATE_IDLE && lastState == TelephonyManager.CALL_STATE_RINGING -> {
                            // Missed call (was ringing, now idle without being answered)
                            val serviceIntent = Intent(contextRef!!, SmsForwardingService::class.java).apply {
                                action = SmsForwardingService.ACTION_PROCESS_CALL
                                putExtra(SmsForwardingService.EXTRA_CALL_NUMBER, incomingNumber)
                                putExtra(SmsForwardingService.EXTRA_CALL_STATE, "missed")
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                contextRef!!.startForegroundService(serviceIntent)
                            } else {
                                contextRef!!.startService(serviceIntent)
                            }
                        }
                    }

                    lastState = state
                }
            }

            // Listen for call state changes
            telephonyManager?.listen(listener!!, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    /**
     * Call this to stop listening (e.g., when service stops).
     */
    fun stopListening() {
        listener?.let { l ->
            telephonyManager?.listen(l, PhoneStateListener.LISTEN_NONE)
            listener = null
        }
        telephonyManager = null
        contextRef = null
    }
}