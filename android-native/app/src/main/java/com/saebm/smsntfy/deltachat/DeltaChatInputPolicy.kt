package com.saebm.smsntfy.deltachat

import java.util.Locale

object DeltaChatInputPolicy {
    fun isLoginCode(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT).startsWith("dclogin:")

    fun isInvite(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.ROOT)
        return normalized.startsWith("openpgp4fpr:") ||
            normalized.startsWith("https://i.delta.chat/#") ||
            normalized.startsWith("http://i.delta.chat/#")
    }
}
