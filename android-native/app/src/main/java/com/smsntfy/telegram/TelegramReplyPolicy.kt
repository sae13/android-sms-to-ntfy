package com.smsntfy.telegram

/**
 * Pure routing rules for Telegram replies. Keeping these checks independent
 * from Android and Room makes the fail-closed contract directly testable.
 */
sealed interface TelegramReplyDecision {
    data object Reject : TelegramReplyDecision

    data class Send(
        val phoneNumber: String,
        val text: String
    ) : TelegramReplyDecision
}

object TelegramReplyPolicy {
    fun isReplyCandidate(update: TelegramUpdate, expectedChatId: String): Boolean =
        !update.edited &&
            !update.fromBot &&
            update.chatId == expectedChatId.trim() &&
            !update.text.isNullOrBlank() &&
            update.replyToMessageId != null &&
            update.repliedMessageFromBot

    fun decide(
        update: TelegramUpdate,
        expectedChatId: String,
        mappedPhoneNumber: String?
    ): TelegramReplyDecision {
        if (!isReplyCandidate(update, expectedChatId)) return TelegramReplyDecision.Reject

        val phoneNumber = mappedPhoneNumber.orEmpty()
        val text = update.text.orEmpty()
        if (!isValidPhoneNumber(phoneNumber) || text.isBlank()) {
            return TelegramReplyDecision.Reject
        }
        return TelegramReplyDecision.Send(phoneNumber, text)
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean =
        phoneNumber.matches(Regex("^[+]?[0-9\\s\\-()]{6,20}\$"))
}
