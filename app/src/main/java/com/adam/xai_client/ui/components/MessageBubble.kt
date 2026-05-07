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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole

@Composable
fun MessageBubble(
    message: Message,
    isPending: Boolean,
    canRegenerate: Boolean,
    canResend: Boolean,
    canEdit: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onResend: () -> Unit,
    onEdit: (String) -> Unit,
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
    val copyText = message.content.trim()
    var isReasoningExpanded by rememberSaveable(message.id) {
        mutableStateOf(message.content.isBlank())
    }
    var isEditing by rememberSaveable(message.id) { mutableStateOf(false) }
    var editedText by rememberSaveable(message.id) { mutableStateOf(message.content) }

    LaunchedEffect(message.id, message.content.isNotBlank(), isPending) {
        if (!isPending && message.content.isNotBlank()) {
            isReasoningExpanded = false
        }
    }

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
                        ReasoningBlock(
                            reasoning = reasoning,
                            isExpanded = isReasoningExpanded,
                            textColor = textColor,
                            onToggle = { isReasoningExpanded = !isReasoningExpanded },
                            onCopy = { onCopy(reasoning) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${message.tokenCount} ток.",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.72f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (canEdit) {
                                IconButton(
                                    onClick = {
                                        editedText = message.content
                                        isEditing = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Редактировать"
                                    )
                                }
                            }
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
                            if (canResend) {
                                IconButton(onClick = onResend) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        contentDescription = "Отправить снова"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = { Text("Редактировать сообщение") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    minLines = 3,
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(editedText)
                        isEditing = false
                    },
                    enabled = editedText.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ReasoningBlock(
    reasoning: String,
    isExpanded: Boolean,
    textColor: Color,
    onToggle: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Размышления",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Копировать размышления",
                    tint = textColor.copy(alpha = 0.78f)
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) {
                        "Свернуть размышления"
                    } else {
                        "Показать размышления"
                    },
                    tint = textColor.copy(alpha = 0.78f)
                )
            }
        }
        if (isExpanded) {
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.78f)
            )
        }
    }
}
