package com.saebm.smsntfy.aether

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy

@RunWith(AndroidJUnit4::class)
class TelegramAetherProbeInstrumentedTest {
    @Test
    fun proxyUsesUnresolvedSocksAddressForAndroidLoopback() {
        val address = TelegramAetherProxy.socketAddress(1819)

        assertTrue(address.isUnresolved)
        assertTrue(address.hostString == "127.0.0.1")
        assertTrue(address.port == 1819)
        assertTrue(TelegramAetherProxy.proxy(1819).type() == Proxy.Type.SOCKS)
    }

    @Test
    fun verifiesTelegramGetMeThroughRunningAetherWhenTokenProvided() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val token = arguments.getString("telegramBotToken").orEmpty()
        if (token.isBlank()) return@runBlocking

        assertTrue(TelegramAetherProbe().verifyTelegram(1819, token))
    }
}
