package com.saebm.smsntfy.deltachat

import org.junit.Assert.assertEquals
import org.junit.Test

class DeltaChatMessageFormatterTest {
    @Test
    fun formatsReadableMultilineSms() {
        assertEquals(
            "SMS\nFrom: علی <+98912>\nTime: 2026-08-30T12:30:00Z\n\nخط اول\nخط دوم",
            DeltaChatMessageFormatter.sms(
                sender = "+98912",
                contact = "علی",
                message = "خط اول\nخط دوم",
                timestamp = "2026-08-30T12:30:00Z"
            )
        )
    }

    @Test
    fun formatsReadableCall() {
        assertEquals(
            "Call: missed\nFrom: علی <+98912>\nTime: 2026-08-30T12:30:00Z",
            DeltaChatMessageFormatter.call(
                callerNumber = "+98912",
                callerName = "علی",
                callState = "missed",
                timestamp = "2026-08-30T12:30:00Z"
            )
        )
    }

    @Test
    fun stripsControlCharactersFromMetadataButPreservesMessageLines() {
        assertEquals(
            "SMS\nFrom: A B <+98 evil>\nTime: now next\n\nbody\nline",
            DeltaChatMessageFormatter.sms(
                sender = "+98\u202Eevil",
                contact = "A\tB",
                message = "body\r\nline",
                timestamp = "now\u2028next"
            )
        )
    }
}