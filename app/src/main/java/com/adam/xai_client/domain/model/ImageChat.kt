package com.adam.xai_client.domain.model

data class ImageChat(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?
)

data class ImageChatMessage(
    val id: Long,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val imageBytes: ByteArray? = null,
    val imageMimeType: String? = null,
    val sourceMessageId: Long? = null,
    val createdAt: Long
) {
    val generatedImage: GeneratedImage?
        get() = imageBytes?.let { bytes ->
            GeneratedImage(
                bytes = bytes,
                mimeType = imageMimeType ?: "image/jpeg"
            )
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageChatMessage) return false

        return id == other.id &&
            chatId == other.chatId &&
            role == other.role &&
            content == other.content &&
            imageBytes.contentEquals(other.imageBytes) &&
            imageMimeType == other.imageMimeType &&
            sourceMessageId == other.sourceMessageId &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + chatId.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (imageMimeType?.hashCode() ?: 0)
        result = 31 * result + (sourceMessageId?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
