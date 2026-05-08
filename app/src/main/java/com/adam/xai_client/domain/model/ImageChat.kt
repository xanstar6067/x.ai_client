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
    val imageFilePath: String? = null,
    val imageMimeType: String? = null,
    val hasImage: Boolean = imageBytes != null || imageFilePath != null,
    val sourceMessageId: Long? = null,
    val parentMessageId: Long? = null,
    val activeChildMessageId: Long? = null,
    val versionIndex: Int = 1,
    val versionCount: Int = 1,
    val createdAt: Long
) {
    val generatedImage: GeneratedImage?
        get() = if (imageBytes != null || imageFilePath != null) {
            GeneratedImage(
                bytes = imageBytes,
                mimeType = imageMimeType ?: "image/jpeg",
                filePath = imageFilePath
            )
        } else {
            null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageChatMessage) return false

        return id == other.id &&
            chatId == other.chatId &&
            role == other.role &&
            content == other.content &&
            imageBytes.contentEquals(other.imageBytes) &&
            imageFilePath == other.imageFilePath &&
            imageMimeType == other.imageMimeType &&
            hasImage == other.hasImage &&
            sourceMessageId == other.sourceMessageId &&
            parentMessageId == other.parentMessageId &&
            activeChildMessageId == other.activeChildMessageId &&
            versionIndex == other.versionIndex &&
            versionCount == other.versionCount &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + chatId.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (imageFilePath?.hashCode() ?: 0)
        result = 31 * result + (imageMimeType?.hashCode() ?: 0)
        result = 31 * result + hasImage.hashCode()
        result = 31 * result + (sourceMessageId?.hashCode() ?: 0)
        result = 31 * result + (parentMessageId?.hashCode() ?: 0)
        result = 31 * result + (activeChildMessageId?.hashCode() ?: 0)
        result = 31 * result + versionIndex
        result = 31 * result + versionCount
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
