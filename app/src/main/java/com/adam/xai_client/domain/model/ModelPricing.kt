package com.adam.xai_client.domain.model

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
    return "$" + String.format(Locale.US, "%.2f", this / 1_000_000.0)
}
