package com.adam.xai_client.domain.model

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImage) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
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
