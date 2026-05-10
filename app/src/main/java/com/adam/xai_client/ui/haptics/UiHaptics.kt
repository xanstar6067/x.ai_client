package com.adam.xai_client.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

val LocalUiHapticsEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

enum class UiHapticSignal {
    Tap,
    Toggle,
    Selection,
    Confirm,
    Destructive
}

@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    return rememberHapticClick(UiHapticSignal.Tap, onClick)
}

@Composable
fun rememberHapticClick(
    signal: UiHapticSignal = UiHapticSignal.Tap,
    onClick: () -> Unit
): () -> Unit {
    val hapticFeedback = LocalHapticFeedback.current
    val enabled = LocalUiHapticsEnabled.current
    val currentOnClick = rememberUpdatedState(onClick)
    return remember(hapticFeedback, enabled, signal) {
        {
            hapticFeedback.performUiHaptic(signal, enabled)
            currentOnClick.value()
        }
    }
}

@Composable
fun <T> rememberHapticValueChange(onValueChange: (T) -> Unit): (T) -> Unit {
    return rememberHapticValueChange(UiHapticSignal.Toggle, onValueChange)
}

@Composable
fun <T> rememberHapticValueChange(
    signal: UiHapticSignal = UiHapticSignal.Toggle,
    onValueChange: (T) -> Unit
): (T) -> Unit {
    val hapticFeedback = LocalHapticFeedback.current
    val enabled = LocalUiHapticsEnabled.current
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    return remember(hapticFeedback, enabled, signal) {
        { value ->
            hapticFeedback.performUiHaptic(signal, enabled)
            currentOnValueChange.value(value)
        }
    }
}

fun HapticFeedback.performUiHaptic(signal: UiHapticSignal, enabled: Boolean) {
    if (!enabled) return
    val type = when (signal) {
        UiHapticSignal.Destructive,
        UiHapticSignal.Confirm -> HapticFeedbackType.LongPress
        UiHapticSignal.Tap,
        UiHapticSignal.Toggle,
        UiHapticSignal.Selection -> HapticFeedbackType.TextHandleMove
    }
    performHapticFeedback(type)
}
