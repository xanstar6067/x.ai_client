package com.adam.xai_client.domain.token

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType

class TokenCounter {
    private val registry = Encodings.newLazyEncodingRegistry()
    private val encoding: Encoding by lazy {
        runCatching { registry.getEncoding(EncodingType.O200K_BASE) }
            .getOrElse { registry.getEncoding(EncodingType.CL100K_BASE) }
    }

    fun count(text: String): Int {
        val normalized = text.trim()
        if (normalized.isBlank()) return 0
        return runCatching { encoding.countTokensOrdinary(normalized) }
            .getOrElse { encoding.countTokens(normalized) }
    }

    fun countMessage(content: String, reasoningContent: String? = null): Int {
        val visibleContentTokens = count(content)
        val reasoningTokens = reasoningContent
            ?.takeIf { it.isNotBlank() }
            ?.let(::count)
            ?: 0
        return visibleContentTokens + reasoningTokens
    }
}
