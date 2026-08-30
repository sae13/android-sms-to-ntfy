package com.smsntfy.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomPersistenceTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO reply_sequence (singleton, nextReplyId) VALUES (1, 0)"
        )
    }

    @After fun close() = db.close()

    @Test fun allocationIsAtomicUniqueAndWraps() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE reply_sequence SET nextReplyId = 999 WHERE singleton = 1")
        val allocated = (1..2).map { n -> async { db.replyMappingDao().allocateAndInsert("+$n", n.toLong()) } }.awaitAll()
        assertEquals(setOf(999, 0), allocated.map { it.replyId }.toSet())
        assertEquals(1, db.replyMappingDao().nextReplyId())
    }

    @Test fun newestMappingUsesInsertionOrderAndClaimsAreUniqueTerminal() = runBlocking {
        db.replyMappingDao().insert(ReplyMapping(replyId = 42, phoneNumber = "+old", receivedAt = 9_000))
        db.replyMappingDao().insert(ReplyMapping(replyId = 42, phoneNumber = "+new", receivedAt = 1_000))
        assertEquals("+new", db.replyMappingDao().findNewest(42)?.phoneNumber)

        val claims = listOf(async { db.ntfyCommandDao().claim("event-1", 1) }, async { db.ntfyCommandDao().claim("event-1", 2) }).awaitAll()
        assertEquals(1, claims.count { it })
        assertEquals(1, db.ntfyCommandDao().complete("event-1", "failed", 3))
        assertEquals(0, db.ntfyCommandDao().complete("event-1", "sent", 4))
    }

    @Test fun staleClaimsBecomeObservableTerminalFailuresWithoutRetry() = runBlocking {
        assertTrue(db.ntfyCommandDao().claim("stale", 1))
        assertEquals(1, db.ntfyCommandDao().finalizeStaleClaims(2, 2))
        assertFalse(db.ntfyCommandDao().claim("stale", 3))
        assertEquals(0, db.ntfyCommandDao().finalizeStaleClaims(4, 4))
    }
}
