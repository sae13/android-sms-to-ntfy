package com.smsntfy.network

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NtfyPayloadFormatter {
    fun timestamp(epochMillis: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMillis))

    fun sms(id: String, sender: String, contact: String, time: String, message: String): String =
        listOf(
            "type: sms",
            "id: ${quote(id)}",
            "from: ${quote(sender)}",
            "contact: ${quote(contact)}",
            "time: ${quote(time)}",
            "message: |-",
            sanitizeMessage(message).split('\n').joinToString("\n") { "  $it" }
        ).joinToString("\n")

    fun call(number: String, contact: String, status: String, time: String): String =
        listOf(
            "type: call",
            "from: ${quote(number)}",
            "contact: ${quote(contact)}",
            "status: ${quote(status)}",
            "time: ${quote(time)}"
        ).joinToString("\n")

    private fun quote(value: String): String =
        "\"${sanitizeScalar(value).replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun sanitizeScalar(value: String): String = buildString {
        value.forEach { char ->
            when {
                char == '\t' || char == '\r' || char == '\n' || char.category == CharCategory.LINE_SEPARATOR ||
                    char.category == CharCategory.PARAGRAPH_SEPARATOR -> append(' ')
                char.category == CharCategory.CONTROL || (char.category == CharCategory.FORMAT && char != '\u200C') ||
                    char.category == CharCategory.SURROGATE || char.category == CharCategory.PRIVATE_USE ||
                    char.category == CharCategory.UNASSIGNED -> Unit
                else -> append(char)
            }
        }
    }

    /** Preserve message characters except normalize all line separators to LF and remove unsafe controls. */
    private fun sanitizeMessage(value: String): String {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        return buildString {
            normalized.forEach { char ->
                when {
                    char == '\n' -> append('\n')
                    char.category == CharCategory.LINE_SEPARATOR || char.category == CharCategory.PARAGRAPH_SEPARATOR -> append('\n')
                    char == '\t' -> append(char)
                    char.category == CharCategory.CONTROL || (char.category == CharCategory.FORMAT && char != '\u200C') ||
                        char.category == CharCategory.SURROGATE || char.category == CharCategory.PRIVATE_USE ||
                        char.category == CharCategory.UNASSIGNED -> Unit
                    else -> append(char)
                }
            }
        }
    }
}
