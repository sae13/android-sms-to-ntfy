package com.smsntfy.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramMigrationInstrumentedTest {
    private lateinit var context: Context
    private val databaseName = "telegram-migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        AppDatabase.destroyInstance()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPersistsOffsetMappingAndUpdateDedupeAcrossRestart() = runBlocking {
        createVersionThreeDatabase()

        var database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        assertEquals(0L, database.telegramStateDao().nextOffset())
        assertTrue(database.telegramReplyMappingDao().insert(
            TelegramReplyMapping(chatId = "-1001", telegramMessageId = 42, phoneNumber = "+15551234567")
        ) >= 0)
        assertTrue(database.telegramUpdateDao().claim(99, 1000))
        assertFalse(database.telegramUpdateDao().claim(99, 1001))
        database.telegramUpdateDao().complete(99, "sent", 1002)
        database.telegramStateDao().advanceTo(100)
        database.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        assertEquals(100L, database.telegramStateDao().nextOffset())
        assertEquals("+15551234567", database.telegramReplyMappingDao().find("-1001", 42)?.phoneNumber)
        assertFalse(database.telegramUpdateDao().claim(99, 2000))
        database.close()
    }

    private fun createVersionThreeDatabase() {
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `event_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `sender` TEXT NOT NULL, `contact` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `success` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reply_mappings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `replyId` INTEGER NOT NULL, `phoneNumber` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reply_mappings_replyId_id` ON `reply_mappings` (`replyId`, `id`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reply_sequence` (`singleton` INTEGER NOT NULL, `nextReplyId` INTEGER NOT NULL, PRIMARY KEY(`singleton`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ntfy_commands` (`eventId` TEXT NOT NULL, `claimedAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`eventId`))")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 3 && newVersion == 4) AppDatabase.MIGRATION_3_4.migrate(db)
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(callback)
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            assertNotNull(helper.writableDatabase)
        }
    }
}
