package com.smsntfy.deltachat

object DeltaChatDestinationPolicy {
    fun isReady(enabled: Boolean, chatId: Int): Boolean = enabled && chatId > 0
}
