package com.saebm.smsntfy.deltachat

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeDeltaChatInitializationTest {
    @Test
    fun `loads native library before constructing JNI wrapper`() {
        val events = mutableListOf<String>()

        createNativeWrapper(
            loadLibrary = { events += "library" },
            constructWrapper = { events += "wrapper"; Any() }
        )

        assertEquals(listOf("library", "wrapper"), events)
    }
}
