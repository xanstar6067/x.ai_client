package com.adam.xai_client.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: String,
    val attachments: List<ApiMessageAttachment> = emptyList()
)

@Serializable
data class ApiMessageAttachment(
    val kind: ApiMessageAttachmentKind,
    val dataUrl: String? = null,
    val fileId: String? = null
)

@Serializable
enum class ApiMessageAttachmentKind {
    IMAGE,
    DOCUMENT
}
