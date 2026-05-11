package com.adam.xai_client.domain.model

data class GeneratedVideo(
    val filePath: String,
    val mimeType: String = "video/mp4",
    val durationSeconds: Int? = null,
    val respectModeration: Boolean? = null
)

data class VideoGenerationOptions(
    val modelId: String,
    val prompt: String,
    val durationSeconds: Int = 5,
    val aspectRatio: String = "16:9",
    val resolution: String = "480p",
    val sourceImageUrl: String? = null,
    val sourceVideoFilePath: String? = null,
    val sourceVideoFileName: String? = null,
    val sourceVideoMimeType: String? = null
)

data class VideoGenerationProgress(
    val requestId: String? = null,
    val status: String = "pending",
    val progress: Int? = null
)
