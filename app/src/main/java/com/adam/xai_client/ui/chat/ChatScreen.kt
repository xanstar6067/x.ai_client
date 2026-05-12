package com.adam.xai_client.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.MessageAttachment
import com.adam.xai_client.domain.model.MessageAttachmentKind
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ModelLimits
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.domain.model.ReasoningEffort
import com.adam.xai_client.domain.model.XaiModelLimits
import com.adam.xai_client.ui.components.DropdownSelector
import com.adam.xai_client.ui.components.MessageBubble
import com.adam.xai_client.ui.components.SafeSnackbarHost
import com.adam.xai_client.ui.components.TransientSnackbar
import com.adam.xai_client.ui.haptics.StreamingResponseHaptics
import com.adam.xai_client.ui.haptics.UiHapticSignal
import com.adam.xai_client.ui.haptics.rememberHapticClick
import com.adam.xai_client.ui.haptics.rememberHapticValueChange
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onRoleSelected: (Long) -> Unit,
    onSend: () -> Unit,
    onStopSending: () -> Unit,
    onRegenerate: (Long) -> Unit,
    onResendMessage: (Long) -> Unit,
    onSwitchMessageVersion: (Long, Int) -> Unit,
    onUpdateMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onModelInfoOpenChange: (Boolean) -> Unit,
    onModelSettingsOpenChange: (Boolean) -> Unit,
    onMaxTokensChange: (Int?) -> Unit,
    onTemperatureChange: (Double?) -> Unit,
    onTopPChange: (Double?) -> Unit,
    onFrequencyPenaltyChange: (Double?) -> Unit,
    onPresencePenaltyChange: (Double?) -> Unit,
    onReasoningEffortChange: (ReasoningEffort?) -> Unit,
    onContextMessageLimitChange: (Int) -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onAttachmentSelected: (Uri, MessageAttachmentKind) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onResetModelSettings: () -> Unit,
    onBack: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val hapticBack = rememberHapticClick(onBack)
    val hapticModelInfoOpen = rememberHapticClick { onModelInfoOpenChange(true) }
    val hapticModelSettingsOpen = rememberHapticClick { onModelSettingsOpenChange(true) }
    var pendingAttachmentKind by remember { mutableStateOf(MessageAttachmentKind.IMAGE) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onAttachmentSelected(it, pendingAttachmentKind) }
    }
    TransientSnackbar(
        message = state.error,
        snackbarHostState = snackbarHostState,
        onShown = onErrorShown
    )
    StreamingResponseHaptics(
        messages = state.messages,
        isSending = state.isSending,
        enabled = state.streamingHapticsEnabled
    )

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val observedWebSearchState = remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.modelSettings.webSearchEnabled) {
        val previous = observedWebSearchState.value
        observedWebSearchState.value = state.modelSettings.webSearchEnabled
        if (previous != null && previous != state.modelSettings.webSearchEnabled) {
            snackbarHostState.showSnackbar(
                if (state.modelSettings.webSearchEnabled) "Веб-поиск включен" else "Веб-поиск отключен"
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SafeSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (state.chatId == null) "Новый чат" else "Чат",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TokenSummaryInline(
                            chatTokenCount = state.chatTokenCount,
                            cachedTokenCount = state.cachedTokenCount,
                            lastPromptTokenCount = state.lastPromptTokenCount,
                            lastCompletionTokenCount = state.lastCompletionTokenCount,
                            lastCachedTokenCount = state.lastCachedTokenCount,
                            lastReasoningTokenCount = state.lastReasoningTokenCount,
                            inputTokenCount = state.inputTokenCount
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = hapticBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = hapticModelInfoOpen,
                        enabled = state.selectedModelId != null
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "Лимиты модели")
                    }
                    IconButton(
                        onClick = hapticModelSettingsOpen,
                        enabled = state.selectedModelId != null
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки модели")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ChatSelectors(
                models = state.availableModels,
                roles = state.availableRoles,
                selectedModelId = state.selectedModelId,
                selectedRoleId = state.selectedRoleId,
                onModelSelected = onModelSelected,
                onRoleSelected = onRoleSelected
            )
            if (state.isSending) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Напишите первое сообщение",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.messages,
                        key = { it.id }
                    ) { message ->
                        val isLastMessage = message.id == state.messages.lastOrNull()?.id
                        MessageBubble(
                            message = message,
                            isPending = state.isSending &&
                                isLastMessage &&
                                message.role == MessageRole.ASSISTANT &&
                                message.content.isBlank() &&
                                message.reasoningContent.isNullOrBlank(),
                            canRegenerate = !state.isSending &&
                                message.role == MessageRole.ASSISTANT,
                            canResend = !state.isSending &&
                                message.role == MessageRole.USER,
                            canEdit = !state.isSending,
                            onCopy = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                            },
                            onRegenerate = { onRegenerate(message.id) },
                            onResend = { onResendMessage(message.id) },
                            onPreviousVersion = { onSwitchMessageVersion(message.id, -1) },
                            onNextVersion = { onSwitchMessageVersion(message.id, 1) },
                            onEdit = { text -> onUpdateMessage(message.id, text) },
                            onDelete = { onDeleteMessage(message.id) }
                        )
                    }
                }
            }
            ChatInput(
                inputText = state.inputText,
                inputTokenCount = state.inputTokenCount,
                attachments = state.pendingAttachments,
                supportsImageAttachments = state.supportsImageAttachments,
                supportsDocumentAttachments = state.supportsDocumentAttachments,
                webSearchEnabled = state.modelSettings.webSearchEnabled,
                isSending = state.isSending,
                onInputChange = onInputChange,
                onWebSearchEnabledChange = onWebSearchEnabledChange,
                onPickAttachment = { kind ->
                    pendingAttachmentKind = kind
                    attachmentPicker.launch(kind.openDocumentMimeTypes())
                },
                onRemoveAttachment = onRemoveAttachment,
                onSend = onSend,
                onStopSending = onStopSending
            )
        }
    }

    if (state.isModelInfoOpen) {
        ModelInfoDialog(
            modelId = state.selectedModelId,
            limits = state.selectedModelLimits,
            onDismiss = { onModelInfoOpenChange(false) }
        )
    }

    if (state.isModelSettingsOpen) {
        ModelSettingsDialog(
            modelId = state.selectedModelId,
            settings = state.modelSettings,
            limits = state.selectedModelLimits,
            onDismiss = { onModelSettingsOpenChange(false) },
            onMaxTokensChange = onMaxTokensChange,
            onTemperatureChange = onTemperatureChange,
            onTopPChange = onTopPChange,
            onFrequencyPenaltyChange = onFrequencyPenaltyChange,
            onPresencePenaltyChange = onPresencePenaltyChange,
            onReasoningEffortChange = onReasoningEffortChange,
            onContextMessageLimitChange = onContextMessageLimitChange,
            onReset = onResetModelSettings
        )
    }
}

@Composable
private fun TokenSummaryInline(
    chatTokenCount: Int,
    cachedTokenCount: Int,
    lastPromptTokenCount: Int,
    lastCompletionTokenCount: Int,
    lastCachedTokenCount: Int,
    lastReasoningTokenCount: Int,
    inputTokenCount: Int
) {
    val cachedText = if (cachedTokenCount > 0) " | Кэш $cachedTokenCount" else ""
    val lastUsageText = buildString {
        append("Запрос: кэш ")
        append(lastCachedTokenCount)
        append(" / вход ")
        append(lastPromptTokenCount)
        append(" | ответ ")
        append(lastCompletionTokenCount)
        if (lastReasoningTokenCount > 0) {
            append(" | разм. ")
            append(lastReasoningTokenCount)
        }
    }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy((-2).dp)
    ) {
        Text(
            text = "Ввод $inputTokenCount | Всего ${chatTokenCount + inputTokenCount}$cachedText",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = lastUsageText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatSelectors(
    models: List<AiModel>,
    roles: List<ModelRole>,
    selectedModelId: String?,
    selectedRoleId: Long?,
    onModelSelected: (String) -> Unit,
    onRoleSelected: (Long) -> Unit
) {
    val selectedModel = models.firstOrNull { it.id == selectedModelId }
    val selectedRole = roles.firstOrNull { it.id == selectedRoleId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = if (models.isEmpty()) "Нет включенных моделей" else "Модель",
            options = models,
            selectedOption = selectedModel,
            optionLabel = { it.name },
            onOptionSelected = { onModelSelected(it.id) },
            modifier = Modifier.weight(1f)
        )
        DropdownSelector(
            label = if (roles.isEmpty()) "Нет ролей" else "Роль",
            options = roles,
            selectedOption = selectedRole,
            optionLabel = { it.name },
            onOptionSelected = { onRoleSelected(it.id) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChatInput(
    inputText: String,
    inputTokenCount: Int,
    attachments: List<MessageAttachment>,
    supportsImageAttachments: Boolean,
    supportsDocumentAttachments: Boolean,
    webSearchEnabled: Boolean,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onPickAttachment: (MessageAttachmentKind) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
    onStopSending: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val toggleWebSearch = rememberHapticClick(UiHapticSignal.Toggle) {
        onWebSearchEnabledChange(!webSearchEnabled)
    }
    val sendAndHideKeyboard = {
        keyboardController?.hide()
        onSend()
    }
    val hapticSendOrStop = rememberHapticClick(
        if (isSending) UiHapticSignal.Destructive else UiHapticSignal.Confirm,
        if (isSending) onStopSending else sendAndHideKeyboard
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
    ) {
        if (attachments.isNotEmpty()) {
            AttachmentChips(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                label = { Text("Сообщение") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                minLines = 1,
                maxLines = 6,
                leadingIcon = {
                    AttachmentMenuButton(
                        enabled = !isSending && (supportsImageAttachments || supportsDocumentAttachments),
                        supportsImageAttachments = supportsImageAttachments,
                        supportsDocumentAttachments = supportsDocumentAttachments,
                        onPickAttachment = onPickAttachment
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = toggleWebSearch,
                        enabled = !isSending
                    ) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = if (webSearchEnabled) {
                                "РћС‚РєР»СЋС‡РёС‚СЊ РІРµР±-РїРѕРёСЃРє"
                            } else {
                                "Р’РєР»СЋС‡РёС‚СЊ РІРµР±-РїРѕРёСЃРє"
                            },
                            tint = if (webSearchEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            )
            IconButton(
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                onClick = hapticSendOrStop,
                enabled = isSending || inputText.isNotBlank() || attachments.isNotEmpty()
            ) {
                if (isSending) {
                    Icon(Icons.Filled.Stop, contentDescription = "РћСЃС‚Р°РЅРѕРІРёС‚СЊ")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "РћС‚РїСЂР°РІРёС‚СЊ")
                }
            }
        }
    }
}

@Composable
private fun AttachmentMenuButton(
    enabled: Boolean,
    supportsImageAttachments: Boolean,
    supportsDocumentAttachments: Boolean,
    onPickAttachment: (MessageAttachmentKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticOpen = rememberHapticClick { expanded = true }
    IconButton(
        onClick = hapticOpen,
        enabled = enabled
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Прикрепить файл")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        if (supportsImageAttachments) {
            DropdownMenuItem(
                text = { Text("Изображение") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                onClick = {
                    expanded = false
                    onPickAttachment(MessageAttachmentKind.IMAGE)
                }
            )
        }
        if (supportsDocumentAttachments) {
            DropdownMenuItem(
                text = { Text("Документ") },
                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                onClick = {
                    expanded = false
                    onPickAttachment(MessageAttachmentKind.DOCUMENT)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentChips(
    attachments: List<MessageAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEachIndexed { index, attachment ->
            androidx.compose.material3.InputChip(
                selected = false,
                onClick = {},
                label = {
                    Text(
                        text = attachment.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (attachment.kind == MessageAttachmentKind.IMAGE) {
                            Icons.Filled.Image
                        } else {
                            Icons.Filled.Description
                        },
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onRemoveAttachment(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать вложение")
                    }
                }
            )
        }
    }
}

private fun MessageAttachmentKind.openDocumentMimeTypes(): Array<String> {
    return when (this) {
        MessageAttachmentKind.IMAGE -> arrayOf("image/jpeg", "image/png")
        MessageAttachmentKind.DOCUMENT -> arrayOf(
            "text/*",
            "application/pdf",
            "application/json",
            "application/xml",
            "application/octet-stream"
        )
        MessageAttachmentKind.VIDEO -> arrayOf("video/*")
    }
}

@Composable
private fun ChatInput(
    inputText: String,
    inputTokenCount: Int,
    webSearchEnabled: Boolean,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onStopSending: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val toggleWebSearch = rememberHapticClick(UiHapticSignal.Toggle) {
        onWebSearchEnabledChange(!webSearchEnabled)
    }
    val sendAndHideKeyboard = {
        keyboardController?.hide()
        onSend()
    }
    val hapticSendOrStop = rememberHapticClick(
        if (isSending) UiHapticSignal.Destructive else UiHapticSignal.Confirm,
        if (isSending) onStopSending else sendAndHideKeyboard
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            label = { Text("Сообщение") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default
            ),
            minLines = 1,
            maxLines = 6,
            trailingIcon = {
                IconButton(
                    onClick = toggleWebSearch,
                    enabled = !isSending
                ) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = if (webSearchEnabled) {
                            "Отключить веб-поиск"
                        } else {
                            "Включить веб-поиск"
                        },
                        tint = if (webSearchEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        )
        IconButton(
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            onClick = hapticSendOrStop,
            enabled = isSending || inputText.isNotBlank()
        ) {
            if (isSending) {
                Icon(Icons.Filled.Stop, contentDescription = "Остановить")
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun ModelInfoDialog(
    modelId: String?,
    limits: ModelLimits?,
    onDismiss: () -> Unit
) {
    val hapticDismiss = rememberHapticClick(onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(modelId ?: "Модель") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (limits == null) {
                    Text("Для этой модели в приложении пока нет проверенного описания лимитов.")
                    Text(
                        text = "Источник для проверки: ${XaiModelLimits.sourceForRateLimitDetails()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    return@Column
                }
                LimitRow("Контекст", limits.contextWindowTokens?.let { "$it токенов" } ?: "Нет в ответе API")
                LimitRow("Лимит запросов", limits.publicRateLimit)
                LimitRow("Цена ввода", "${limits.inputPricePerMillion} / 1 млн токенов")
                limits.cachedInputPricePerMillion?.let {
                    LimitRow("Цена кэшированного ввода", "$it / 1 млн токенов")
                }
                LimitRow("Цена вывода", "${limits.outputPricePerMillion} / 1 млн токенов")
                limits.imagePrice?.let {
                    LimitRow("Цена изображения", "$it / изображение")
                }
                HorizontalDivider()
                limits.notes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "Источник: ${limits.sourceLabel} (${limits.sourceUrl})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = hapticDismiss) {
                Text("ОК")
            }
        }
    )
}

@Composable
private fun LimitRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModelSettingsDialog(
    modelId: String?,
    settings: ChatModelSettings,
    limits: ModelLimits?,
    onDismiss: () -> Unit,
    onMaxTokensChange: (Int?) -> Unit,
    onTemperatureChange: (Double?) -> Unit,
    onTopPChange: (Double?) -> Unit,
    onFrequencyPenaltyChange: (Double?) -> Unit,
    onPresencePenaltyChange: (Double?) -> Unit,
    onReasoningEffortChange: (ReasoningEffort?) -> Unit,
    onContextMessageLimitChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    val hapticDismiss = rememberHapticClick(onDismiss)
    val hapticReset = rememberHapticClick(UiHapticSignal.Destructive, onReset)
    val hapticReasoningReset = rememberHapticClick(UiHapticSignal.Destructive) {
        onReasoningEffortChange(null)
    }
    val maxContext = limits?.contextWindowTokens ?: 131_072
    val normalizedModelId = modelId.orEmpty().lowercase()
    val isGrok420MultiAgent = normalizedModelId.startsWith("grok-4.20-multi-agent")
    val supportsReasoningEffort = normalizedModelId.startsWith("grok-3-mini") || isGrok420MultiAgent
    val reasoningEffortOptions = listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки модели") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = modelId.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ContextMessageLimitSetting(
                    value = settings.contextMessageLimit,
                    onValueChange = onContextMessageLimitChange
                )
                if (isGrok420MultiAgent) {
                    DropdownSelector(
                        label = "Количество агентов",
                        options = reasoningEffortOptions,
                        selectedOption = settings.reasoningEffort,
                        optionLabel = { effort ->
                            when {
                                !isGrok420MultiAgent -> effort.label
                                effort == ReasoningEffort.LOW || effort == ReasoningEffort.MEDIUM -> "4 агента"
                                else -> "16 агентов"
                            }
                        },
                        onOptionSelected = onReasoningEffortChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = hapticReasoningReset) {
                        Text(
                            if (isGrok420MultiAgent) {
                                "Сбросить выбор агентов"
                            } else {
                                "Сбросить глубину рассуждения"
                            }
                        )
                    }
                }
                if (isGrok420MultiAgent) {
                    Text(
                        text = "xAI сопоставляет 4 агентов с reasoning.effort=low/medium, а 16 агентов - с high/xhigh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Максимум токенов ответа не поддерживается для multi-agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    NumericTextSetting(
                        label = "Максимум токенов ответа",
                        value = settings.maxTokens,
                        placeholder = "По умолчанию API",
                        onValueChange = onMaxTokensChange,
                        maxValue = maxContext
                    )
                }
                SliderSetting(
                    label = "Температура",
                    value = settings.temperature,
                    defaultValue = 1.0,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = onTemperatureChange
                )
                SliderSetting(
                    label = "Top P",
                    value = settings.topP,
                    defaultValue = 1.0,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = onTopPChange
                )
                if (!isGrok420MultiAgent) {
                    SliderSetting(
                        label = "Штраф частоты",
                        value = settings.frequencyPenalty,
                        defaultValue = 0.0,
                        valueRange = -2f..2f,
                        steps = 39,
                        onValueChange = onFrequencyPenaltyChange
                    )
                    SliderSetting(
                        label = "Штраф присутствия",
                        value = settings.presencePenalty,
                        defaultValue = 0.0,
                        valueRange = -2f..2f,
                        steps = 39,
                        onValueChange = onPresencePenaltyChange
                    )
                }
                if (supportsReasoningEffort && !isGrok420MultiAgent) {
                    DropdownSelector(
                        label = if (isGrok420MultiAgent) "Настройка агентов" else "Глубина рассуждения",
                        options = reasoningEffortOptions,
                        selectedOption = settings.reasoningEffort,
                        optionLabel = { effort ->
                            when {
                                !isGrok420MultiAgent -> effort.label
                                effort == ReasoningEffort.LOW || effort == ReasoningEffort.MEDIUM -> "${effort.label} - 4 агента"
                                else -> "${effort.label} - 16 агентов"
                            }
                        },
                        onOptionSelected = onReasoningEffortChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = hapticReasoningReset) {
                        Text("Сбросить глубину рассуждения")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = hapticDismiss) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = hapticReset) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("Сброс")
            }
        }
    )
}

@Composable
private fun ContextMessageLimitSetting(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val hapticValueChange = rememberHapticValueChange<Float>(UiHapticSignal.Selection) { raw ->
        onValueChange(raw.roundToInt().coerceIn(0, 99))
    }
    Column {
        Text("Сообщений в контексте", style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (value == 0) "Без ограничения" else "Последние $value сообщений",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = hapticValueChange,
            valueRange = 0f..99f,
            steps = 98
        )
    }
}

@Composable
private fun NumericTextSetting(
    label: String,
    value: Int?,
    placeholder: String,
    onValueChange: (Int?) -> Unit,
    maxValue: Int
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text ->
            val digits = text.filter { it.isDigit() }
            onValueChange(digits.toIntOrNull()?.coerceIn(1, maxValue))
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Double?,
    defaultValue: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Double?) -> Unit
) {
    val enabled = value != null
    val sliderValue = (value ?: defaultValue).toFloat()
    val hapticEnabledChange = rememberHapticValueChange<Boolean> { checked ->
        onValueChange(if (checked) defaultValue else null)
    }
    val hapticSliderChange = rememberHapticValueChange<Float>(UiHapticSignal.Selection) { raw ->
        val rounded = (raw * 100).roundToInt() / 100.0
        onValueChange(rounded)
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = enabled,
                onCheckedChange = hapticEnabledChange
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = value?.let { ((it * 100).roundToInt() / 100.0).toString() }
                        ?: "По умолчанию API",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = hapticSliderChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}
