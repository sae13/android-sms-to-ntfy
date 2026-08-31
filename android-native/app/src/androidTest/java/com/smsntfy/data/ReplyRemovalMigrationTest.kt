package com.smsntfy.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplyRemovalMigrationTest {
    @Test fun migrationKeepsEventsAndDropsEveryReplyTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reply-removal.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        helper.writableDatabase.apply {
            execSQL("CREATE TABLE event_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, message TEXT NOT NULL, sender TEXT NOT NULL, contact TEXT NOT NULL, timestamp INTEGER NOT NULL, success INTEGER NOT NULL)")
            execSQL("INSERT INTO event_logs(id,type,title,message,sender,contact,timestamp,success) VALUES(42,'sms','kept','body','+15551234567','Saved contact',123456789,1)")
            listOf("reply_mappings", "reply_sequence", "ntfy_commands", "telegram_reply_mappings", "telegram_updates", "telegram_state").forEach { execSQL("CREATE TABLE $it(dummy INTEGER)") }
            version = 4
        }
        helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        db.openHelper.writableDatabase.query(
            "SELECT id,type,title,message,sender,contact,timestamp,success FROM event_logs"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals("sms", cursor.getString(1))
            assertEquals("kept", cursor.getString(2))
            assertEquals("body", cursor.getString(3))
            assertEquals("+15551234567", cursor.getString(4))
            assertEquals("Saved contact", cursor.getString(5))
            assertEquals(123456789L, cursor.getLong(6))
            assertEquals(1, cursor.getInt(7))
            assertFalse(cursor.moveToNext())
        }
        db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(0)
            assertFalse(names.any { name ->
                name.contains("reply") || name in setOf("ntfy_commands", "telegram_updates", "telegram_state")
            })
        }
        db.close()
    }
}
