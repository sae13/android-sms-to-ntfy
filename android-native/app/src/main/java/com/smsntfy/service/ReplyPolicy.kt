package com.smsntfy.service

data class ReplyCommand(val id: Int, val message: String)

object ReplyPolicy {
    private val command = Regex("^/([0-9]{3}) (.*)$", setOf(RegexOption.DOT_MATCHES_ALL))

    fun formatId(id: Int): String = (id.mod(1000)).toString().padStart(3, '0')

    fun nextId(current: Int): Int = (current + 1).mod(1000)

    fun parseCommand(value: String): ReplyCommand? {
        val match = command.matchEntire(value) ?: return null
        val message = match.groupValues[2]
        if (message.trim().isEmpty()) return null
        return ReplyCommand(match.groupValues[1].toInt(), message)
    }
}
