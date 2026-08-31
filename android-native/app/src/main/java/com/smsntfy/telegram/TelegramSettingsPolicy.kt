package com.smsntfy.telegram

enum class TelegramSettingsField { BOT_TOKEN, CHAT_ID }

sealed interface TelegramSettingsValidation {
    data class Valid(val config: TelegramConfig) : TelegramSettingsValidation
    data class Invalid(val field: TelegramSettingsField) : TelegramSettingsValidation
}

object TelegramSettingsPolicy {
    fun validate(requestedEnabled: Boolean, botToken: String, chatId: String): TelegramSettingsValidation {
        val normalized = TelegramConfig(requestedEnabled, botToken.trim(), chatId.trim())
        if (!requestedEnabled) return TelegramSettingsValidation.Valid(normalized)
        if (!TelegramBotClient.isValidBotToken(normalized.botToken)) {
            return TelegramSettingsValidation.Invalid(TelegramSettingsField.BOT_TOKEN)
        }
        if (!TelegramBotClient.isValidChatId(normalized.chatId)) {
            return TelegramSettingsValidation.Invalid(TelegramSettingsField.CHAT_ID)
        }
        return TelegramSettingsValidation.Valid(normalized)
    }
}
