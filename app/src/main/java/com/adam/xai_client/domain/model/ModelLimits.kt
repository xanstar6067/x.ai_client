package com.adam.xai_client.domain.model

data class ModelLimits(
    val contextWindowTokens: Int,
    val publicRateLimit: String,
    val inputPricePerMillion: String,
    val outputPricePerMillion: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val notes: List<String> = emptyList()
)

object XaiModelLimits {
    private const val XAI_MODELS_URL = "https://docs.x.ai/developers/models"
    private const val XAI_LIMITS_URL = "https://docs.x.ai/developers/rate-limits"

    private val grok3 = ModelLimits(
        contextWindowTokens = 131_072,
        publicRateLimit = "600 rpm in the public xAI models table; team limits may differ in xAI Console",
        inputPricePerMillion = "$3.00",
        outputPricePerMillion = "$15.00",
        sourceLabel = "xAI Models and Pricing",
        sourceUrl = XAI_MODELS_URL,
        notes = listOf(
            "The context window is the maximum prompt size accepted by the model.",
            "xAI says per-team rate limits should be checked in xAI Console."
        )
    )

    private val grok3Mini = ModelLimits(
        contextWindowTokens = 131_072,
        publicRateLimit = "480 rpm in the public xAI models table; team limits may differ in xAI Console",
        inputPricePerMillion = "$0.30",
        outputPricePerMillion = "$0.50",
        sourceLabel = "xAI Models and Pricing",
        sourceUrl = XAI_MODELS_URL,
        notes = listOf(
            "The context window is the maximum prompt size accepted by the model.",
            "xAI documents token consumption and team-specific limits separately.",
            "Reasoning effort is only exposed here for Grok 3 Mini text models."
        )
    )

    fun forModel(modelId: String?): ModelLimits? {
        val normalized = modelId.orEmpty().lowercase()
        return when {
            normalized.startsWith("grok-3-mini") -> grok3Mini
            normalized.startsWith("grok-3") -> grok3
            else -> null
        }
    }

    fun sourceForRateLimitDetails(): String = XAI_LIMITS_URL
}
