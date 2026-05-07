package com.adam.xai_client.domain.model

data class ChatModelSettings(
    val chatId: Long? = null,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val reasoningEffort: ReasoningEffort? = null
) {
    val hasCustomValues: Boolean
        get() = maxTokens != null ||
            temperature != null ||
            topP != null ||
            frequencyPenalty != null ||
            presencePenalty != null ||
            reasoningEffort != null
}

enum class ReasoningEffort(val apiName: String, val label: String) {
    LOW("low", "Low"),
    HIGH("high", "High")
}
