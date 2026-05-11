package com.adam.xai_client.domain.model

fun AiModel.isImageGenerationModel(): Boolean {
    val normalizedId = id.lowercase()
    val normalizedName = name.lowercase()
    return normalizedId.startsWith("grok-imagine-image") ||
        normalizedName.startsWith("grok-imagine-image") ||
        normalizedId.startsWith("grok-2-image") ||
        normalizedName.startsWith("grok-2-image")
}

fun AiModel.isVideoGenerationModel(): Boolean {
    val normalizedId = id.lowercase()
    val normalizedName = name.lowercase()
    return normalizedId.startsWith("grok-imagine-video") ||
        normalizedName.startsWith("grok-imagine-video") ||
        outputModalities.any { it.equals("video", ignoreCase = true) }
}

fun AiModel.isTextChatModel(): Boolean {
    return !isImageGenerationModel() && !isVideoGenerationModel()
}

fun AiModel.supportsImageInput(): Boolean {
    return inputModalities.any { it.equals("image", ignoreCase = true) }
}

fun AiModel.supportsFileAttachments(): Boolean {
    val normalizedId = id.lowercase()
    val normalizedName = name.lowercase()
    return isTextChatModel() &&
        (
            normalizedId.startsWith("grok-4") ||
                normalizedName.startsWith("grok-4") ||
                aliases.any { it.lowercase().startsWith("grok-4") }
            )
}
