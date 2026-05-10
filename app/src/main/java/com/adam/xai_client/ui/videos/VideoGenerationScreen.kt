package com.adam.xai_client.ui.videos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.adam.xai_client.domain.model.GeneratedVideo
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.VideoChat
import com.adam.xai_client.domain.model.VideoChatMessage
import com.adam.xai_client.ui.components.DropdownSelector
import com.adam.xai_client.ui.components.ModelInfoDialog
import com.adam.xai_client.ui.components.SafeSnackbarHost
import com.adam.xai_client.ui.components.TransientSnackbar
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGenerationScreen(
    state: VideoGenerationUiState,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onChatSelected: (Long?) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onDuplicateChat: (Long) -> Unit,
    onVideoSettingsOpenChange: (Boolean) -> Unit,
    onModelInfoOpenChange: (Boolean) -> Unit,
    onShowChatList: () -> Unit,
    onUpdateUserMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onGenerate: () -> Unit,
    onStopGeneration: () -> Unit,
    onGenerateFromMessage: (Long) -> Unit,
    onRegenerate: (Long) -> Unit,
    onSwitchMessageVersion: (Long, Int) -> Unit,
    onSave: (Long) -> Unit,
    onStoragePermissionDenied: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var pendingSaveMessageId by remember { mutableLongStateOf(0L) }
    var chatPendingDeletion by remember { mutableStateOf<VideoChat?>(null) }
    var chatPendingDuplication by remember { mutableStateOf<VideoChat?>(null) }
    val isChatOpen = state.selectedChatId != null || state.isNewChatMode
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val messageId = pendingSaveMessageId
        if (granted && messageId != 0L) {
            onSave(messageId)
        } else if (!granted) {
            onStoragePermissionDenied()
        }
        pendingSaveMessageId = 0L
    }

    TransientSnackbar(
        message = state.error ?: state.message,
        snackbarHostState = snackbarHostState,
        onShown = onMessageShown
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SafeSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isChatOpen) "VideoGen чат" else "VideoGen чаты",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (isChatOpen) onShowChatList else onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onModelInfoOpenChange(true) },
                        enabled = isChatOpen && state.selectedModelId != null
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "Лимиты модели")
                    }
                    IconButton(
                        onClick = { onVideoSettingsOpenChange(true) },
                        enabled = isChatOpen && state.selectedModelId != null
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки генерации")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isChatOpen) {
                FloatingActionButton(
                    onClick = onNewChat,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Новый видеочат")
                }
            }
        }
    ) { padding ->
        if (!isChatOpen) {
            VideoChatList(
                state = state,
                padding = padding,
                onOpenChat = { onChatSelected(it) },
                onDuplicateChat = { chatPendingDuplication = it },
                onDeleteChat = { chatPendingDeletion = it }
            )
        } else {
            VideoChatContent(
                state = state,
                padding = padding,
                onPromptChange = onPromptChange,
                onSourceImageUrlChange = onSourceImageUrlChange,
                onModelSelected = onModelSelected,
                onUpdateUserMessage = onUpdateUserMessage,
                onDeleteMessage = onDeleteMessage,
                onCopyMessage = { text -> clipboardManager.setText(AnnotatedString(text)) },
                onGenerate = onGenerate,
                onStopGeneration = onStopGeneration,
                onGenerateFromMessage = onGenerateFromMessage,
                onRegenerate = onRegenerate,
                onSwitchMessageVersion = onSwitchMessageVersion,
                onSaveRequested = { messageId ->
                    if (requiresWritePermission(context)) {
                        pendingSaveMessageId = messageId
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        onSave(messageId)
                    }
                }
            )
        }
    }

    if (state.isVideoSettingsOpen) {
        VideoSettingsDialog(
            durationSeconds = state.durationSeconds,
            aspectRatio = state.aspectRatio,
            resolution = state.resolution,
            onDurationChange = onDurationChange,
            onAspectRatioChange = onAspectRatioChange,
            onResolutionChange = onResolutionChange,
            onDismiss = { onVideoSettingsOpenChange(false) }
        )
    }

    if (state.isModelInfoOpen) {
        ModelInfoDialog(
            modelId = state.selectedModelId,
            limits = state.selectedModelLimits,
            onDismiss = { onModelInfoOpenChange(false) }
        )
    }

    chatPendingDeletion?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatPendingDeletion = null },
            title = { Text("Удалить чат?") },
            text = {
                Text("Чат \"${chat.title}\" и все сгенерированные видео будут удалены без восстановления.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(chat.id)
                        chatPendingDeletion = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatPendingDeletion = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    chatPendingDuplication?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatPendingDuplication = null },
            title = { Text("Скопировать чат?") },
            text = { Text("Будет создана копия чата \"${chat.title}\" со всеми сообщениями.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDuplicateChat(chat.id)
                        chatPendingDuplication = null
                    }
                ) {
                    Text("Скопировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatPendingDuplication = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun VideoChatList(
    state: VideoGenerationUiState,
    padding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    onDuplicateChat: (VideoChat) -> Unit,
    onDeleteChat: (VideoChat) -> Unit
) {
    if (state.chats.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "VideoGen чатов пока нет",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = state.chats,
                key = { it.id }
            ) { chat ->
                VideoChatCard(
                    chat = chat,
                    onClick = { onOpenChat(chat.id) },
                    onDuplicate = { onDuplicateChat(chat) },
                    onDelete = { onDeleteChat(chat) }
                )
            }
        }
    }
}

@Composable
private fun VideoChatCard(
    chat: VideoChat,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(chat.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(modifier = Modifier.padding(start = 8.dp))
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать чат")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить чат")
            }
        }
    }
}

@Composable
private fun VideoChatContent(
    state: VideoGenerationUiState,
    padding: PaddingValues,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onUpdateUserMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onCopyMessage: (String) -> Unit,
    onGenerate: () -> Unit,
    onStopGeneration: () -> Unit,
    onGenerateFromMessage: (Long) -> Unit,
    onRegenerate: (Long) -> Unit,
    onSwitchMessageVersion: (Long, Int) -> Unit,
    onSaveRequested: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VideoChatControls(
            state = state,
            onModelSelected = onModelSelected
        )
        if (state.isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = buildString {
                    append("Генерация видео")
                    state.generationProgress?.let { append(" $it%") }
                    state.generationRequestId?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (state.videoModels.isEmpty()) {
            EmptyHint(
                text = "Включите модель с video или imagine в названии на странице моделей.",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else if (state.messages.isEmpty()) {
            EmptyHint(
                text = "Опишите видео ниже. После генерации чат сохранится здесь.",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.messages,
                    key = { it.id }
                ) { message ->
                    VideoMessageCard(
                        message = message,
                        isSaving = state.isSavingMessageId == message.id,
                        isGenerating = state.isGenerating,
                        onCopy = { onCopyMessage(message.content) },
                        onUpdateUserMessage = { text -> onUpdateUserMessage(message.id, text) },
                        onDelete = { onDeleteMessage(message.id) },
                        onGenerateFromUser = { onGenerateFromMessage(message.id) },
                        onRegenerate = { onRegenerate(message.id) },
                        onPreviousVersion = { onSwitchMessageVersion(message.id, -1) },
                        onNextVersion = { onSwitchMessageVersion(message.id, 1) },
                        onSave = { onSaveRequested(message.id) }
                    )
                }
            }
        }
        VideoPromptBar(
            state = state,
            onPromptChange = onPromptChange,
            onSourceImageUrlChange = onSourceImageUrlChange,
            onGenerate = onGenerate,
            onStopGeneration = onStopGeneration
        )
    }
}

@Composable
private fun VideoChatControls(
    state: VideoGenerationUiState,
    onModelSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = if (state.videoModels.isEmpty()) "Нет video-моделей" else "Модель",
            options = state.videoModels,
            selectedOption = state.videoModels.firstOrNull { it.id == state.selectedModelId },
            optionLabel = { model -> model.name.ifBlank { model.id } },
            onOptionSelected = { onModelSelected(it.id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VideoSettingsDialog(
    durationSeconds: Int,
    aspectRatio: String,
    resolution: String,
    onDurationChange: (Int) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки генерации") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DropdownSelector(
                    label = "Длительность",
                    options = durationOptions,
                    selectedOption = durationSeconds,
                    optionLabel = { "$it с" },
                    onOptionSelected = onDurationChange,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Формат",
                    options = aspectRatios,
                    selectedOption = aspectRatio,
                    optionLabel = { it },
                    onOptionSelected = onAspectRatioChange,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Разрешение",
                    options = resolutions,
                    selectedOption = resolution,
                    optionLabel = { it },
                    onOptionSelected = onResolutionChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@Composable
private fun VideoPromptBar(
    state: VideoGenerationUiState,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onStopGeneration: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val generateAndHideKeyboard = {
        keyboardController?.hide()
        onGenerate()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.sourceImageUrl,
            onValueChange = onSourceImageUrlChange,
            label = { Text("URL исходной картинки") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = { Text("Запрос") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = if (state.isGenerating) onStopGeneration else generateAndHideKeyboard,
                enabled = state.isGenerating ||
                    (state.prompt.isNotBlank() && state.selectedModelId != null),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                if (state.isGenerating) {
                    Icon(Icons.Filled.Stop, contentDescription = "Остановить")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
private fun VideoMessageCard(
    message: VideoChatMessage,
    isSaving: Boolean,
    isGenerating: Boolean,
    onCopy: () -> Unit,
    onUpdateUserMessage: (String) -> Unit,
    onDelete: () -> Unit,
    onGenerateFromUser: () -> Unit,
    onRegenerate: () -> Unit,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onSave: () -> Unit
) {
    var isDeleteConfirmationOpen by remember(message.id) { mutableStateOf(false) }
    var isTextEditOpen by remember(message.id) { mutableStateOf(false) }
    var editedText by rememberSaveable(message.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = message.content,
                selection = TextRange(message.content.length)
            )
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (message.role == MessageRole.USER) "Вы" else "xAI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (message.generatedVideo == null) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            message.sourceImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Text(
                    text = "Исходная картинка: $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            message.generatedVideo?.let { video ->
                VideoPreview(video = video, aspectRatio = message.aspectRatio)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onSave,
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(18.dp)
                                    .aspectRatio(1f),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null)
                        }
                        Spacer(Modifier.padding(3.dp))
                        Text("Сохранить")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.role == MessageRole.USER) {
                    IconButton(
                        onClick = {
                            editedText = TextFieldValue(
                                text = message.content,
                                selection = TextRange(message.content.length)
                            )
                            isTextEditOpen = true
                        },
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                }
                IconButton(
                    onClick = { isDeleteConfirmationOpen = true },
                    enabled = !isGenerating
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
                if (message.content.isNotBlank()) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                }
                if (message.role == MessageRole.ASSISTANT) {
                    IconButton(
                        onClick = onRegenerate,
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Сгенерировать заново")
                    }
                }
                if (message.versionCount > 1) {
                    Text(
                        text = "${message.versionIndex}/${message.versionCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onPreviousVersion,
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущая версия")
                    }
                    IconButton(
                        onClick = onNextVersion,
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Следующая версия")
                    }
                }
                if (message.role == MessageRole.USER) {
                    IconButton(
                        onClick = onGenerateFromUser,
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Отправить снова")
                    }
                }
            }
        }
    }

    if (isDeleteConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmationOpen = false },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено без восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmationOpen = false
                        onDelete()
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmationOpen = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (isTextEditOpen) {
        AlertDialog(
            onDismissRequest = { isTextEditOpen = false },
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
                    onClick = {
                        onUpdateUserMessage(editedText.text)
                        isTextEditOpen = false
                    },
                    enabled = editedText.text.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { isTextEditOpen = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun VideoPreview(video: GeneratedVideo, aspectRatio: String?) {
    val file = remember(video.filePath) { File(video.filePath) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.toPreviewRatio()),
        contentAlignment = Alignment.Center
    ) {
        if (!file.exists()) {
            Text("Не удалось открыть видео")
            return@Box
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VideoView(context).apply {
                    val controller = MediaController(context)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { player ->
                        player.isLooping = false
                        seekTo(1)
                    }
                }
            },
            update = { view ->
                if (view.tag != video.filePath) {
                    view.tag = video.filePath
                    view.setVideoPath(file.absolutePath)
                    view.seekTo(1)
                }
            }
        )
    }
}

@Composable
private fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun requiresWritePermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
}

private fun String?.toPreviewRatio(): Float {
    val parts = this?.split(":").orEmpty()
    val width = parts.getOrNull(0)?.toFloatOrNull()
    val height = parts.getOrNull(1)?.toFloatOrNull()
    return if (width != null && height != null && height > 0f) {
        width / height
    } else {
        16f / 9f
    }
}

private val durationOptions = (1..15).toList()

private val aspectRatios = listOf(
    "1:1",
    "16:9",
    "9:16",
    "4:3",
    "3:4",
    "3:2",
    "2:3"
)

private val resolutions = listOf("480p", "720p")
