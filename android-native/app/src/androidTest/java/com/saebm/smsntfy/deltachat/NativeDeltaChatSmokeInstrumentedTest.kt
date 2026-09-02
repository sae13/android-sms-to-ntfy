package com.saebm.smsntfy.deltachat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeDeltaChatSmokeInstrumentedTest {
    @Test
    fun loadsCurrentNativeCoreAndCreatesAccountManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataDirectory = File(context.cacheDir, "deltachat-native-smoke").apply {
            deleteRecursively()
            assertTrue(mkdirs())
        }

        val eventChannel = createNativeWrapper(
            loadLibrary = { System.loadLibrary("native-utils") },
            constructWrapper = { com.b44t.messenger.DcEventChannel() }
        )
        val accounts = com.b44t.messenger.DcAccounts(dataDirectory.absolutePath, eventChannel)

        try {
            assertTrue(accounts.jsonrpcInstance != null)
        } finally {
            accounts.stopIo()
            accounts.unref()
            dataDirectory.deleteRecursively()
        }
    }
}
