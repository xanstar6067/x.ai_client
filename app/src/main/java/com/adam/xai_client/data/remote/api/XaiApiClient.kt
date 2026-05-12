package com.adam.xai_client.data.remote.api

import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.VideoGenerationOptions
import com.adam.xai_client.domain.model.VideoGenerationProgress
import kotlinx.coroutines.flow.Flow

data class ChatStreamDelta(
    val content: String = "",
    val reasoningContent: String = "",
    val tokenUsage: TokenUsage? = null,
    val responseId: String? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val imageTokens: Int = 0
)

data class RemoteGeneratedVideo(
    val url: String,
    val durationSeconds: Int? = null,
    val respectModeration: Boolean? = null,
    val requestId: String
)

data class DownloadedVideo(
    val bytes: ByteArray,
    val mimeType: String = "video/mp4"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadedVideo) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

data class UploadedFile(
    val id: String,
    val filename: String,
    val bytes: Long
)

interface XaiApiClient {
    suspend fun getModels(
        apiKey: String,
        baseUrl: String
    ): List<AiModel>

    suspend fun sendChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings = ChatModelSettings(),
        promptCacheKey: String? = null,
        previousResponseId: String? = null
    ): String

    fun streamChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings = ChatModelSettings(),
        promptCacheKey: String? = null,
        previousResponseId: String? = null
    ): Flow<ChatStreamDelta>

    suspend fun uploadFile(
        apiKey: String,
        baseUrl: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): UploadedFile

    suspend fun generateImage(
        apiKey: String,
        baseUrl: String,
        options: ImageGenerationOptions
    ): GeneratedImage

    suspend fun generateVideo(
        apiKey: String,
        baseUrl: String,
        options: VideoGenerationOptions,
        onProgress: (VideoGenerationProgress) -> Unit = {}
    ): RemoteGeneratedVideo

    suspend fun downloadGeneratedVideo(url: String): DownloadedVideo
}
