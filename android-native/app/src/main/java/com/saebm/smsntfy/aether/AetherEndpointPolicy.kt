package com.saebm.smsntfy.aether

data class AetherEndpoints(
    val socksBind: String,
    val httpBind: String,
    val socksPort: Int = 1819,
    val httpPort: Int = 1820
) {
    val socksAddress: String get() = "$socksBind:$socksPort"
    val httpAddress: String get() = "$httpBind:$httpPort"
}

object AetherEndpointPolicy {
    fun endpoints(publicProxy: Boolean): AetherEndpoints {
        val bind = if (publicProxy) "0.0.0.0" else "127.0.0.1"
        return AetherEndpoints(socksBind = bind, httpBind = bind)
    }
}
