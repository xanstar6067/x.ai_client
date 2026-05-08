package com.adam.xai_client.domain.model

data class ModelLimits(
    val contextWindowTokens: Int?,
    val publicRateLimit: String,
    val inputPricePerMillion: String,
    val cachedInputPricePerMillion: String? = null,
    val outputPricePerMillion: String,
    val imagePrice: String? = null,
    val sourceLabel: String,
    val sourceUrl: String,
    val notes: List<String> = emptyList()
)

object XaiModelLimits {
    private const val XAI_MODELS_URL = "https://docs.x.ai/developers/models"
    private const val XAI_LIMITS_URL = "https://docs.x.ai/developers/rate-limits"
    private const val XAI_IMAGE_MODEL_URL = "https://docs.x.ai/developers/models/grok-imagine-image"
    private const val XAI_IMAGE_QUALITY_MODEL_URL = "https://docs.x.ai/developers/models/grok-imagine-image-quality"
    private const val XAI_VIDEO_MODEL_URL = "https://docs.x.ai/developers/models/grok-imagine-video"

    private val grok3 = ModelLimits(
        contextWindowTokens = 131_072,
        publicRateLimit = "600 rpm in the public xAI models table; team limits may differ in xAI Console",
        inputPricePerMillion = "$3.00",
        cachedInputPricePerMillion = "$0.75",
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
        cachedInputPricePerMillion = "$0.075",
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
        cachedInputPricePerMillion = "$0.20",
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

    private val grokImagineImage = ModelLimits(
        contextWindowTokens = null,
        publicRateLimit = "300 rpm in the public xAI model page; team limits may differ in xAI Console",
        inputPricePerMillion = "not token-priced",
        outputPricePerMillion = "not token-priced",
        imagePrice = "$0.02",
        sourceLabel = "xAI Grok Imagine Image model page",
        sourceUrl = XAI_IMAGE_MODEL_URL,
        notes = listOf(
            "Image generation is billed per generated image.",
            "1K and 2K output are listed at $0.02 per image for grok-imagine-image.",
            "Generated URLs are temporary, so the app downloads generated images before storing them."
        )
    )

    private val grokImagineImageQuality = grokImagineImage.copy(
        publicRateLimit = "300 rpm in the public xAI model page; team limits may differ in xAI Console",
        imagePrice = "$0.04-$0.05",
        sourceLabel = "xAI Grok Imagine Image Quality model page",
        sourceUrl = XAI_IMAGE_QUALITY_MODEL_URL,
        notes = listOf(
            "Image generation is billed per generated image.",
            "The public model page lists 1K output at $0.04 and 2K output at $0.05.",
            "Generated URLs are temporary, so the app downloads generated images before storing them."
        )
    )

    private val grokImagineVideo = ModelLimits(
        contextWindowTokens = null,
        publicRateLimit = "70 rpm in the public xAI model page; team limits may differ in xAI Console",
        inputPricePerMillion = "not token-priced",
        outputPricePerMillion = "$0.05/sec at 480p, $0.07/sec at 720p",
        sourceLabel = "xAI Grok Imagine Video model page",
        sourceUrl = XAI_VIDEO_MODEL_URL,
        notes = listOf(
            "Video generation is asynchronous: start a request, poll by request_id, then download the temporary MP4 URL.",
            "The public docs list a 1-15 second duration range for generation.",
            "Supported generation resolutions are 480p and 720p."
        )
    )

    fun forModel(modelId: String?): ModelLimits? {
        val normalized = modelId.orEmpty().lowercase()
        return when {
            normalized.startsWith("grok-imagine-video") -> grokImagineVideo
            normalized.startsWith("grok-imagine-image-quality") -> grokImagineImageQuality
            normalized.startsWith("grok-imagine-image-pro") -> grokImagineImageQuality.copy(
                notes = grokImagineImageQuality.notes + "xAI announced grok-imagine-image-pro deprecation as of May 15, 2026; use grok-imagine-image-quality for new requests."
            )
            normalized.startsWith("grok-imagine-image") -> grokImagineImage
            normalized.startsWith("grok-2-image") -> grokImagineImage
            normalized.startsWith("grok-4.20-multi-agent") -> grok420MultiAgent
            normalized.startsWith("grok-4.20") -> grok420
            normalized.startsWith("grok-3-mini") -> grok3Mini
            normalized.startsWith("grok-3") -> grok3
            else -> null
        }
    }

    fun forModel(model: AiModel?): ModelLimits? {
        if (model == null) return null
        val fallback = forModel(model.id)
        val hasApiMetadata = model.hasApiMetadata()
        val contextWindowTokens = model.maxPromptLength ?: fallback?.contextWindowTokens
        val inputPrice = model.promptTextTokenPrice?.toUsdPerMillionTokens()
            ?: fallback?.inputPricePerMillion
            ?: "unknown"
        val outputPrice = model.completionTextTokenPrice?.toUsdPerMillionTokens()
            ?: fallback?.outputPricePerMillion
            ?: "unknown"
        val notes = buildList {
            if (model.inputModalities.isNotEmpty()) {
                add("Input modalities: ${model.inputModalities.joinToString()}.")
            }
            if (model.outputModalities.isNotEmpty()) {
                add("Output modalities: ${model.outputModalities.joinToString()}.")
            }
            if (model.aliases.isNotEmpty()) {
                add("Aliases: ${model.aliases.joinToString()}.")
            }
            model.fingerprint?.let { add("Fingerprint: $it.") }
            model.version?.let { add("Version: $it.") }
            if (model.searchPrice != null) {
                add("Search price: ${model.searchPrice.toUsdPerMillionTokens()} / 1M searches.")
            }
            if (model.promptImageTokenPrice != null) {
                add("Image input price: ${model.promptImageTokenPrice.toUsdPerMillionTokens()} / 1M tokens.")
            }
            if (model.imagePrice != null) {
                add("Image generation price: ${model.imagePrice.toUsdPerImage()} per image.")
            }
            if (model.maxPromptLength == null && fallback != null) {
                add("Context window comes from the built-in fallback because this API response did not include max_prompt_length.")
            }
            addAll(fallback?.notes.orEmpty())
        }

        if (contextWindowTokens == null && !hasApiMetadata) return null

        return ModelLimits(
            contextWindowTokens = contextWindowTokens,
            publicRateLimit = fallback?.publicRateLimit
                ?: "Team-specific limits are available in xAI Console.",
            inputPricePerMillion = inputPrice,
            cachedInputPricePerMillion = model.cachedPromptTextTokenPrice?.toUsdPerMillionTokens()
                ?: fallback?.cachedInputPricePerMillion,
            outputPricePerMillion = outputPrice,
            imagePrice = model.imagePrice?.toUsdPerImage() ?: fallback?.imagePrice,
            sourceLabel = when {
                model.imagePrice != null -> "xAI Image Generation Models API"
                hasApiMetadata -> "xAI Language Models API"
                else -> fallback?.sourceLabel ?: "xAI API"
            },
            sourceUrl = if (hasApiMetadata) XAI_LANGUAGE_MODELS_URL else fallback?.sourceUrl ?: XAI_MODELS_URL,
            notes = notes.distinct()
        )
    }

    fun sourceForRateLimitDetails(): String = XAI_LIMITS_URL

    private const val XAI_LANGUAGE_MODELS_URL = "https://docs.x.ai/developers/rest-api-reference/inference/models"

    private fun AiModel.hasApiMetadata(): Boolean {
        return aliases.isNotEmpty() ||
            fingerprint != null ||
            version != null ||
            inputModalities.isNotEmpty() ||
            outputModalities.isNotEmpty() ||
            maxPromptLength != null ||
            promptTextTokenPrice != null ||
            cachedPromptTextTokenPrice != null ||
            completionTextTokenPrice != null ||
            promptImageTokenPrice != null ||
            searchPrice != null ||
            imagePrice != null
    }
}
