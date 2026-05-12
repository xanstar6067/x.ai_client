package com.adam.xai_client.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageAttachmentKind
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.ui.haptics.UiHapticSignal
import com.adam.xai_client.ui.haptics.rememberHapticClick

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
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
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
        if (reasoning.isNotBlank()) "" else if (isPending) "Ожидание ответа..." else ""
    }
    val copyText = message.content.trim()
    var isReasoningExpanded by rememberSaveable(message.id) {
        mutableStateOf(message.content.isBlank())
    }
    var isEditing by rememberSaveable(message.id) { mutableStateOf(false) }
    var isDeleteConfirmationOpen by rememberSaveable(message.id) { mutableStateOf(false) }
    var editedText by rememberSaveable(message.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = message.content,
                selection = TextRange(message.content.length)
            )
        )
    }
    val openEdit = rememberHapticClick {
        editedText = TextFieldValue(
            text = message.content,
            selection = TextRange(message.content.length)
        )
        isEditing = true
    }
    val requestDelete = rememberHapticClick(UiHapticSignal.Destructive) {
        isDeleteConfirmationOpen = true
    }
    val copyMessage = rememberHapticClick { onCopy(copyText) }
    val hapticRegenerate = rememberHapticClick(UiHapticSignal.Confirm, onRegenerate)
    val hapticResend = rememberHapticClick(UiHapticSignal.Confirm, onResend)
    val hapticPreviousVersion = rememberHapticClick(UiHapticSignal.Selection, onPreviousVersion)
    val hapticNextVersion = rememberHapticClick(UiHapticSignal.Selection, onNextVersion)
    val saveEdit = rememberHapticClick(UiHapticSignal.Confirm) {
        onEdit(editedText.text)
        isEditing = false
    }
    val cancelEdit = rememberHapticClick { isEditing = false }
    val confirmDelete = rememberHapticClick(UiHapticSignal.Destructive) {
        isDeleteConfirmationOpen = false
        onDelete()
    }
    val cancelDelete = rememberHapticClick { isDeleteConfirmationOpen = false }

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
                        MarkdownText(
                            markdown = visibleContent,
                            color = textColor
                        )
                    }
                    if (message.attachments.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            message.attachments.forEach { attachment ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (attachment.kind == MessageAttachmentKind.IMAGE) {
                                            Icons.Filled.Image
                                        } else {
                                            Icons.Filled.Description
                                        },
                                        contentDescription = null,
                                        tint = textColor.copy(alpha = 0.78f)
                                    )
                                    Text(
                                        text = attachment.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = textColor.copy(alpha = 0.78f)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                if (message.tokenCount > 0) {
                                    append("${message.tokenCount} ток.")
                                }
                                if (message.versionCount > 1) {
                                    if (isNotEmpty()) append(" ")
                                    append("${message.versionIndex}/${message.versionCount}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.72f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (canEdit) {
                                IconButton(onClick = openEdit) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                                }
                            }
                            IconButton(onClick = requestDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                            }
                            if (copyText.isNotBlank()) {
                                IconButton(onClick = copyMessage) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать")
                                }
                            }
                            if (canRegenerate) {
                                IconButton(onClick = hapticRegenerate) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Сгенерировать заново")
                                }
                            }
                            if (!isPending && message.versionCount > 1) {
                                IconButton(onClick = hapticPreviousVersion) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Предыдущая версия")
                                }
                                IconButton(onClick = hapticNextVersion) {
                                    Icon(Icons.Filled.ChevronRight, contentDescription = "Следующая версия")
                                }
                            }
                            if (canResend) {
                                IconButton(onClick = hapticResend) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Отправить снова")
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
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(
                    onClick = saveEdit,
                    enabled = editedText.text.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = cancelEdit) {
                    Text("Отмена")
                }
            }
        )
    }

    if (isDeleteConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmationOpen = false },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено безвозвратно.") },
            confirmButton = {
                TextButton(
                    onClick = confirmDelete
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = cancelDelete) {
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
    val copyReasoning = rememberHapticClick(onCopy)
    val toggleReasoning = rememberHapticClick(UiHapticSignal.Toggle, onToggle)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Рассуждение",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.78f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = copyReasoning) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Копировать рассуждение",
                        tint = textColor.copy(alpha = 0.78f)
                    )
                }
                IconButton(onClick = toggleReasoning) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Свернуть рассуждение" else "Показать рассуждение",
                        tint = textColor.copy(alpha = 0.78f)
                    )
                }
            }
            if (isExpanded) {
                MarkdownText(
                    markdown = reasoning,
                    color = textColor.copy(alpha = 0.78f)
                )
            }
        }
    }
}
