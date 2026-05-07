package com.adam.xai_client.domain.model

data class Message(
    val id: Long,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val createdAt: Long
)

enum class MessageRole(val apiName: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system")
}
