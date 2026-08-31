package com.smsntfy.aether

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class AetherRouteAttemptStage {
    STARTING,
    VERIFYING,
    VERIFIED,
    FAILED
}

/** Tries candidate transports in order and keeps only a data-plane verified process. */
class AetherRouteSelector(
    private val process: AetherProcess
) {
    suspend fun select(
        routes: List<AetherRoute>,
        endpoints: AetherEndpoints,
        port: Int,
        startTimeoutMs: Long,
        stopTimeoutMs: Long,
        onAttempt: (AetherRoute, AetherRouteAttemptStage) -> Unit = { _, _ -> },
        verify: suspend (AetherRoute, Int) -> Boolean
    ): AetherRoute? {
        try {
            for (route in routes) {
                onAttempt(route, AetherRouteAttemptStage.STARTING)
                stopNonCancellable(stopTimeoutMs)
                val started = try {
                    process.start(route, endpoints)
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                if (!started) {
                    onAttempt(route, AetherRouteAttemptStage.FAILED)
                    continue
                }
                val socksListening = try {
                    process.awaitListening(port, startTimeoutMs)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                if (socksListening) onAttempt(route, AetherRouteAttemptStage.VERIFYING)
                val verified = socksListening && try {
                    verify(route, port)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                if (verified) {
                    onAttempt(route, AetherRouteAttemptStage.VERIFIED)
                    return route
                }
                onAttempt(route, AetherRouteAttemptStage.FAILED)
                stopNonCancellable(stopTimeoutMs)
            }
            return null
        } catch (error: CancellationException) {
            stopNonCancellable(stopTimeoutMs)
            throw error
        }
    }

    private suspend fun stopNonCancellable(stopTimeoutMs: Long) = withContext(NonCancellable) {
        runCatching { process.stop(stopTimeoutMs) }
    }
}
