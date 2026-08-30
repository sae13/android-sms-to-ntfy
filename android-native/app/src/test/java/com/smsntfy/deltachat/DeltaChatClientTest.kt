package com.smsntfy.deltachat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaChatClientTest {
    @Test
    fun setupConfiguresThenJoinsAndStartsIo() = runBlocking {
        val core = FakeCore()
        val client = client(core)

        val result = client.setup("dclogin:user@example.org/?p=secret", "https://i.delta.chat/#fingerprint")

        assertTrue(result is DeltaChatSetupResult.Ready)
        assertEquals(listOf("configure", "join", "start"), core.calls)
        assertEquals(3, core.savedAccountId)
        assertEquals(7, core.savedChatId)
    }

    @Test
    fun invalidInputsNeverReachCore() = runBlocking {
        val core = FakeCore()
        val client = client(core)

        assertTrue(client.setup("https://example.org", "https://i.delta.chat/#x") is DeltaChatSetupResult.InvalidLogin)
        assertTrue(client.setup("dclogin:a", "https://example.org") is DeltaChatSetupResult.InvalidInvite)
        assertTrue(core.calls.isEmpty())
    }

    @Test
    fun sendFailsClosedWithoutReadyChat() = runBlocking {
        val core = FakeCore()
        val client = client(core)

        assertFalse(client.sendText(0, "hello"))
        assertTrue(core.calls.isEmpty())
    }

    @Test
    fun sendSelectsPersistedAccountAndDelegatesToConfiguredChat() = runBlocking {
        val core = FakeCore()
        val client = client(core, loadedAccountId = 3)

        assertTrue(client.sendText(9, "hello"))
        assertEquals(listOf("select:3", "start", "send:9:hello"), core.calls)
    }

    @Test
    fun sendFailsClosedWithoutPersistedAccount() = runBlocking {
        val core = FakeCore()
        val client = client(core, loadedAccountId = 0)

        assertFalse(client.sendText(9, "hello"))
        assertEquals(listOf("select:0"), core.calls)
    }

    @Test
    fun setupFailureDoesNotPersistReadyDestination() = runBlocking {
        val core = FakeCore().apply { joinedChatId = 0 }
        val client = client(core)

        assertTrue(client.setup("dclogin:a?p=x&v=1", "https://i.delta.chat/#x") is DeltaChatSetupResult.Failed)
        assertEquals(0, core.savedAccountId)
        assertEquals(0, core.savedChatId)
        assertEquals(listOf("configure", "join"), core.calls)
    }

    @Test
    fun coreFailureIsReportedWithoutCredentialEcho() = runBlocking {
        val core = FakeCore().apply { configureError = IllegalStateException("login failed") }
        val client = client(core)

        val result = client.setup("dclogin:user@example.org?p=secret&v=1", "https://i.delta.chat/#x")

        assertEquals(DeltaChatSetupResult.Failed("login failed"), result)
        assertFalse((result as DeltaChatSetupResult.Failed).reason.contains("secret"))
    }

    @Test
    fun rejectedCoreSendReturnsFailure() = runBlocking {
        val core = FakeCore().apply { sendAccepted = false }
        val client = client(core, loadedAccountId = 3)

        assertFalse(client.sendText(9, "hello"))
    }

    private fun client(core: FakeCore, loadedAccountId: Int = 0) = DeltaChatClient(
        core = core,
        loadAccountId = { loadedAccountId },
        saveDestination = { accountId, chatId ->
            core.savedAccountId = accountId
            core.savedChatId = chatId
        }
    )

    private class FakeCore : DeltaChatCore {
        val calls = mutableListOf<String>()
        var savedAccountId = 0
        var savedChatId = 0
        var joinedChatId = 7
        var sendAccepted = true
        var configureError: RuntimeException? = null

        override fun configure(loginCode: String): Int {
            calls += "configure"
            configureError?.let { throw it }
            return 3
        }

        override fun selectAccount(accountId: Int) {
            calls += "select:$accountId"
            require(accountId > 0)
        }

        override fun join(invite: String): Int {
            calls += "join"
            return joinedChatId
        }

        override fun startIo() {
            calls += "start"
        }

        override fun sendText(chatId: Int, text: String): Boolean {
            calls += "send:$chatId:$text"
            return sendAccepted
        }

        override fun close() = Unit
    }
}
