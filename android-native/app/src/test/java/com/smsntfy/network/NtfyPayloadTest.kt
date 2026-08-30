package com.smsntfy.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfyPayloadTest {
    @Test
    fun smsBodyIsReadableAndPreservesMultilinePersian() {
        assertEquals(
            "type: sms\nid: \"042\"\nfrom: \"+98912\"\ncontact: \"علی\"\ntime: \"2026-08-29T19:00:00Z\"\nmessage: |-\n  خط اول\n  خط دوم",
            NtfyPayloadFormatter.sms("042", "+98912", "علی", "2026-08-29T19:00:00Z", "خط اول\nخط دوم")
        )
        val formatted = NtfyPayloadFormatter.sms("042", "+98912", "علی", "time", "خط اول\nخط دوم")
        assertTrue(formatted.codePoints().toArray().contains(0x0A))
        assertTrue(!formatted.contains("\\n"))
    }

    @Test
    fun callBodyHasNoReplyId() {
        assertEquals(
            "type: call\nfrom: \"+98912\"\ncontact: \"علی\"\nstatus: \"missed\"\ntime: \"2026-08-29T19:00:00Z\"",
            NtfyPayloadFormatter.call("+98912", "علی", "missed", "2026-08-29T19:00:00Z")
        )
    }

    @Test
    fun parsesRealJsonIncludingEscapesAndMultilineMessage() {
        val parsed = NtfyEventParser.parse("""{"id":"n1","message":"/042 سلام\nدنیا","title":"پاسخ","priority":4,"tags":["reply"]}""")
        assertEquals("/042 سلام\nدنیا", parsed?.message)
        assertEquals(listOf("reply"), parsed?.tags)
    }

    @Test
    fun parserExposesNtfyEventTypeSoKeepalivesCanBeIgnored() {
        val keepalive = NtfyEventParser.parse(
            """{"id":"k1","event":"keepalive","topic":"reply"}"""
        )
        val message = NtfyEventParser.parse(
            """{"id":"m1","event":"message","topic":"reply","message":"/002 hi"}"""
        )

        assertEquals("keepalive", keepalive?.event)
        assertEquals("message", message?.event)
    }

    @Test
    fun malformedJsonIsRejected() {
        assertNull(NtfyEventParser.parse("{not json"))
    }

    @Test
    fun hostileScalarCharactersCannotInjectReadableFields() {
        val formatted = NtfyPayloadFormatter.sms(
            "042\radmin: true",
            "+98\t912\u202Eevil",
            "name\u0000\u2028forged: yes",
            "time\u2029next",
            "اصل\r\nخط دوم\tبا تب\u2028و جداکننده"
        )
        assertTrue(formatted.contains("id: \"042 admin: true\""))
        assertTrue(formatted.contains("from: \"+98 912evil\""))
        assertTrue(formatted.contains("contact: \"name forged: yes\""))
        assertTrue(formatted.contains("time: \"time next\""))
        assertTrue(formatted.endsWith("message: |-\n  اصل\n  خط دوم\tبا تب\n  و جداکننده"))
        assertTrue(formatted.none { it == '\r' || it == '\u2028' || it == '\u2029' || it == '\u202E' || it == '\u0000' })
        assertTrue(NtfyPayloadFormatter.sms("042", "from", "contact", "time", "می\u200Cروم").contains("می\u200Cروم"))
    }

    @Test
    fun malformedParserResultCarriesVisibleReason() {
        val result = NtfyEventParser.parseResult("{not json")
        assertTrue(result is NtfyParseResult.Malformed)
        assertTrue((result as NtfyParseResult.Malformed).reason.isNotBlank())
    }

    @Test
    fun incomingSseJsonStillDecodesEscapedNewlinesWithMoshi() {
        val decoded = NtfyEventParser.parse("""{"id":"n1","message":"خط اول\nخط دوم"}""")!!

        assertTrue(decoded.message.codePoints().toArray().contains(0x0A))
        assertTrue(!decoded.message.contains("\\n"))
        assertEquals("خط اول\nخط دوم", decoded.message)
    }
}
