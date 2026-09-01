package com.saebm.smsntfy.aether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AetherAbiPolicyTest {
    @Test
    fun selectsOnlyPackagedAndroidAbis() {
        assertEquals("arm64-v8a", AetherBinaryManager.selectAbi(listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("armeabi-v7a", AetherBinaryManager.selectAbi(listOf("armeabi-v7a")))
        assertNull(AetherBinaryManager.selectAbi(listOf("x86_64", "x86")))
    }

    @Test
    fun mapsSupportedAbisToPackagedExecutableNames() {
        assertEquals("libaether.so", AetherBinaryManager.libraryName("arm64-v8a"))
        assertEquals("libaether.so", AetherBinaryManager.libraryName("armeabi-v7a"))
        assertNull(AetherBinaryManager.libraryName("x86_64"))
        assertNull(AetherBinaryManager.libraryName("x86"))
    }
}
