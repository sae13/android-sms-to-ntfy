package com.smsntfy.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smsntfy.BuildConfig
import com.smsntfy.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ReleaseUpdateChecker(
    private val context: Context,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val state = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkAndNotify() = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "sms-ntfy-android/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val release = GitHubReleaseParser.parse(
                    json = response.body?.string().orEmpty(),
                    commitVersionCode = { targetCommitVersionCode(it) },
                    preferredAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
                )
                val update = ReleaseUpdatePolicy.availableUpdate(currentVersionCode, release)
                    ?: return@use
                val lastNotified = state.getInt(KEY_LAST_NOTIFIED_VERSION, 0)
                if (!ReleaseUpdatePolicy.shouldNotify(update.versionCode, lastNotified)) return@use
                copyDownloadUrl(update.downloadUrl)
                ReleaseUpdatePolicy.notifiedVersionToPersist(
                    update.versionCode,
                    notificationPosted = notifyUpdate(update)
                )?.let { notifiedVersion ->
                    state.edit().putInt(KEY_LAST_NOTIFIED_VERSION, notifiedVersion).apply()
                }
            }
        }.onFailure { Log.w(TAG, "Release update check failed", it) }
    }

    private fun copyDownloadUrl(url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.update_download_url_label), url)
        )
    }

    private fun targetCommitVersionCode(targetCommit: String): Int? {
        if (!targetCommit.matches(Regex("[0-9a-fA-F]{7,40}"))) return null
        val request = Request.Builder()
            .url("https://api.github.com/repos/sae13/android-sms-to-ntfy/actions/runs?head_sha=$targetCommit&per_page=10")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "sms-ntfy-android/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            GitHubActionsParser.androidNativeRunNumber(response.body?.string().orEmpty())
        }
    }

    private fun notifyUpdate(update: AvailableUpdate): Boolean {
        createChannel()
        val notificationManager = NotificationManagerCompat.from(context)
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val channelEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .getNotificationChannel(CHANNEL_ID)
                    ?.importance != NotificationManager.IMPORTANCE_NONE
        if (
            !NotificationDeliveryPolicy.canPost(
                permissionGranted = permissionGranted,
                notificationsEnabled = notificationManager.areNotificationsEnabled(),
                channelEnabled = channelEnabled
            )
        ) return false

        val copyIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = UpdateActionReceiver.ACTION_COPY_DOWNLOAD_URL
            putExtra(UpdateActionReceiver.EXTRA_DOWNLOAD_URL, update.downloadUrl)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            update.versionCode,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val releasePageIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.downloadUrl))
        val releasePagePendingIntent = PendingIntent.getActivity(
            context,
            update.versionCode,
            releasePageIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(context.getString(R.string.update_available_message, update.versionName))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.update_available_details, update.versionName)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(releasePagePendingIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.copy_download_url), copyPendingIntent)
            .build()
        return runCatching {
            notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
            true
        }.getOrElse { error ->
            Log.w(TAG, "Unable to post update notification", error)
            false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.update_channel_description) }
        )
    }

    companion object {
        private const val TAG = "ReleaseUpdateChecker"
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/sae13/android-sms-to-ntfy/releases/latest"
        private const val PREFS_NAME = "release_update_state"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
        private const val CHANNEL_ID = "app_updates"
        private const val UPDATE_NOTIFICATION_ID = 2001
    }
}