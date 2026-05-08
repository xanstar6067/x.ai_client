package com.adam.xai_client.domain.model

data class VideoChat(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?
)

data class VideoChatMessage(
    val id: Long,
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
    val versionIndex: Int = 1,
    val versionCount: Int = 1,
    val createdAt: Long
) {
    val generatedVideo: GeneratedVideo?
        get() = videoFilePath?.let { path ->
            GeneratedVideo(
                filePath = path,
                mimeType = videoMimeType ?: "video/mp4",
                durationSeconds = videoDurationSeconds,
                respectModeration = videoRespectModeration
            )
        }
}
