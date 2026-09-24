package com.saebm.smsntfy.deltachat

import chat.delta.rpc.Rpc
import com.b44t.messenger.DcAccounts
import com.b44t.messenger.DcBackupProvider
import com.b44t.messenger.DcEventChannel
import com.b44t.messenger.DcJsonrpcInstance
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeltaChatBindingsProvenanceTest {
    @Test
    fun `checked in bindings expose the account manager and matching json rpc api`() {
        assertNotNull(DcAccounts::class.java.getDeclaredMethod("getJsonrpcInstance"))
        assertNotNull(DcEventChannel::class.java.getDeclaredConstructor())
        assertNotNull(DcJsonrpcInstance::class.java.getDeclaredMethod("request", String::class.java))
        assertNotNull(DcBackupProvider::class.java)
        assertNotNull(Rpc::class.java)
    }
}
