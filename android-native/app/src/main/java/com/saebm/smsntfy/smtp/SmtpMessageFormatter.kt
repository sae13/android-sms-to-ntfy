package com.saebm.smsntfy.smtp

data class SmtpMessage(val subject: String, val body: String)

object SmtpMessageFormatter {
    fun sms(sender: String, contact: String, body: String, timestamp: String): SmtpMessage {
        val c = scalar(contact).ifBlank { scalar(sender) }
        val s = scalar(sender)
        return SmtpMessage(
            subject = "SMS from $c <$s>",
            body = buildString {
                append("SMS\n")
                append("From: $c <$s>\n")
                append("Time: ${scalar(timestamp)}\n\n")
                append(messageBody(body))
            }
        )
    }

    fun call(callerNumber: String, callerName: String, callState: String, timestamp: String): SmtpMessage {
        val n = scalar(callerName).ifBlank { scalar(callerNumber) }
        return SmtpMessage(
            subject = "Call $callState from $n",
            body = buildString {
                append("Call: ${scalar(callState)}\n")
                append("From: $n <${scalar(callerNumber)}>\n")
                append("Time: ${scalar(timestamp)}")
            }
        )
    }

    private fun scalar(value: String): String = value
        .replace(Regex("[\\r\\n\\t\\u0000-\\u001F\\u007F\\u2028\\u2029\\u202A-\\u202E\\u2066-\\u2069]"), " ")
        .replace(Regex(" +"), " ")
        .trim()

    private fun messageBody(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        .replace('\u2028', '\n')
        .replace('\u2029', '\n')
}
