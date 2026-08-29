package com.smsntfy.update

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.smsntfy.R

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_DOWNLOAD_URL) return
        val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
            ?.takeIf(ReleaseUrlPolicy::isTrusted)
            ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.update_download_url_label), url))
        Toast.makeText(context, R.string.update_url_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_COPY_DOWNLOAD_URL = "com.smsntfy.action.COPY_UPDATE_DOWNLOAD_URL"
        const val EXTRA_DOWNLOAD_URL = "download_url"
    }
}