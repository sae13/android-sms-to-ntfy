package com.saebm.smsntfy.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

object NtfyEndpointDefaults {
    const val serverUrl = "https://ntfy.fc5.ir"
    const val host = "ntfy.fc5.ir"
    const val address = "178.131.137.111"
}

/** Resolves one configured host to a fixed address without consulting system DNS. */
class PinnedDns(
    private val pinnedHost: String,
    pinnedAddress: String,
    private val fallback: (String) -> List<InetAddress> = Dns.SYSTEM::lookup
) : Dns {
    private val addressBytes = parseIpv4(pinnedAddress)

    override fun lookup(hostname: String): List<InetAddress> =
        if (hostname.equals(pinnedHost, ignoreCase = true)) {
            listOf(InetAddress.getByAddress(addressBytes))
        } else {
            fallback(hostname)
        }

    companion object {
        private fun parseIpv4(value: String): ByteArray {
            val parts = value.split('.')
            if (parts.size != 4) throw UnknownHostException("Invalid pinned IPv4 address")
            return ByteArray(4) { index ->
                val octet = parts[index].toIntOrNull()
                    ?: throw UnknownHostException("Invalid pinned IPv4 address")
                if (octet !in 0..255) throw UnknownHostException("Invalid pinned IPv4 address")
                octet.toByte()
            }
        }
    }
}
