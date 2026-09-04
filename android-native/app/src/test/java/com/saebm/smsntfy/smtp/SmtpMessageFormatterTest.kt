package com.saebm.smsntfy.smtp

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class SmtpMessageFormatterTest {
    @Test
    fun formatsSmsSubjectAndBody() {
        val message = SmtpMessageFormatter.sms(
            sender = "+98912\ninject",
            contact = "Ali",
            body = "hello\r\nworld",
            timestamp = "2026-09-03 10:00"
        )
        assertTrue(message.subject.contains("Ali"))
        assertTrue(message.subject.contains("+98912 inject"))
        assertTrue(message.body.contains("hello\nworld"))
        assertFalse(message.subject.contains('\n'))
    }

    @Test
    fun formatsCallNotification() {
        val message = SmtpMessageFormatter.call(
            callerNumber = "+98912",
            callerName = "Ali",
            callState = "RINGING",
            timestamp = "2026-09-03 10:00"
        )
        assertTrue(message.subject.contains("Ali"))
        assertTrue(message.body.contains("RINGING"))
    }

    @Test
    fun headerInjectionIsSanitized() {
        val message = SmtpMessageFormatter.sms(
            sender = "a@b.com\r\nBcc: victim@c.com",
            contact = "X\r\nY",
            body = "hi",
            timestamp = "t"
        )
        assertFalse(message.subject.contains('\r'))
        assertFalse(message.subject.contains('\n'))
    }
}
