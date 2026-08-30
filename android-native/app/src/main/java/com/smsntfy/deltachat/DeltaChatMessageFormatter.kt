package com.smsntfy.deltachat

object DeltaChatMessageFormatter {
    fun sms(
        sender: String,
        contact: String,
        message: String,
        replyId: String,
        timestamp: String
    ): String = buildString {
        append("SMS\n")
        append("From: ${scalar(contact)} <${scalar(sender)}>\n")
        append("Time: ${scalar(timestamp)}\n")
        append("Reply ID: ${scalar(replyId)}\n\n")
        append(body(message))
    }

    fun call(
        callerNumber: String,
        callerName: String,
        callState: String,
        timestamp: String
    ): String = buildString {
        append("Call: ${scalar(callState)}\n")
        append("From: ${scalar(callerName)} <${scalar(callerNumber)}>\n")
        append("Time: ${scalar(timestamp)}")
    }

    private fun scalar(value: String): String = value
        .replace(Regex("[\\r\\n\\t\\u0000-\\u001F\\u007F\\u2028\\u2029\\u202A-\\u202E\\u2066-\\u2069]"), " ")
        .replace(Regex(" +"), " ")
        .trim()

    private fun body(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        .replace('\u2028', '\n')
        .replace('\u2029', '\n')
}
