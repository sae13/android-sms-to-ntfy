package com.smsntfy.aether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherArgumentsTest {
    private val h3 = AetherRoute("masque-h3", "masque")
    private val h2 = AetherRoute("masque-h2", "masque", listOf("--h2", "--fragment"))
    private val wireGuard = AetherRoute("wireguard", "wg")
    private val gool = AetherRoute("gool", "gool")

    @Test
    fun localModeKeepsBothListenersOnLoopback() {
        val endpoints = AetherEndpointPolicy.endpoints(publicProxy = false)
        assertEquals("127.0.0.1:1819", endpoints.socksAddress)
        assertEquals("127.0.0.1:1820", endpoints.httpAddress)
    }

    @Test
    fun publicModeExposesBothListenersOnAllInterfaces() {
        val endpoints = AetherEndpointPolicy.endpoints(publicProxy = true)
        assertEquals("0.0.0.0:1819", endpoints.socksAddress)
        assertEquals("0.0.0.0:1820", endpoints.httpAddress)
    }

    @Test
    fun eachRouteUsesOnlyArgumentsSupportedByThePinnedCli() {
        val binary = "/data/user/0/com.smsntfy/lib/libaether.so"
        val endpoints = AetherEndpointPolicy.endpoints(false)
        val stateDirectory = "/data/user/0/com.smsntfy/no_backup/aether"
        val commands = listOf(h3, h2, wireGuard, gool).map {
            AetherArguments.build(binary, it, endpoints, stateDirectory)
        }

        commands.forEach { command ->
            assertEquals(binary, command.first())
            assertTrue(command.containsAll(listOf(
                "--bind", "127.0.0.1:1819",
                "--http-proxy", "127.0.0.1:1820",
                "--scan", "turbo",
                "--perf", "low",
                "--config", "/data/user/0/com.smsntfy/no_backup/aether/aether.toml",
                "--wg-config", "/data/user/0/com.smsntfy/no_backup/aether/aether-wg.toml",
                "--masque-config", "/data/user/0/com.smsntfy/no_backup/aether/aether-masque.toml"
            )))
        }
        assertTrue(commands[0].containsAll(listOf("--protocol", "masque")))
        assertTrue(commands[1].containsAll(listOf("--protocol", "masque", "--h2", "--fragment")))
        assertTrue(commands[2].containsAll(listOf("--protocol", "wg")))
        assertTrue(commands[3].containsAll(listOf("--protocol", "gool")))
    }

    @Test
    fun publicModePassesBothWildcardListenerAddressesToThePinnedCli() {
        val command = AetherArguments.build(
            "/data/user/0/com.smsntfy/lib/libaether.so",
            h3,
            AetherEndpointPolicy.endpoints(publicProxy = true),
            "/data/user/0/com.smsntfy/no_backup/aether"
        )

        assertTrue(command.containsAll(listOf(
            "--bind", "0.0.0.0:1819",
            "--http-proxy", "0.0.0.0:1820"
        )))
    }
}
