package com.smsntfy.deltachat

import android.content.Context
import chat.delta.rpc.Rpc
import chat.delta.rpc.types.Qr
import com.b44t.messenger.DcAccounts
import com.b44t.messenger.DcEventChannel
import com.b44t.messenger.FFITransport
import java.io.File

class NativeDeltaChatCore(context: Context) : DeltaChatCore {
    // DcAccounts keeps the native pointer, while this strong Java reference prevents
    // the event channel finalizer from releasing its native pointer prematurely.
    private val eventChannel = DcEventChannel()
    private val accounts: DcAccounts
    private val rpc: Rpc
    private var accountId: Int = 0

    init {
        System.loadLibrary("native-utils")
        val dataDirectory = File(context.filesDir, "deltachat-accounts").apply { mkdirs() }
        accounts = DcAccounts(dataDirectory.absolutePath, eventChannel)
        rpc = Rpc(FFITransport(accounts.jsonrpcInstance))
    }

    override fun configure(loginCode: String): Int {
        val newAccountId = rpc.addAccount()
        val parsed = rpc.checkQr(newAccountId, loginCode)
        if (parsed !is Qr.Login) {
            accounts.removeAccount(newAccountId)
            throw IllegalArgumentException("Delta Chat login code was rejected")
        }

        try {
            rpc.addTransportFromQr(newAccountId, loginCode)
            if (!rpc.isConfigured(newAccountId)) {
                throw IllegalStateException("Delta Chat transport was not configured")
            }
            rpc.selectAccount(newAccountId)
            accountId = newAccountId
            return newAccountId
        } catch (error: Exception) {
            accounts.removeAccount(newAccountId)
            throw IllegalStateException("Delta Chat login failed", error)
        }
    }

    override fun selectAccount(accountId: Int) {
        require(accountId > 0) { "Delta Chat account is not configured" }
        if (!rpc.isConfigured(accountId)) {
            throw IllegalStateException("Delta Chat account is not configured")
        }
        rpc.selectAccount(accountId)
        this.accountId = accountId
    }

    override fun join(invite: String): Int = rpc.secureJoin(requireAccountId(), invite)

    override fun startIo() = rpc.startIo(requireAccountId())

    override fun sendText(chatId: Int, text: String): Boolean =
        rpc.miscSendTextMessage(requireAccountId(), chatId, text) > 0

    override fun close() {
        accounts.stopIo()
        accounts.unref()
    }

    private fun requireAccountId(): Int {
        check(accountId > 0) { "Delta Chat account is not selected" }
        return accountId
    }
}
