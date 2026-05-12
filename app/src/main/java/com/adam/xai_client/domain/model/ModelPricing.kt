package com.adam.xai_client.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

fun Int.toUsdPerMillionTokens(): String {
    val dollars = this / 10_000.0
    return "$" + if (dollars < 1.0) {
        String.format(Locale.US, "%.4f", dollars).trimEnd('0').trimEnd('.')
    } else {
        String.format(Locale.US, "%.2f", dollars)
    }
}

fun Int.toUsdPerImage(): String {
    return "$" + String.format(Locale.US, "%.2f", this / 10_000_000_000.0)
}

fun Int.toUsdPriceInput(): String {
    val dollars = this / 10_000.0
    return if (dollars < 1.0) {
        String.format(Locale.US, "%.4f", dollars).trimEnd('0').trimEnd('.')
    } else {
        String.format(Locale.US, "%.2f", dollars).trimEnd('0').trimEnd('.')
    }
}

fun String.toTokenPriceTicksOrNull(): Int? {
    val normalized = trim()
        .removePrefix("$")
        .replace(',', '.')
    if (normalized.isBlank()) return null
    val dollars = normalized.toBigDecimalOrNull() ?: return null
    if (dollars < BigDecimal.ZERO) return null
    return dollars
        .multiply(BigDecimal(10_000))
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()
}

fun Long.toUsdCost(): String {
    val dollars = this / 1_000_000.0
    val pattern = when {
        dollars == 0.0 -> "%.4f"
        dollars < 0.01 -> "%.6f"
        dollars < 1.0 -> "%.4f"
        else -> "%.2f"
    }
    return "$" + String.format(Locale.US, pattern, dollars)
}

fun estimatedTokenUsageCostMicros(
    promptTokens: Int,
    completionTokens: Int,
    cachedTokens: Int,
    imageTokens: Int,
    model: AiModel?
): Long {
    if (model == null) return 0L
    val cached = cachedTokens.coerceAtMost(promptTokens).coerceAtLeast(0)
    val image = imageTokens.coerceAtMost(promptTokens - cached).coerceAtLeast(0)
    val uncachedTextInput = (promptTokens - cached - image).coerceAtLeast(0)
    return tokensCostMicros(uncachedTextInput, model.promptTextTokenPrice) +
        tokensCostMicros(cached, model.cachedPromptTextTokenPrice) +
        tokensCostMicros(image, model.promptImageTokenPrice ?: model.promptTextTokenPrice) +
        tokensCostMicros(completionTokens, model.completionTextTokenPrice)
}

fun estimatedPromptCostMicros(
    promptTokens: Int,
    model: AiModel?
): Long = tokensCostMicros(promptTokens.coerceAtLeast(0), model?.promptTextTokenPrice)

fun AiModel.withKnownTokenPricingFallback(): AiModel {
    val limits = XaiModelLimits.forModel(this) ?: XaiModelLimits.forModel(id) ?: return this
    return copy(
        promptTextTokenPrice = promptTextTokenPrice ?: limits.inputPricePerMillion.toTokenPriceTicksOrNull(),
        cachedPromptTextTokenPrice = cachedPromptTextTokenPrice
            ?: limits.cachedInputPricePerMillion?.toTokenPriceTicksOrNull(),
        completionTextTokenPrice = completionTextTokenPrice ?: limits.outputPricePerMillion.toTokenPriceTicksOrNull()
    )
}

private fun tokensCostMicros(tokens: Int, priceTicks: Int?): Long {
    if (tokens <= 0 || priceTicks == null || priceTicks <= 0) return 0L
    return tokens.toLong() * priceTicks.toLong() / 10_000L
}
