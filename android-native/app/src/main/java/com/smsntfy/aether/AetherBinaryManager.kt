package com.smsntfy.aether

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException

/** Resolves the packaged Aether executable from Android's native library directory. */
class AetherBinaryManager(private val context: Context) {
    fun prepare(): File {
        val abi = selectAbi(Build.SUPPORTED_ABIS?.toList().orEmpty())
            ?: throw IOException("No supported Aether ABI")
        val name = libraryName(abi) ?: throw IOException("No packaged Aether binary for ABI: $abi")
        val binary = File(context.applicationInfo.nativeLibraryDir, name)
        if (!binary.isFile || binary.length() == 0L || !binary.canExecute()) {
            throw IOException("Aether executable is missing for ABI: $abi")
        }
        return binary
    }

    companion object {
        private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a")

        fun selectAbi(abis: List<String>): String? = abis.firstOrNull { it in supportedAbis }

        fun libraryName(abi: String): String? = if (abi in supportedAbis) "libaether.so" else null
    }
}
