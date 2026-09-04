package com.saebm.smsntfy.telegram

/** Runs Telegram transports in increasing-cost order. */
object TelegramFallbackChain {
    suspend fun send(
        direct: suspend () -> TelegramSendResult,
        aether: suspend () -> TelegramSendResult
    ): TelegramSendResult {
        val directResult = attempt("direct", direct)
        if (directResult !is TelegramSendResult.RouteUnavailable) return directResult

        return attempt("Aether", aether)
    }

    private suspend fun attempt(
        route: String,
        send: suspend () -> TelegramSendResult
    ): TelegramSendResult = try {
        send()
    } catch (error: java.util.concurrent.CancellationException) {
        throw error
    } catch (error: Exception) {
        TelegramSendResult.RouteUnavailable("$route route failed: ${error.javaClass.simpleName}")
    }
}
