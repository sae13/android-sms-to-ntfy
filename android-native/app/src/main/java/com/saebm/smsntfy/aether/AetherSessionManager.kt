package com.saebm.smsntfy.aether

import android.content.Context
import android.util.Log
import com.saebm.smsntfy.data.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocketFactory

interface AetherProbe {
    /** Must complete a real Telegram Bot API response through the candidate SOCKS proxy. */
    suspend fun verifyTelegram(port: Int, botToken: String): Boolean
}

object TelegramAetherProxy {
    fun socketAddress(port: Int): InetSocketAddress =
        InetSocketAddress.createUnresolved("127.0.0.1", port)

    fun proxy(port: Int): Proxy = Proxy(Proxy.Type.SOCKS, socketAddress(port))
}

object TelegramAetherSocksClient {
    private const val TELEGRAM_HOST = "api.telegram.org"
    private const val TELEGRAM_PORT = 443

    fun getMe(port: Int, botToken: String): Boolean {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 8_000)
            socket.soTimeout = 8_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()
            if (input.read() != 0x05 || input.read() != 0x00) return false

            val host = TELEGRAM_HOST.toByteArray(StandardCharsets.US_ASCII)
            output.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()))
            output.write(host)
            output.write(byteArrayOf((TELEGRAM_PORT ushr 8).toByte(), TELEGRAM_PORT.toByte()))
            output.flush()
            if (input.read() != 0x05 || input.read() != 0x00) return false
            skipSocksAddress(input)

            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(
                socket,
                TELEGRAM_HOST,
                TELEGRAM_PORT,
                true
            ) as javax.net.ssl.SSLSocket
            tls.use {
                it.soTimeout = 8_000
                it.startHandshake()
                val request = "GET /bot$botToken/getMe HTTP/1.1\r\n" +
                    "Host: $TELEGRAM_HOST\r\n" +
                    "Connection: close\r\n\r\n"
                it.outputStream.write(request.toByteArray(StandardCharsets.US_ASCII))
                it.outputStream.flush()
                val body = it.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                return body.contains("\"ok\":true")
            }
        }
    }

    private fun skipSocksAddress(input: BufferedInputStream) {
        input.read() // RSV
        when (input.read()) {
            0x01 -> readExact(input, 4)
            0x03 -> readExact(input, requireByte(input))
            0x04 -> readExact(input, 16)
            else -> throw EOFException("Invalid SOCKS address type")
        }
        readExact(input, 2)
    }

    private fun requireByte(input: BufferedInputStream): Int =
        input.read().also { if (it < 0) throw EOFException("Unexpected SOCKS EOF") }

    private fun readExact(input: BufferedInputStream, count: Int) {
        repeat(count) { requireByte(input) }
    }
}

class TelegramAetherProbe(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socksRequest: (Int, String) -> Boolean = TelegramAetherSocksClient::getMe
) : AetherProbe {
    override suspend fun verifyTelegram(port: Int, botToken: String): Boolean =
        withContext(ioDispatcher) {
            val result = runCatching {
                socksRequest(port, botToken)
            }
            result.exceptionOrNull()?.let { error ->
                Log.w(TAG, "Telegram data-plane probe failed: ${error.javaClass.simpleName}")
            }
            result.getOrDefault(false)
        }

    private companion object {
        const val TAG = "SmsNtfyAether"
    }
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
