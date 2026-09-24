package com.saebm.smsntfy.aether

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AetherProcessInstrumentedTest {
    @Test
    fun launchesPinnedBinaryAndStopsCleanly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = AndroidAetherProcess(context)
        val endpoints = AetherEndpoints(
            socksBind = "127.0.0.1",
            httpBind = "127.0.0.1",
            socksPort = 28119,
            httpPort = 28120
        )

        process.start(AetherRoutes.supported.first(), endpoints)
        try {
            assertTrue("Aether exited immediately after launch", process.isAlive())
            assertTrue(
                "Aether did not open its HTTP proxy before the timeout",
                process.awaitListening(endpoints.httpPort, 150_000L)
            )
            assertTrue("Aether exited after opening its HTTP proxy", process.isAlive())
        } finally {
            if (process.isAlive()) {
                process.stop(5_000)
            }
        }

        assertFalse(process.isAlive())
    }
}
