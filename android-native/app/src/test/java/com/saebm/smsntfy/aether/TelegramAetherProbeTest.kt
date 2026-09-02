package com.saebm.smsntfy.aether

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class TelegramAetherProbeTest {
    @Test
    fun runsBlockingSocksRequestOffCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ioDispatcher ->
            var requestThread: Thread? = null
            var requestPort: Int? = null
            var requestToken: String? = null
            val probe = TelegramAetherProbe(
                ioDispatcher = ioDispatcher,
                socksRequest = { port, token ->
                    requestThread = Thread.currentThread()
                    requestPort = port
                    requestToken = token
                    true
                }
            )

            assertTrue(probe.verifyTelegram(1819, "redacted-test-token"))
            assertNotSame(callerThread, requestThread)
            assertEquals(1819, requestPort)
            assertEquals("redacted-test-token", requestToken)
        }
    }
}
