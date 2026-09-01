package com.saebm.smsntfy.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfyPublishRequestTest {
    @Test
    fun smsPublishUsesRawMultilineTextWithSafeMetadataHeaders() {
        val body = NtfyPayloadFormatter.sms( "+98912", "علی", "time", "خط اول\nخط دوم")
        val request = NtfyPublishRequest.build(
            url = "https://ntfy.example/topic",
            body = body,
            metadata = NtfyPublishMetadata(
                title = "علی <+98912>",
                priority = 4,
                tags = listOf("sms", "inbox"),
                click = "sms:+98912",
                actions = listOf(NtfyPublishAction("view", "View", "sms:+98912"))
            )
        )

        val wireBody = request.body!!.let { buffer ->
            okio.Buffer().also { buffer.writeTo(it) }.readUtf8()
        }
        assertTrue(wireBody.startsWith("type: sms"))
        assertTrue(wireBody.contains('\n'))
        assertFalse(wireBody.startsWith("{"))
        assertEquals("text/plain; charset=utf-8", request.body!!.contentType().toString())
        assertTrue(request.header("Title")!!.startsWith("=?UTF-8?B?"))
        assertEquals("4", request.header("Priority"))
        assertEquals("sms,inbox", request.header("Tags"))
        assertEquals("sms:+98912", request.header("Click"))
        assertEquals("view, View, sms:+98912", request.header("Actions"))
    }

    @Test
    fun unsafeOptionalMetadataIsOmittedRatherThanInjectingHeaders() {
        val request = NtfyPublishRequest.build(
            "https://ntfy.example/topic",
            "type: sms\nmessage: |-\n  hi",
            NtfyPublishMetadata(
                title = "ok\r\nX-Evil: yes",
                priority = 99,
                tags = listOf("sms", "bad\ntag"),
                click = "sms:+1\r\nX-Evil: yes",
                actions = listOf(NtfyPublishAction("view", "Bad\nLabel", "sms:+1"))
            )
        )

        assertEquals(null, request.header("Title"))
        assertEquals(null, request.header("Priority"))
        assertEquals(null, request.header("Tags"))
        assertEquals(null, request.header("Click"))
        assertEquals(null, request.header("Actions"))
        assertEquals(null, request.header("X-Evil"))
    }
}