package com.adam.xai_client.data.local.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adam.xai_client.data.local.dao.AiModelDao
import com.adam.xai_client.data.local.dao.ChatDao
import com.adam.xai_client.data.local.dao.MessageDao
import com.adam.xai_client.data.local.dao.ModelRoleDao
import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        AiModelEntity::class,
        ModelRoleEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun modelRoleDao(): ModelRoleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT")
            }
        }
    }
}
