package com.saebm.smsntfy.aether

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherRouteSelectionTest {
    private val routes = AetherRoutes.supported

    @Test
    fun supportedCandidatesCoverEveryAetherTransportInFastestFirstOrder() {
        assertEquals(
            listOf("masque-h3", "masque-h2", "wireguard", "gool"),
            routes.map { it.id }
        )
    }

    @Test
    fun selectsFirstCandidateWithoutWaitingForLaterTransports() = runBlocking {
        val process = FakeProcess(listeningRoutes = routes.map { it.id }.toSet())
        val verified = mutableListOf<String>()

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { route, _ ->
            verified += route.id
            true
        }

        assertEquals("masque-h3", selected?.id)
        assertEquals(listOf("masque-h3"), process.started)
        assertEquals(listOf("masque-h3"), verified)
    }

    @Test
    fun reportsEachCandidateWhileSearching() = runBlocking {
        val process = FakeProcess(listeningRoutes = setOf("wireguard"))
        val attempts = mutableListOf<Pair<String, AetherRouteAttemptStage>>()

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L,
            onAttempt = { route, stage -> attempts += route.id to stage }
        ) { route, _ -> route.id == "wireguard" }

        assertEquals("wireguard", selected?.id)
        assertTrue(attempts.contains("masque-h3" to AetherRouteAttemptStage.STARTING))
        assertTrue(attempts.contains("masque-h3" to AetherRouteAttemptStage.FAILED))
        assertTrue(attempts.contains("masque-h2" to AetherRouteAttemptStage.FAILED))
        assertTrue(attempts.contains("wireguard" to AetherRouteAttemptStage.VERIFIED))
    }

    @Test
    fun selectsFirstCandidateThatPassesListeningAndDataPlaneChecks() = runBlocking {
        val process = FakeProcess(listeningRoutes = setOf("masque-h3", "masque-h2"))
        val verified = mutableListOf<String>()

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { route, port ->
            verified += "${route.id}:$port"
            route.id == "masque-h2"
        }

        assertEquals("masque-h2", selected?.id)
        assertEquals(listOf("masque-h3", "masque-h2"), process.started)
        assertEquals(listOf("masque-h3:1819", "masque-h2:1819"), verified)
    }

    @Test
    fun continuesToNextRouteWhenCandidateStartThrows() = runBlocking {
        val process = FakeProcess(
            listeningRoutes = setOf("masque-h2"),
            failingRoutes = setOf("masque-h3")
        )

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { route, _ -> route.id == "masque-h2" }

        assertEquals("masque-h2", selected?.id)
        assertEquals(listOf("masque-h3", "masque-h2"), process.started)
    }

    @Test
    fun continuesToNextRouteWhenCandidateListeningCheckThrows() = runBlocking {
        val process = FakeProcess(
            listeningRoutes = setOf("masque-h2"),
            failingListeningRoutes = setOf("masque-h3")
        )

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { route, _ -> route.id == "masque-h2" }

        assertEquals("masque-h2", selected?.id)
        assertEquals(listOf("masque-h3", "masque-h2"), process.started)
    }

    @Test
    fun continuesToNextRouteWhenDataPlaneProbeThrows() = runBlocking {
        val process = FakeProcess(listeningRoutes = setOf("masque-h3", "masque-h2"))

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { route, _ ->
            if (route.id == "masque-h3") error("probe failed")
            route.id == "masque-h2"
        }

        assertEquals("masque-h2", selected?.id)
        assertEquals(listOf("masque-h3", "masque-h2"), process.started)
    }

    @Test
    fun acceptsCandidateAsSoonAsSocksListenerAndTelegramProbeSucceed() = runBlocking {
        val process = FakeProcess(
            listeningRoutes = setOf("masque-h3"),
            listeningPorts = setOf(1819)
        )
        var probes = 0

        val selected = AetherRouteSelector(process).select(
            routes = listOf(routes.first()),
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { _, _ ->
            probes++
            true
        }

        assertEquals("masque-h3", selected?.id)
        assertEquals(1, probes)
    }

    @Test
    fun skipsDataPlaneProbeWhenListenerNeverStarts() = runBlocking {
        val process = FakeProcess(listeningRoutes = emptySet())
        var probes = 0

        val selected = AetherRouteSelector(process).select(
            routes = routes,
            endpoints = AetherEndpointPolicy.endpoints(false),
            port = 1819,
            startTimeoutMs = 1L,
            stopTimeoutMs = 1L
        ) { _, _ ->
            probes++
            true
        }

        assertEquals(null, selected)
        assertEquals(0, probes)
        assertEquals(routes.map { it.id }, process.started)
        assertTrue(process.stopCalls >= routes.size)
    }

    @Test
    fun cancellationStopsCandidateAndPropagates() = runBlocking {
        val process = FakeProcess(listeningRoutes = setOf("masque-h3"))
        val probeStarted = CompletableDeferred<Unit>()
        val selection = async {
            AetherRouteSelector(process).select(
                routes = listOf(routes.first()),
                endpoints = AetherEndpointPolicy.endpoints(publicProxy = true),
                port = 1819,
                startTimeoutMs = 1L,
                stopTimeoutMs = 1L
            ) { _, _ ->
                probeStarted.complete(Unit)
                CompletableDeferred<Boolean>().await()
            }
        }

        probeStarted.await()
        selection.cancel()
        var cancellation: CancellationException? = null
        try {
            selection.await()
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertTrue(cancellation != null)
        assertFalse(process.isAlive())
    }

    @Test
    fun cancellationFromProbeIsNotConvertedToFailedRoute() = runBlocking {
        val process = FakeProcess(
            listeningRoutes = setOf("masque-h3"),
            cancellationAwareStop = false
        )
        val selection = async {
            AetherRouteSelector(process).select(
                routes = listOf(routes.first()),
                endpoints = AetherEndpointPolicy.endpoints(publicProxy = true),
                port = 1819,
                startTimeoutMs = 1L,
                stopTimeoutMs = 1L
            ) { _, _ ->
                throw CancellationException("discovery canceled")
            }
        }

        var cancellation: CancellationException? = null
        try {
            selection.await()
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertTrue(cancellation != null)
        assertFalse(process.isAlive())
    }

    @Test
    fun candidateArgumentsMirrorAetherStRuntimeProfiles() {
        val endpoints = AetherEndpointPolicy.endpoints(false)
        val stateDirectory = "/tmp/aether"
        val arguments = routes.associate { route ->
            route.id to AetherArguments.build("/tmp/aether-bin", route, endpoints, stateDirectory)
        }

        assertTrue(arguments.getValue("masque-h3").containsAll(listOf("--protocol", "masque")))
        assertFalse(arguments.getValue("masque-h3").contains("--h2"))
        assertTrue(arguments.getValue("masque-h2").containsAll(listOf("--protocol", "masque", "--h2", "--fragment")))
        assertTrue(arguments.getValue("wireguard").containsAll(listOf("--protocol", "wg", "--no-data-check")))
        assertTrue(arguments.getValue("gool").containsAll(listOf("--protocol", "gool", "--no-data-check")))
        assertTrue(arguments.values.all { it.contains("--bind") && it.contains("127.0.0.1:1819") })
        assertTrue(arguments.values.all { it.contains("--http-proxy") && it.contains("127.0.0.1:1820") })
        assertTrue(arguments.values.all { it.contains("--perf") && it.contains("low") })
        assertTrue(arguments.values.all { it.contains("--startup-secs") && it.contains("30") })
        assertTrue(arguments.values.all { it.contains("--validate-secs") && it.contains("10") })
        assertTrue(arguments.values.all { it.contains("--reconnect-secs") && it.contains("2") })
        assertTrue(arguments.values.all { it.contains("-4") })
        assertTrue(arguments.values.all { it.contains("--log-level") && it.contains("debug") })
        assertTrue(arguments.values.all { it.contains("--config") })
        assertTrue(arguments.values.all { it.contains("--quick-reconnect") })
    }

    @Test
    fun candidateArgumentsUseTransportSpecificNoiseAndScanProfiles() {
        val endpoints = AetherEndpointPolicy.endpoints(false)
        val stateDirectory = "/tmp/aether"
        val arguments = routes.associate { route ->
            route.id to AetherArguments.build("/tmp/aether-bin", route, endpoints, stateDirectory)
        }

        assertTrue(arguments.getValue("masque-h3").containsAll(listOf("--noize", "firewall", "--scan", "balanced")))
        assertTrue(arguments.getValue("masque-h2").containsAll(listOf("--noize", "firewall", "--scan", "balanced")))
        assertTrue(arguments.getValue("wireguard").containsAll(listOf("--noize", "balanced", "--scan", "turbo")))
        assertTrue(arguments.getValue("gool").containsAll(listOf("--noize", "balanced", "--scan", "balanced")))
    }

    @Test
    fun candidateArgumentsUseOnlyFlagsSupportedByPinnedAetherCli() {
        val supportedFlags = setOf(
            "--bind",
            "--http-proxy",
            "--protocol",
            "--scan",
            "--noize",
            "--perf",
            "--startup-secs",
            "--validate-secs",
            "--reconnect-secs",
            "--quick-reconnect",
            "--no-data-check",
            "-4",
            "--log-level",
            "--config",
            "--wg-config",
            "--masque-config",
            "--h2",
            "--fragment"
        )
        val flagsWithValues = setOf(
            "--bind",
            "--http-proxy",
            "--protocol",
            "--scan",
            "--noize",
            "--perf",
            "--startup-secs",
            "--validate-secs",
            "--reconnect-secs",
            "--log-level",
            "--config",
            "--wg-config",
            "--masque-config"
        )

        routes.forEach { route ->
            val arguments = AetherArguments.build(
                "/tmp/aether-bin",
                route,
                AetherEndpointPolicy.endpoints(false),
                "/tmp/aether"
            ).drop(1)
            val flags = buildList {
                var index = 0
                while (index < arguments.size) {
                    val argument = arguments[index]
                    if (argument.startsWith("--")) add(argument)
                    index += if (argument in flagsWithValues) 2 else 1
                }
            }

            assertTrue("Unsupported CLI flag in ${route.id}: $flags", supportedFlags.containsAll(flags))
        }
    }

    private class FakeProcess(
        private val listeningRoutes: Set<String>,
        private val failingRoutes: Set<String> = emptySet(),
        private val failingListeningRoutes: Set<String> = emptySet(),
        private val listeningPorts: Set<Int> = setOf(1819, 1820),
        private val cancellationAwareStop: Boolean = true
    ) : AetherProcess {
        val started = mutableListOf<String>()
        var stopCalls = 0
        private var activeRoute: String? = null

        override suspend fun start(route: AetherRoute, endpoints: AetherEndpoints) {
            activeRoute = route.id
            started += route.id
            if (route.id in failingRoutes) error("start failed")
        }

        override suspend fun awaitListening(port: Int, timeoutMs: Long): Boolean {
            if (activeRoute in failingListeningRoutes) error("listening check failed")
            return activeRoute in listeningRoutes && port in listeningPorts
        }

        override suspend fun stop(timeoutMs: Long) {
            if (cancellationAwareStop) currentCoroutineContext().ensureActive()
            stopCalls++
            activeRoute = null
        }

        override fun isAlive(): Boolean = activeRoute != null
    }
}
