package com.smsntfy.deltachat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface DeltaChatCore {
    fun configure(loginCode: String): Int
    fun selectAccount(accountId: Int)
    fun join(invite: String): Int
    fun startIo()
    fun sendText(chatId: Int, text: String): Boolean
    fun close()
}

sealed interface DeltaChatSetupResult {
    data class Ready(val chatId: Int) : DeltaChatSetupResult
    data object InvalidLogin : DeltaChatSetupResult
    data object InvalidInvite : DeltaChatSetupResult
    data class Failed(val reason: String) : DeltaChatSetupResult
}

sealed interface DeltaChatSendResult {
    data object Sent : DeltaChatSendResult
    data class Failed(val reason: String) : DeltaChatSendResult
}

class DeltaChatClient(
    private val core: DeltaChatCore,
    private val loadAccountId: () -> Int,
    private val saveDestination: (accountId: Int, chatId: Int) -> Unit
) {
    private val operationMutex = Mutex()
    suspend fun setup(loginCode: String, invite: String): DeltaChatSetupResult = withContext(Dispatchers.IO) {
        if (!DeltaChatInputPolicy.isLoginCode(loginCode)) return@withContext DeltaChatSetupResult.InvalidLogin
        if (!DeltaChatInputPolicy.isInvite(invite)) return@withContext DeltaChatSetupResult.InvalidInvite

        operationMutex.withLock {
            try {
                val accountId = core.configure(loginCode.trim())
                val chatId = core.join(invite.trim())
                if (chatId <= 0) return@withLock DeltaChatSetupResult.Failed("Invitation could not be joined")
                core.startIo()
                saveDestination(accountId, chatId)
                DeltaChatSetupResult.Ready(chatId)
            } catch (error: Exception) {
                DeltaChatSetupResult.Failed(redactedReason(error))
            }
        }
    }

    suspend fun sendText(chatId: Int, text: String): Boolean =
        sendTextWithResult(chatId, text) is DeltaChatSendResult.Sent

    suspend fun sendTextWithResult(chatId: Int, text: String): DeltaChatSendResult = withContext(Dispatchers.IO) {
        if (chatId <= 0 || text.isBlank()) {
            return@withContext DeltaChatSendResult.Failed("Delta Chat destination is not configured")
        }
        operationMutex.withLock {
            try {
                core.selectAccount(loadAccountId())
                core.startIo()
                if (core.sendText(chatId, text)) {
                    DeltaChatSendResult.Sent
                } else {
                    DeltaChatSendResult.Failed("Delta Chat rejected the message")
                }
            } catch (error: Exception) {
                DeltaChatSendResult.Failed(redactedReason(error))
            }
        }
    }

    fun close() = core.close()

    private fun redactedReason(error: Exception): String {
        val reason = error.message.orEmpty()
            .replace(Regex("(?i)(password|passwd|pwd|token|secret)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]")
            .replace(Regex("(?i)dclogin:[^\\s]+"), "[REDACTED]")
            .take(200)
        return reason.ifBlank { "Delta Chat setup failed" }
    }
}
