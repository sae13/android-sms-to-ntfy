package com.saebm.smsntfy.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class PinnedDnsTest {
    @Test
    fun defaultHostResolvesOnlyToPinnedAddressWithoutSystemDns() {
        val dns = PinnedDns(
            pinnedHost = "ntfy.fc5.ir",
            pinnedAddress = "178.131.137.111",
            fallback = { throw AssertionError("System DNS must not be used for pinned host") }
        )

        assertEquals(
            listOf(InetAddress.getByAddress(byteArrayOf(178.toByte(), 131.toByte(), 137.toByte(), 111.toByte()))),
            dns.lookup("NTFY.FC5.IR")
        )
    }

    @Test
    fun defaultServerConfigurationIsPinned() {
        assertEquals("https://ntfy.fc5.ir", NtfyEndpointDefaults.serverUrl)
        assertEquals("ntfy.fc5.ir", NtfyEndpointDefaults.host)
        assertEquals("178.131.137.111", NtfyEndpointDefaults.address)
    }

    @Test
    fun otherHostsUseFallbackResolver() {
        val fallbackAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val dns = PinnedDns("ntfy.fc5.ir", "178.131.137.111") { listOf(fallbackAddress) }

        assertEquals(listOf(fallbackAddress), dns.lookup("example.com"))
    }
}
