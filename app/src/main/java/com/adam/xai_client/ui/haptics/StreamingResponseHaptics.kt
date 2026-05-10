package com.adam.xai_client.ui.haptics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole

@Composable
fun StreamingResponseHaptics(
    messages: List<Message>,
    isSending: Boolean,
    enabled: Boolean
) {
    val hapticFeedback = LocalHapticFeedback.current
    var observedMessageId by remember { mutableStateOf<Long?>(null) }
    var observedLength by remember { mutableIntStateOf(0) }
    var observedBucket by remember { mutableIntStateOf(0) }
    var lastPulseAt by remember { mutableLongStateOf(0L) }

    val streamingMessage = if (isSending) {
        messages.lastOrNull { it.role == MessageRole.ASSISTANT }
    } else {
        null
    }
    val signalLength = streamingMessage?.let { message ->
        message.content.length + message.reasoningContent.orEmpty().length
    } ?: 0

    LaunchedEffect(enabled, isSending, streamingMessage?.id, signalLength) {
        val message = streamingMessage
        if (!enabled || !isSending || message == null) {
            observedMessageId = null
            observedLength = 0
            observedBucket = 0
            lastPulseAt = 0L
            return@LaunchedEffect
        }

        if (observedMessageId != message.id) {
            observedMessageId = message.id
            observedLength = signalLength
            observedBucket = signalLength / CHARACTERS_PER_PULSE
            lastPulseAt = 0L
            return@LaunchedEffect
        }

        if (signalLength <= observedLength) return@LaunchedEffect

        val nextBucket = signalLength / CHARACTERS_PER_PULSE
        val now = SystemClock.elapsedRealtime()
        if (nextBucket > observedBucket && now - lastPulseAt >= MIN_PULSE_INTERVAL_MS) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            observedBucket = nextBucket
            lastPulseAt = now
        }
        observedLength = signalLength
    }
}

private const val CHARACTERS_PER_PULSE = 18
private const val MIN_PULSE_INTERVAL_MS = 80L
