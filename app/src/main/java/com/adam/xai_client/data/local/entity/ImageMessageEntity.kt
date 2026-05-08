package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.adam.xai_client.domain.model.MessageRole

@Entity(
    tableName = "image_messages",
    foreignKeys = [
        ForeignKey(
            entity = ImageChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("parentMessageId")]
)
data class ImageMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val imageBytes: ByteArray? = null,
    val imageFilePath: String? = null,
    val imageMimeType: String? = null,
    val sourceMessageId: Long? = null,
    val parentMessageId: Long? = null,
    val activeChildMessageId: Long? = null,
    val createdAt: Long
)

data class ImageMessageSummary(
    val id: Long,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val imageFilePath: String? = null,
    val imageMimeType: String? = null,
    val hasImage: Boolean = false,
    val sourceMessageId: Long? = null,
    val parentMessageId: Long? = null,
    val activeChildMessageId: Long? = null,
    val createdAt: Long
)

data class ImagePayload(
    val imageBytes: ByteArray? = null,
    val imageFilePath: String? = null,
    val imageMimeType: String? = null
)

data class LegacyImageRef(
    val id: Long,
    val imageMimeType: String? = null,
    val byteCount: Int
)
