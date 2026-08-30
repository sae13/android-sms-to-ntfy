package com.smsntfy.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramProxyTest {
    @Test
    fun parsesSocks5UrlWithoutExposingCredentials() {
        val parsed = TelegramProxy.parse("socks5://alice:secret@example.org:1080").getOrThrow()
        assertEquals(
            TelegramProxy.Socks5("example.org", 1080, "alice", "secret"),
            parsed
        )
    }

    @Test
    fun parsesTelegramSocksLink() {
        val parsed = TelegramProxy.parse(
            "https://t.me/socks?server=proxy.example&port=443&user=u&pass=p"
        ).getOrThrow()
        assertEquals(TelegramProxy.Socks5("proxy.example", 443, "u", "p"), parsed)
    }

    @Test
    fun mtProtoNeverBecomesDirectAndRequiresBridge() {
        val parsed = TelegramProxy.parse(
            "https://t.me/proxy?server=mt.example&port=443&secret=deadbeef"
        ).getOrThrow() as TelegramProxy.MtProto
        assertEquals(null, parsed.bridgeEndpoint())
        assertFalse((parsed as TelegramProxy) is TelegramProxy.Direct)
    }

    @Test
    fun mtProtoCanNameLocalSocksBridge() {
        val parsed = TelegramProxy.parse(
            "https://t.me/proxy?server=mt.example&port=443&secret=deadbeef&socks=127.0.0.1:1080"
        ).getOrThrow() as TelegramProxy.MtProto
        assertEquals(TelegramProxy.Socks5("127.0.0.1", 1080), parsed.bridgeEndpoint())
    }

    @Test
    fun rejectsMalformedProxy() {
        assertTrue(TelegramProxy.parse("socks5://example.org:0").isFailure)
        assertTrue(TelegramProxy.parse("https://t.me/proxy?server=x&port=443").isFailure)
    }
}
