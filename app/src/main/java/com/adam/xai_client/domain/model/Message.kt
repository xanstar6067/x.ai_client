package com.adam.xai_client.domain.model

data class Message(
    val id: Long,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val tokenCount: Int = 0,
    val parentMessageId: Long? = null,
    val activeChildMessageId: Long? = null,
    val versionIndex: Int = 1,
    val versionCount: Int = 1,
    val createdAt: Long
)

enum class MessageRole(val apiName: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system")
}
