package com.smsntfy.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helper for guiding users to disable battery optimizations per manufacturer.
 * Different Android OEMs have different settings locations for battery optimization.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimizationHelper"

    /**
     * Opens the appropriate battery optimization settings for the device manufacturer.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Log.d(TAG, "Opening battery settings for manufacturer: $manufacturer")

        val intent = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                // Xiaomi/Redmi/POCO
                Intent().apply {
                    setAction("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
                    setPackage("com.miui.securitycenter")
                }
            }
            manufacturer.contains("samsung") -> {
                // Samsung
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                // Huawei/Honor
                Intent().apply {
                    setAction("com.huawei.systemmanager.optimize.process.ProtectActivity")
                    setPackage("com.huawei.systemmanager")
                }
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                // OPPO/Realme/OnePlus
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                // Vivo/iQOO
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("motorola") -> {
                // Motorola
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("nokia") -> {
                // Nokia
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("sony") -> {
                // Sony
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("asus") -> {
                // ASUS
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            manufacturer.contains("google") || manufacturer.contains("pixel") -> {
                // Google Pixel
                Intent().apply {
                    setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            else -> {
                // Generic Android - use standard intent
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open manufacturer-specific settings, falling back to generic", e)
            // Fallback to generic
            val fallback = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    /**
     * Checks if battery optimization is disabled for this app.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Gets the manufacturer-specific instructions for disabling battery optimization.
     */
    fun getManufacturerInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> """
                Xiaomi/Redmi/POCO:
                1. Settings → Battery → App battery saver
                2. Find SMS-to-Ntfy → Set to 'No restrictions'
                3. Settings → Apps → Permissions → Autostart → Enable for SMS-to-Ntfy
                4. Security app → Battery → App battery saver → SMS-to-Ntfy → No restrictions
            """.trimIndent()
            manufacturer.contains("samsung") -> """
                Samsung:
                1. Settings → Battery → Background usage limits → Never sleeping apps
                2. Add SMS-to-Ntfy
                3. Settings → Apps → SMS-to-Ntfy → Battery → Unrestricted
            """.trimIndent()
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> """
                Huawei/Honor:
                1. Settings → Battery → App launch
                2. Find SMS-to-Ntfy → Manage manually → Enable all (Auto-launch, Secondary launch, Run in background)
                3. Phone Manager → Protected apps → Add SMS-to-Ntfy
            """.trimIndent()
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> """
                OPPO/Realme/OnePlus:
                1. Settings → Battery → Battery optimization → All apps → SMS-to-Ntfy → Don't optimize
                2. Settings → Apps → SMS-to-Ntfy → Battery → Allow background activity
            """.trimIndent()
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> """
                Vivo/iQOO:
                1. iManager → Battery → High power consumption in background → SMS-to-Ntfy → Allow
                2. Settings → Apps → SMS-to-Ntfy → Battery → Allow background running
            """.trimIndent()
            else -> """
                General Android:
                1. Settings → Battery → Battery optimization
                2. Select 'All apps' → Find SMS-to-Ntfy → Don't optimize
                3. Or: Settings → Apps → SMS-to-Ntfy → Battery → Unrestricted
            """.trimIndent()
        }
    }
}