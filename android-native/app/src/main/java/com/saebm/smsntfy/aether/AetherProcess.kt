package com.saebm.smsntfy.aether

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

interface AetherProcess {
    suspend fun start(route: AetherRoute, endpoints: AetherEndpoints)
    suspend fun awaitListening(port: Int, timeoutMs: Long): Boolean
    suspend fun stop(timeoutMs: Long)
    fun isAlive(): Boolean
}

data class AetherRoute(
    val id: String,
    val protocol: String,
    val extraArguments: List<String> = emptyList(),
    val scanMode: String = "balanced",
    val noise: String = "firewall"
)

object AetherRoutes {
    val supported = listOf(
        AetherRoute("masque-h3", "masque", scanMode = "balanced", noise = "firewall"),
        AetherRoute(
            "masque-h2",
            "masque",
            scanMode = "balanced",
            noise = "firewall",
            extraArguments = listOf("--h2", "--fragment")
        ),
        AetherRoute(
            "wireguard",
            "wg",
            scanMode = "turbo",
            noise = "balanced",
            extraArguments = listOf("--no-data-check")
        ),
        AetherRoute(
            "gool",
            "gool",
            scanMode = "balanced",
            noise = "balanced",
            extraArguments = listOf("--no-data-check")
        )
    )
}

object AetherRouteOrdering {
    fun preferCached(routes: List<AetherRoute>, cached: String): List<AetherRoute> {
        val cachedId = routes.firstOrNull { it.id == cached }?.id
            ?: routes.firstOrNull { it.protocol == cached }?.id
            ?: return routes
        return routes.sortedBy { if (it.id == cachedId) 0 else 1 }
    }
}

internal object AetherProcessCompatibility {
    fun isAlive(exitValue: () -> Int): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    fun waitForExit(
        timeoutMs: Long,
        isAlive: () -> Boolean,
        nowMillis: () -> Long = System::currentTimeMillis,
        sleep: (Long) -> Unit = Thread::sleep
    ): Boolean {
        val deadline = nowMillis() + timeoutMs
        while (isAlive()) {
            val remaining = deadline - nowMillis()
            if (remaining <= 0L) return false
            sleep(minOf(remaining, 50L))
        }
        return true
    }
}

object AetherArguments {
    fun build(
        binary: String,
        route: AetherRoute,
        endpoints: AetherEndpoints,
        stateDirectory: String
    ): List<String> = buildList {
            add(binary)
            add("--bind"); add(endpoints.socksAddress)
            add("--http-proxy"); add(endpoints.httpAddress)
            add("--protocol"); add(route.protocol)
            add("--scan"); add(route.scanMode)
            add("--noize"); add(route.noise)
            add("-4")
            // v1.9.0 auto-tunes flow-control windows from device cpu/ram;
            // overriding with --perf low would force the old 64 KiB h2 window.
            add("--startup-secs"); add("30")
            add("--validate-secs"); add("10")
            add("--reconnect-secs"); add("2")
            add("--quick-reconnect")
            add("--log-level"); add("debug")
        add("--config"); add(File(stateDirectory, "aether.toml").absolutePath)
        add("--wg-config"); add(File(stateDirectory, "aether-wg.toml").absolutePath)
        add("--masque-config"); add(File(stateDirectory, "aether-masque.toml").absolutePath)
        addAll(route.extraArguments)
    }
}

class AndroidAetherProcess(
    context: Context,
    private val binaryManager: AetherBinaryManager = AetherBinaryManager(context)
) : AetherProcess {
    private companion object {
        const val TAG = "SmsNtfyAether"
    }
    private val stateDirectory = File(context.noBackupFilesDir, "aether")
    @Volatile private var process: Process? = null

    override suspend fun start(route: AetherRoute, endpoints: AetherEndpoints) = withContext(Dispatchers.IO) {
        check(!isAlive()) { "Aether is already running" }
        check(stateDirectory.isDirectory || stateDirectory.mkdirs()) {
            "Cannot create Aether state directory"
        }
        val binary = binaryManager.prepare()
        val outputFile = File(stateDirectory, "aether-${route.id}.log")
        val arguments = AetherArguments.build(
            binary.absolutePath,
            route,
            endpoints,
            stateDirectory.absolutePath
        )
        Log.i(TAG, "Starting ${route.id}: ${arguments.drop(1).joinToString(" ")}")
        val started = ProcessBuilder(arguments)
            .directory(stateDirectory)
            .redirectErrorStream(true)
            .start()
        process = started
        Thread({
            runCatching {
                started.inputStream.use { input ->
                    outputFile.outputStream().buffered().use(input::copyTo)
                }
            }.onFailure { error ->
                Log.w(TAG, "Aether output capture stopped: ${error.javaClass.simpleName}")
            }
        }, "sms-ntfy-aether-log").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "Started ${route.id}")
        Unit
    }

    override suspend fun awaitListening(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && isAlive()) {
            val connected = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
                }.isSuccess
            }
            if (connected) return true
            delay(100)
        }
        return false
    }

    override suspend fun stop(timeoutMs: Long) = withContext(Dispatchers.IO) {
        val current = process ?: return@withContext
        current.destroy()
        check(AetherProcessCompatibility.waitForExit(timeoutMs, { isProcessAlive(current) })) {
            "Aether process did not stop"
        }
        process = null
    }

    override fun isAlive(): Boolean = process?.let(::isProcessAlive) == true

    private fun isProcessAlive(candidate: Process): Boolean =
        AetherProcessCompatibility.isAlive(candidate::exitValue)
}
