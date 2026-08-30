package com.smsntfy.network

/** Pure lifecycle rule for distinguishing unexpected closes from explicit stops. */
object SseReconnectPolicy {
    fun shouldReconnect(stopped: Boolean, callbackIsCurrent: Boolean): Boolean =
        !stopped && callbackIsCurrent
}
