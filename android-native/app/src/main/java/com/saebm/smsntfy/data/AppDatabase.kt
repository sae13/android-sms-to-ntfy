package com.saebm.smsntfy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room database for durable forwarding event logs. */
@Database(entities = [EventLog::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sms_ntfy_db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { INSTANCE = it }
        }

        fun destroyInstance() { INSTANCE = null }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reply_mappings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `replyId` INTEGER NOT NULL, `phoneNumber` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reply_mappings_replyId_receivedAt` ON `reply_mappings` (`replyId`, `receivedAt`)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_reply_mappings_replyId_receivedAt`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reply_mappings_replyId_id` ON `reply_mappings` (`replyId`, `id`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reply_sequence` (`singleton` INTEGER NOT NULL, `nextReplyId` INTEGER NOT NULL, PRIMARY KEY(`singleton`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ntfy_commands` (`eventId` TEXT NOT NULL, `claimedAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`eventId`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `telegram_reply_mappings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chatId` TEXT NOT NULL, `telegramMessageId` INTEGER NOT NULL, `phoneNumber` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_telegram_reply_mappings_chatId_telegramMessageId` ON `telegram_reply_mappings` (`chatId`, `telegramMessageId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `telegram_updates` (`updateId` INTEGER NOT NULL, `claimedAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`updateId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `telegram_state` (`singleton` INTEGER NOT NULL, `nextOffset` INTEGER NOT NULL, PRIMARY KEY(`singleton`))")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `reply_mappings`")
                db.execSQL("DROP TABLE IF EXISTS `reply_sequence`")
                db.execSQL("DROP TABLE IF EXISTS `ntfy_commands`")
                db.execSQL("DROP TABLE IF EXISTS `telegram_reply_mappings`")
                db.execSQL("DROP TABLE IF EXISTS `telegram_updates`")
                db.execSQL("DROP TABLE IF EXISTS `telegram_state`")
            }
        }
    }
}
