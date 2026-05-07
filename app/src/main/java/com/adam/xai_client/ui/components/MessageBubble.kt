package com.adam.xai_client.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole

@Composable
fun MessageBubble(
    message: Message,
    isPending: Boolean,
    canRegenerate: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val reasoning = message.reasoningContent.orEmpty().trim()
    val visibleContent = message.content.ifBlank {
        if (reasoning.isNotBlank()) "" else if (isPending) "Ожидаю ответ..." else ""
    }
    val copyText = buildString {
        if (reasoning.isNotBlank()) {
            append("Размышления\n")
            append(reasoning)
            if (message.content.isNotBlank()) {
                append("\n\nОтвет\n")
            }
        }
        append(message.content)
    }.trim()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.86f),
            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                color = bubbleColor,
                contentColor = textColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.widthIn(min = 48.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isUser && reasoning.isNotBlank()) {
                        Text(
                            text = "Размышления",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.78f)
                        )
                        Text(
                            text = reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.78f)
                        )
                        if (visibleContent.isNotBlank()) {
                            HorizontalDivider(color = textColor.copy(alpha = 0.18f))
                        }
                    }
                    if (visibleContent.isNotBlank()) {
                        Text(
                            text = visibleContent,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (copyText.isNotBlank() || canRegenerate) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (copyText.isNotBlank()) {
                                IconButton(onClick = { onCopy(copyText) }) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Копировать"
                                    )
                                }
                            }
                            if (canRegenerate) {
                                IconButton(onClick = onRegenerate) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Перегенерировать"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
