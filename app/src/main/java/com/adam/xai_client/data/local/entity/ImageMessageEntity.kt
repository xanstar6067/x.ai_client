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
    val imageMimeType: String? = null,
    val sourceMessageId: Long? = null,
    val parentMessageId: Long? = null,
    val activeChildMessageId: Long? = null,
    val createdAt: Long
)
