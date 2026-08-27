package com.smsntfy.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Helper class for managing WakeLocks to keep the device awake during critical operations.
 * Partial WakeLock is used to avoid draining battery while still preventing Doze mode from
 * interrupting SMS forwarding and SSE connection.
 */
object WakeLockHelper {

    private const val TAG = "WakeLockHelper"
    private const val WAKE_LOCK_TAG = "SMS-Ntfy::WakeLock"

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Acquires a partial WakeLock with a timeout.
     * This prevents the CPU from sleeping during critical operations.
     */
    @Synchronized
    fun acquireWakeLock(context: Context, timeoutMs: Long = 60_000L) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // Check if we already hold a wake lock
            if (wakeLock?.isHeld == true) {
                Log.d(TAG, "WakeLock already held")
                return
            }

            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }

            Log.d(TAG, "WakeLock acquired for ${timeoutMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    /**
     * Releases the WakeLock if held.
     */
    @Synchronized
    fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    /**
     * Checks if the device is in Doze mode or idle.
     */
    fun isDeviceIdle(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isDeviceIdleMode || powerManager.isPowerSaveMode
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if battery optimization is enabled for the app.
     */
    fun isBatteryOptimizationEnabled(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            true
        }
    }
}