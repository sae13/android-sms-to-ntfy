package com.saebm.smsntfy.aether

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
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
    fun discoversAetherRouteThatReachesTelegramWithConfiguredToken() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = com.saebm.smsntfy.data.Preferences(context)
        val token = preferences.telegramBotToken
        org.junit.Assume.assumeTrue("Telegram bot token is not configured", token.isNotBlank())

        val manager = AetherSessionManager(context, preferences)
        try {
            val route = manager.findFastestRoute(token)
            assertTrue(route.id.isNotBlank())
            assertTrue(preferences.aetherLastRoute == route.id)
            assertTrue(preferences.aetherLastStatus == "verified:${route.id}")
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun verifiesTelegramGetMeThroughRunningAetherWhenTokenProvided() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val token = arguments.getString("telegramBotToken").orEmpty()
        org.junit.Assume.assumeTrue("Telegram bot token was not provided", token.isNotBlank())

        assertTrue(TelegramAetherProbe().verifyTelegram(1819, token))
    }
}
