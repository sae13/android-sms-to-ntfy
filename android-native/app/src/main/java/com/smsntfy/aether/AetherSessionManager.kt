package com.smsntfy.aether

import android.content.Context
import com.smsntfy.data.Preferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

interface AetherProbe {
    /** Must complete a real Telegram Bot API response through the candidate SOCKS proxy. */
    suspend fun verifyTelegram(port: Int, botToken: String): Boolean
}

class TelegramAetherProbe : AetherProbe {
    override suspend fun verifyTelegram(port: Int, botToken: String): Boolean = runCatching {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/getMe")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful && response.body?.string()?.contains("\"ok\":true") == true
        }
    }.getOrDefault(false)
}

class AetherUnavailableException : Exception("Aether could not establish a verified Telegram route")

/** Owns one process and reference-counted temporary/permanent sessions. */
class AetherSessionManager(
    context: Context,
    private val preferences: Preferences,
    private val process: AetherProcess = AndroidAetherProcess(context),
    private val probe: AetherProbe = TelegramAetherProbe(),
    private val port: Int = 1819,
    private val routes: List<AetherRoute> = AetherRoutes.supported
) {
    private val mutex = Mutex()
    private var users = 0
    private var persistent = false
    private var activeRoute: AetherRoute? = null
    private var activeEndpoints: AetherEndpoints? = null

    suspend fun acquire(
        botToken: String,
        keepAlive: Boolean = false,
        publicProxy: Boolean = false
    ): Session = mutex.withLock {
        val endpoints = AetherEndpointPolicy.endpoints(publicProxy)
        if (AetherSessionPolicy.canReuse(
                processAlive = process.isAlive(),
                hasActiveRoute = activeRoute != null,
                endpointsMatch = activeEndpoints == endpoints
            )
        ) {
            if (!probe.verifyTelegram(port, botToken)) {
                if (users > 0) throw AetherUnavailableException()
                stopLocked()
            } else {
                users++
                persistent = persistent || keepAlive
                return@withLock Session(this, port)
            }
        }
        if (users > 0) throw IllegalStateException("Aether listener mode cannot change during an active send")
        if (process.isAlive()) stopLocked()
        val cached = preferences.aetherLastRoute
        val ordered = AetherRouteOrdering.preferCached(routes, cached)
        val selected = AetherRouteSelector(process).select(
            routes = ordered,
            endpoints = endpoints,
            port = port,
            startTimeoutMs = START_TIMEOUT_MS,
            stopTimeoutMs = STOP_TIMEOUT_MS
        ) { _, candidatePort ->
            probe.verifyTelegram(candidatePort, botToken)
        }
        if (selected != null) {
            users = 1
            persistent = keepAlive
            activeRoute = selected
            activeEndpoints = endpoints
            preferences.aetherLastRoute = selected.id
            preferences.aetherLastStatus = "verified:${selected.id}"
            return@withLock Session(this, port)
        }
        preferences.aetherLastStatus = "unavailable"
        throw AetherUnavailableException()
    }

    suspend fun findFastestRoute(
        botToken: String,
        publicProxy: Boolean = false,
        onAttempt: (AetherRoute, AetherRouteAttemptStage) -> Unit = { _, _ -> }
    ): AetherRoute = mutex.withLock {
        check(users == 0) { "Aether route search cannot start during an active send" }
        if (process.isAlive()) stopLocked()
        val endpoints = AetherEndpointPolicy.endpoints(publicProxy)
        val selected = withTimeout(DISCOVERY_TIMEOUT_MS) {
            AetherRouteSelector(process).select(
                routes = routes,
                endpoints = endpoints,
                port = port,
                startTimeoutMs = START_TIMEOUT_MS,
                stopTimeoutMs = STOP_TIMEOUT_MS,
                onAttempt = onAttempt
            ) { _, candidatePort ->
                probe.verifyTelegram(candidatePort, botToken)
            }
        }
        if (selected != null) {
            stopLocked()
            preferences.aetherLastRoute = selected.id
            preferences.aetherLastStatus = "verified:${selected.id}"
            return@withLock selected
        }
        preferences.aetherLastStatus = "unavailable"
        throw AetherUnavailableException()
    }

    suspend fun startPersistent(botToken: String, publicProxy: Boolean = false) {
        val session = acquire(botToken, keepAlive = true, publicProxy = publicProxy)
        session.close()
    }

    suspend fun stopPersistent() = mutex.withLock {
        persistent = false
        if (users == 0) stopLocked()
    }

    suspend fun shutdown() = mutex.withLock {
        users = 0
        persistent = false
        stopLocked()
    }

    private suspend fun release() = mutex.withLock {
        if (users > 0) users--
        if (users == 0 && !persistent) stopLocked()
    }

    private suspend fun stopLocked() {
        process.stop(STOP_TIMEOUT_MS)
        activeRoute = null
        activeEndpoints = null
    }

    class Session internal constructor(
        private val owner: AetherSessionManager,
        val port: Int
    ) {
        private var closed = false
        suspend fun close() {
            if (!closed) {
                closed = true
                owner.release()
            }
        }
    }

    companion object {
        // Turbo MASQUE scan can take 45s before the 25s startup and 10s validation phases.
        const val START_TIMEOUT_MS = 85_000L
        const val STOP_TIMEOUT_MS = 2_000L
        const val DISCOVERY_TIMEOUT_MS = 430_000L
    }
}
