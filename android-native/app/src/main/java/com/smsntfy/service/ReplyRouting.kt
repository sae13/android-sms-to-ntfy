package com.smsntfy.service

sealed class ReplyRoute {
    object InvalidEventId : ReplyRoute()
    object InvalidCommand : ReplyRoute()
    data class Command(val eventId: String, val command: ReplyCommand) : ReplyRoute()
}

object ReplyRouting {
    fun route(eventId: String, message: String): ReplyRoute {
        if (eventId.isBlank()) return ReplyRoute.InvalidEventId
        val command = ReplyPolicy.parseCommand(message) ?: return ReplyRoute.InvalidCommand
        return ReplyRoute.Command(eventId, command)
    }
}
