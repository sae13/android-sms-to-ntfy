package com.smsntfy.aether

import android.content.Context
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
    val extraArguments: List<String> = emptyList()
)

object AetherRoutes {
    val supported = listOf(
        AetherRoute("masque-h3", "masque"),
        AetherRoute("masque-h2", "masque", listOf("--h2", "--fragment")),
        AetherRoute("wireguard", "wg"),
        AetherRoute("gool", "gool")
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
            add("--scan"); add("turbo")
            add("--perf"); add("low")
            add("--startup-secs"); add("25")
            add("--validate-secs"); add("10")
            add("--quick-reconnect")
            add("--log-level"); add("warn")
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
    private val stateDirectory = File(context.noBackupFilesDir, "aether")
    @Volatile private var process: Process? = null

    override suspend fun start(route: AetherRoute, endpoints: AetherEndpoints) = withContext(Dispatchers.IO) {
        check(process?.isAlive != true) { "Aether is already running" }
        check(stateDirectory.isDirectory || stateDirectory.mkdirs()) {
            "Cannot create Aether state directory"
        }
        val binary = binaryManager.prepare()
        process = ProcessBuilder(
            AetherArguments.build(binary.absolutePath, route, endpoints, stateDirectory.absolutePath)
        ).directory(stateDirectory)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
            .start()
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
