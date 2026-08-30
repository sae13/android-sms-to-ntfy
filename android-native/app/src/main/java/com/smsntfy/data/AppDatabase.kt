package com.smsntfy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room database for event logging, reply routing, and durable command claims. */
@Database(
    entities = [EventLog::class, ReplyMapping::class, ReplySequence::class, NtfyCommand::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
    abstract fun replyMappingDao(): ReplyMappingDao
    abstract fun ntfyCommandDao(): NtfyCommandDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sms_ntfy_db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT OR IGNORE INTO `reply_sequence` (`singleton`, `nextReplyId`) VALUES (1, 0)")
                    }
                })
                .build().also { INSTANCE = it }
        }

        fun destroyInstance() {
            INSTANCE = null
        }

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
                db.execSQL("INSERT OR IGNORE INTO `reply_sequence` (`singleton`, `nextReplyId`) SELECT 1, COALESCE((SELECT (`replyId` + 1) % 1000 FROM `reply_mappings` ORDER BY `id` DESC LIMIT 1), 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ntfy_commands` (`eventId` TEXT NOT NULL, `claimedAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`eventId`))")
            }
        }
    }
}
