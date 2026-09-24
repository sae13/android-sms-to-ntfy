package com.saebm.smsntfy.deltachat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.b44t.messenger.DcAccounts
import com.b44t.messenger.DcEventChannel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeDeltaChatSmokeInstrumentedTest {
    @Test
    fun loadsCurrentNativeCoreCreatesAccountManagerAndCrossesRpcAndEventBoundaries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountDirectory = File(context.cacheDir, "deltachat-native-smoke").apply {
            deleteRecursively()
            assertTrue(mkdirs())
        }
        val channel = createNativeWrapper(
            loadLibrary = { System.loadLibrary("native-utils") },
            constructWrapper = { DcEventChannel() }
        )
        val accounts = DcAccounts(accountDirectory.absolutePath, channel)
        val rpc = accounts.jsonrpcInstance
        val emitter = accounts.eventEmitter

        try {
            // Use the native JSON-RPC wrapper directly. BaseRpcTransport owns a
            // permanent response thread, which is inappropriate for a bounded smoke test.
            rpc.request("""{"jsonrpc":"2.0","method":"add_account","params":[],"id":1}""")
            val response = JSONObject(rpc.nextResponse)
            assertEquals(1, response.getInt("id"))
            assertTrue(response.getInt("result") > 0)

            // add_account synchronously queues AccountsChanged, so this native call
            // cannot race an event-producing worker and needs no leaked waiter thread.
            var sawAccountsChanged = false
            for (ignored in 0 until 16) {
                val event = requireNotNull(emitter.nextEvent) {
                    "add_account event stream ended before AccountsChanged"
                }
                try {
                    if (event.id == 2302) {
                        sawAccountsChanged = true
                        break
                    }
                } finally {
                    event.unref()
                }
            }
            assertTrue("add_account did not emit AccountsChanged", sawAccountsChanged)
        } finally {
            emitter.unref()
            rpc.unref()
            accounts.stopIo()
            accounts.unref()
            accountDirectory.deleteRecursively()
        }
    }
}
