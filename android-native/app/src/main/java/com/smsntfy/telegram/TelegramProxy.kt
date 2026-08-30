package com.smsntfy.telegram

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * User supplied Telegram transport configuration.
 *
 * Bot API is HTTPS and may use a SOCKS5 proxy. A Telegram MTProto proxy is
 * never used as a direct Bot API transport; it must name a local SOCKS bridge.
 */
sealed interface TelegramProxy {
    data object Direct : TelegramProxy

    data class Socks5(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null
    ) : TelegramProxy

    data class MtProto(
        val source: String,
        val localSocksHost: String?,
        val localSocksPort: Int?
    ) : TelegramProxy {
        fun bridgeEndpoint(): Socks5? =
            if (!localSocksHost.isNullOrBlank() && localSocksPort != null) {
                Socks5(localSocksHost, localSocksPort)
            } else null

        override fun toString(): String =
            "MtProto(localSocksHost=$localSocksHost, localSocksPort=$localSocksPort)"
    }

    companion object {
        fun parseOrNull(raw: String): TelegramProxy? = parse(raw).getOrNull()

        fun parseSocks5(raw: String): Socks5 =
            parse(raw).getOrThrow() as? Socks5 ?: error("Proxy is not SOCKS5")

        fun parseMtProto(raw: String): MtProto =
            parse(raw).getOrThrow() as? MtProto ?: error("Proxy is not MTProto")

        /**
         * Parses the settings field. Empty input means direct HTTPS.
         *
         * Supported SOCKS forms include `socks5://host:port`, `socks5h://…`,
         * and Telegram's `t.me/socks?server=…&port=…` links. A
         * `t.me/proxy?...&socks=127.0.0.1:1080` link records the MTProto
         * source and the local bridge endpoint. Without `socks`/`local`
         * the result is still represented as MTProto and the client fails
         * closed instead of making a direct request.
         */
        fun parse(raw: String): Result<TelegramProxy> {
            val value = raw.trim()
            if (value.isEmpty()) return Result.success(Direct)

            return runCatching {
                val lower = value.lowercase()
                when {
                    lower.startsWith("socks5://") || lower.startsWith("socks5h://") ->
                        parseSocksUri(value)
                    lower.startsWith("https://t.me/socks") || lower.startsWith("http://t.me/socks") ->
                        parseTelegramSocks(value)
                    lower.startsWith("https://t.me/proxy") || lower.startsWith("http://t.me/proxy") ->
                        parseMtProtoLink(value)
                    lower.startsWith("mtproto://") ->
                        parseMtProtoLink(value)
                    else -> error("Unsupported Telegram proxy URL")
                }
            }
        }

        private fun parseSocksUri(raw: String): Socks5 {
            val uri = URI(raw)
            val host = uri.host?.trim().orEmpty()
            require(host.isNotEmpty()) { "SOCKS host is missing" }
            val port = uri.port
            require(port in 1..65535) { "SOCKS port is invalid" }
            val userInfo = uri.userInfo?.split(':', limit = 2)
            return Socks5(host, port, userInfo?.getOrNull(0), userInfo?.getOrNull(1))
        }

        private fun parseTelegramSocks(raw: String): Socks5 {
            val params = queryParameters(raw)
            val host = params["server"].orEmpty().trim()
            val port = params["port"]?.toIntOrNull() ?: -1
            require(host.isNotEmpty()) { "SOCKS host is missing" }
            require(port in 1..65535) { "SOCKS port is invalid" }
            return Socks5(
                host = host,
                port = port,
                username = params["user"]?.ifBlank { null },
                password = params["pass"]?.ifBlank { null }
            )
        }

        private fun parseMtProtoLink(raw: String): MtProto {
            val params = queryParameters(raw)
            val endpoint = params["socks"] ?: params["local"] ?: params["bridge"] ?: params["local_socks"]
            val (host, port) = endpoint?.let(::parseHostPort) ?: (null to null)
            // Validate the source has the required server/port/secret when it
            // is a t.me/proxy link, without retaining or logging the secret.
            if (raw.lowercase().contains("t.me/proxy")) {
                require(!params["server"].isNullOrBlank()) { "MTProto server is missing" }
                require((params["port"]?.toIntOrNull() ?: -1) in 1..65535) {
                    "MTProto port is invalid"
                }
                require(!params["secret"].isNullOrBlank()) { "MTProto secret is missing" }
            }
            return MtProto(raw, host, port)
        }

        private fun queryParameters(raw: String): Map<String, String> {
            val query = URI(raw).rawQuery.orEmpty()
            if (query.isBlank()) return emptyMap()
            return query.split('&').mapNotNull { part ->
                val key = part.substringBefore('=', "").trim()
                if (key.isBlank()) return@mapNotNull null
                val value = part.substringAfter('=', "")
                URLDecoder.decode(key, StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }.toMap()
        }

        private fun parseHostPort(value: String): Pair<String?, Int?> {
            val text = value.trim().removePrefix("socks5://").removePrefix("socks5h://")
            val uri = runCatching { URI(if (text.contains("://")) text else "socks5://$text") }.getOrNull()
            val host = uri?.host ?: text.substringBeforeLast(':').trim().ifBlank { null }
            val port = uri?.port ?: text.substringAfterLast(':', "").toIntOrNull()
            require(!host.isNullOrBlank() && port != null && port in 1..65535) {
                "MTProto SOCKS bridge is invalid"
            }
            return host to port
        }
    }
}
