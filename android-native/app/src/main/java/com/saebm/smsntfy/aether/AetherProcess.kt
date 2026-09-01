package com.saebm.smsntfy.aether

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

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
            add("--perf"); add("low")
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
        check(process?.isAlive != true) { "Aether is already running" }
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
        process = ProcessBuilder(arguments)
            .directory(stateDirectory)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(outputFile))
            .start()
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
        if (!current.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            current.destroyForcibly()
            current.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
        check(!current.isAlive) { "Aether process did not stop" }
        process = null
    }

    override fun isAlive(): Boolean = process?.isAlive == true
}
