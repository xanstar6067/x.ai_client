package com.adam.xai_client.domain.model

data class GeneratedImage(
    val bytes: ByteArray? = null,
    val mimeType: String = "image/jpeg",
    val filePath: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImage) return false
        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            filePath == other.filePath
    }

    override fun hashCode(): Int {
        var result = bytes?.contentHashCode() ?: 0
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        return result
    }
}

data class ImageGenerationOptions(
    val modelId: String,
    val prompt: String,
    val aspectRatio: String? = null,
    val resolution: String? = null,
    val sourceImageUrl: String? = null
)
