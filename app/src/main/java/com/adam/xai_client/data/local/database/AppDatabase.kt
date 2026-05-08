package com.adam.xai_client.data.local.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adam.xai_client.data.local.dao.AiModelDao
import com.adam.xai_client.data.local.dao.ChatDao
import com.adam.xai_client.data.local.dao.ChatModelSettingsDao
import com.adam.xai_client.data.local.dao.ImageChatDao
import com.adam.xai_client.data.local.dao.ImageMessageDao
import com.adam.xai_client.data.local.dao.MessageDao
import com.adam.xai_client.data.local.dao.ModelRoleDao
import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.local.entity.ChatModelSettingsEntity
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.ImageChatEntity
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        AiModelEntity::class,
        ModelRoleEntity::class,
        ChatModelSettingsEntity::class,
        ImageChatEntity::class,
        ImageMessageEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun modelRoleDao(): ModelRoleDao
    abstract fun chatModelSettingsDao(): ChatModelSettingsDao
    abstract fun imageChatDao(): ImageChatDao
    abstract fun imageMessageDao(): ImageMessageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_model_settings (
                        chatId INTEGER NOT NULL,
                        maxTokens INTEGER,
                        temperature REAL,
                        topP REAL,
                        frequencyPenalty REAL,
                        presencePenalty REAL,
                        reasoningEffort TEXT,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(chatId),
                        FOREIGN KEY(chatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN tokenCount INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS image_chats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        selectedModelId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS image_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chatId INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        imageBytes BLOB,
                        imageMimeType TEXT,
                        sourceMessageId INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(chatId) REFERENCES image_chats(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_image_messages_chatId ON image_messages(chatId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_model_settings ADD COLUMN contextMessageLimit INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN parentMessageId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN activeChildMessageId INTEGER")
                db.execSQL("ALTER TABLE image_messages ADD COLUMN parentMessageId INTEGER")
                db.execSQL("ALTER TABLE image_messages ADD COLUMN activeChildMessageId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parentMessageId ON messages(parentMessageId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_image_messages_parentMessageId ON image_messages(parentMessageId)")
                linkLinearHistory(db, "messages")
                linkLinearHistory(db, "image_messages")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_model_settings ADD COLUMN webSearchEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_models ADD COLUMN aliases TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN fingerprint TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN version TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN inputModalities TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN outputModalities TEXT")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN maxPromptLength INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN promptTextTokenPrice INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN cachedPromptTextTokenPrice INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN completionTextTokenPrice INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN promptImageTokenPrice INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN searchPrice INTEGER")
                db.execSQL("ALTER TABLE ai_models ADD COLUMN imagePrice INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("ai_models", "imagePrice")) {
                    db.execSQL("ALTER TABLE ai_models ADD COLUMN imagePrice INTEGER")
                }
            }
        }

        private fun linkLinearHistory(db: SupportSQLiteDatabase, tableName: String) {
            db.query("SELECT id, chatId FROM $tableName ORDER BY chatId ASC, createdAt ASC, id ASC").use { cursor ->
                var currentChatId: Long? = null
                var previousMessageId: Long? = null
                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(0)
                    val chatId = cursor.getLong(1)
                    if (currentChatId != chatId) {
                        currentChatId = chatId
                        previousMessageId = null
                    }
                    previousMessageId?.let { parentId ->
                        db.execSQL(
                            "UPDATE $tableName SET parentMessageId = ? WHERE id = ?",
                            arrayOf(parentId, messageId)
                        )
                        db.execSQL(
                            "UPDATE $tableName SET activeChildMessageId = ? WHERE id = ?",
                            arrayOf(messageId, parentId)
                        )
                    }
                    previousMessageId = messageId
                }
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
            query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) return true
                }
            }
            return false
        }
    }
}
