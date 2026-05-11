package com.adam.xai_client.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageAttachment(
    val kind: MessageAttachmentKind,
    val displayName: String,
    val mimeType: String,
    val filePath: String,
    val sizeBytes: Long
)

@Serializable
enum class MessageAttachmentKind {
    IMAGE,
    DOCUMENT,
    VIDEO
}

