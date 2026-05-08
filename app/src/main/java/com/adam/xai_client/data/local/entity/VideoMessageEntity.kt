package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.adam.xai_client.domain.model.MessageRole

@Entity(
    tableName = "video_messages",
    foreignKeys = [
        ForeignKey(
            entity = VideoChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("parentMessageId")]
)
data class VideoMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val sourceImageUrl: String? = null,
    val videoFilePath: String? = null,
    val videoMimeType: String? = null,
    val videoDurationSeconds: Int? = null,
    val videoRespectModeration: Boolean? = null,
    val requestId: String? = null,
    val aspectRatio: String? = null,
    val resolution: String? = null,
    val parentMessageId: Long? = null,
    val activeChildMessageId: Long? = null,
    val createdAt: Long
)
