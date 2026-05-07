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

    private val grok420 = ModelLimits(
        contextWindowTokens = 2_000_000,
        publicRateLimit = "Check current team limits in xAI Console; public limits can vary by account.",
        inputPricePerMillion = "$1.25",
        outputPricePerMillion = "$2.50",
        sourceLabel = "xAI API",
        sourceUrl = XAI_MODELS_URL,
        notes = listOf(
            "Grok 4.20 uses a 2M token context window.",
            "Use grok-4.20-reasoning for automatic reasoning or grok-4.20-non-reasoning for lower latency."
        )
    )

    private val grok420MultiAgent = grok420.copy(
        notes = listOf(
            "Use the Responses API for grok-4.20-multi-agent; Chat Completions is not supported.",
            "Agent count is controlled by reasoning.effort: low/medium selects 4 agents, high/xhigh selects 16 agents.",
            "The multi-agent variant does not support max_tokens."
        )
    )

    fun forModel(modelId: String?): ModelLimits? {
        val normalized = modelId.orEmpty().lowercase()
        return when {
            normalized == "grok-4.20-multi-agent" -> grok420MultiAgent
            normalized.startsWith("grok-4.20") -> grok420
            normalized.startsWith("grok-3-mini") -> grok3Mini
            normalized.startsWith("grok-3") -> grok3
            else -> null
        }
    }

    fun sourceForRateLimitDetails(): String = XAI_LIMITS_URL
}
