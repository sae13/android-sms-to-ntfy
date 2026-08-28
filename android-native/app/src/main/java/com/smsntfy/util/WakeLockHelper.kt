package com.smsntfy.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Helper class for managing WakeLocks to keep the device awake during critical operations.
 */
object WakeLockHelper {

    private const val TAG = "WakeLockHelper"
    private const val WAKE_LOCK_TAG = "SMS-Ntfy::WakeLock"

    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquireWakeLock(context: Context, timeoutMs: Long = 60_000L) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock?.isHeld == true) return

            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    @Synchronized
    fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    fun isDeviceIdle(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isDeviceIdleMode || powerManager.isPowerSaveMode
        } catch (e: Exception) {
            false
        }
    }

    fun isBatteryOptimizationEnabled(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            // Using a simpler approach to avoid potential API issues during compilation
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            !isIgnoring
        } catch (e: Exception) {
            true
        }
    }
}