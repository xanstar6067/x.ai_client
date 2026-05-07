package com.adam.xai_client.data.local.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adam.xai_client.data.local.dao.AiModelDao
import com.adam.xai_client.data.local.dao.ChatDao
import com.adam.xai_client.data.local.dao.ChatModelSettingsDao
import com.adam.xai_client.data.local.dao.MessageDao
import com.adam.xai_client.data.local.dao.ModelRoleDao
import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.local.entity.ChatModelSettingsEntity
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        AiModelEntity::class,
        ModelRoleEntity::class,
        ChatModelSettingsEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun modelRoleDao(): ModelRoleDao
    abstract fun chatModelSettingsDao(): ChatModelSettingsDao

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
    }
}
