package com.smsntfy.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyPolicyTest {
    @Test
    fun idsAreAlwaysThreeDigitsAndWrapAfter999() {
        assertEquals("000", ReplyPolicy.formatId(0))
        assertEquals("042", ReplyPolicy.formatId(42))
        assertEquals(0, ReplyPolicy.nextId(999))
    }

    @Test
    fun validCommandPreservesEverythingAfterFirstSpace() {
        assertEquals(ReplyCommand(42, "سلام\nدنیا"), ReplyPolicy.parseCommand("/042 سلام\nدنیا"))
    }

    @Test
    fun malformedUnknownShapesAndEmptyTextAreRejected() {
        listOf("/42 hi", "/0042 hi", "/۰۴۲ hi", "/042", "/042    ", "REPLY:+1:hi").forEach {
            assertNull(it, ReplyPolicy.parseCommand(it))
        }
    }

    @Test
    fun eventRoutingRejectsMissingEventIdBeforeAnySend() {
        assertEquals(ReplyRoute.InvalidEventId, ReplyRouting.route("", "/042 hi"))
        assertEquals(ReplyRoute.InvalidCommand, ReplyRouting.route("event-1", "/42 hi"))
        assertEquals(ReplyRoute.Command("event-1", ReplyCommand(42, "hi")), ReplyRouting.route("event-1", "/042 hi"))
    }
}
