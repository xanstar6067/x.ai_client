package com.adam.xai_client.ui.images

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.ui.components.DropdownSelector
import com.adam.xai_client.ui.components.TransientSnackbar
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    state: ImageGenerationUiState,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onChatSelected: (Long?) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onImageSettingsOpenChange: (Boolean) -> Unit,
    onShowChatList: () -> Unit,
    onEditFromMessage: (Long) -> Unit,
    onUpdateUserMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onClearEditSource: () -> Unit,
    onGenerate: () -> Unit,
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
    var chatPendingDeletion by remember { mutableStateOf<ImageChat?>(null) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isChatOpen) "ImageGen чат" else "ImageGen чаты",
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
                        onClick = { onImageSettingsOpenChange(true) },
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
                    Icon(Icons.Default.Add, contentDescription = "Новый image-чат")
                }
            }
        }
    ) { padding ->
        if (!isChatOpen) {
            ImageChatList(
                state = state,
                padding = padding,
                onOpenChat = { onChatSelected(it) },
                onDeleteChat = { chatPendingDeletion = it }
            )
        } else {
            ImageChatContent(
                state = state,
                padding = padding,
                onPromptChange = onPromptChange,
                onSourceImageUrlChange = onSourceImageUrlChange,
                onModelSelected = onModelSelected,
                onImageSettingsOpenChange = onImageSettingsOpenChange,
                onEditFromMessage = onEditFromMessage,
                onUpdateUserMessage = onUpdateUserMessage,
                onDeleteMessage = onDeleteMessage,
                onCopyMessage = { text -> clipboardManager.setText(AnnotatedString(text)) },
                onClearEditSource = onClearEditSource,
                onGenerate = onGenerate,
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

    if (state.isImageSettingsOpen) {
        ImageSettingsDialog(
            aspectRatio = state.aspectRatio,
            resolution = state.resolution,
            onAspectRatioChange = onAspectRatioChange,
            onResolutionChange = onResolutionChange,
            onDismiss = { onImageSettingsOpenChange(false) }
        )
    }

    chatPendingDeletion?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatPendingDeletion = null },
            title = { Text("Удалить чат?") },
            text = {
                Text("Чат \"${chat.title}\" и все сгенерированные изображения будут удалены без восстановления.")
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
}

@Composable
private fun ImageChatList(
    state: ImageGenerationUiState,
    padding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    onDeleteChat: (ImageChat) -> Unit
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
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "ImageGen чатов пока нет",
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
                ImageChatCard(
                    chat = chat,
                    onClick = { onOpenChat(chat.id) },
                    onDelete = { onDeleteChat(chat) }
                )
            }
        }
    }
}

@Composable
private fun ImageChatCard(
    chat: ImageChat,
    onClick: () -> Unit,
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить чат")
            }
        }
    }
}

@Composable
private fun ImageChatContent(
    state: ImageGenerationUiState,
    padding: PaddingValues,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onImageSettingsOpenChange: (Boolean) -> Unit,
    onEditFromMessage: (Long) -> Unit,
    onUpdateUserMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onCopyMessage: (String) -> Unit,
    onClearEditSource: () -> Unit,
    onGenerate: () -> Unit,
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
        ImageChatControls(
            state = state,
            onModelSelected = onModelSelected
        )
        if (state.isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.imageModels.isEmpty()) {
            EmptyHint(
                text = "Включите модель с image или imagine в названии на странице моделей.",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else if (state.messages.isEmpty()) {
            EmptyHint(
                text = "Опишите изображение ниже. После генерации чат сохранится здесь.",
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
                    ImageMessageCard(
                        message = message,
                        isSaving = state.isSavingMessageId == message.id,
                        isGenerating = state.isGenerating,
                        onCopy = { onCopyMessage(message.content) },
                        onEdit = { onEditFromMessage(message.id) },
                        onUpdateUserMessage = { text -> onUpdateUserMessage(message.id, text) },
                        onDelete = { onDeleteMessage(message.id) },
                        onRegenerate = { onRegenerate(message.id) },
                        onPreviousVersion = { onSwitchMessageVersion(message.id, -1) },
                        onNextVersion = { onSwitchMessageVersion(message.id, 1) },
                        onSave = { onSaveRequested(message.id) }
                    )
                }
            }
        }
        ImagePromptBar(
            state = state,
            onPromptChange = onPromptChange,
            onSourceImageUrlChange = onSourceImageUrlChange,
            onClearEditSource = onClearEditSource,
            onGenerate = onGenerate
        )
    }
}

@Composable
private fun ImageChatControls(
    state: ImageGenerationUiState,
    onModelSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = if (state.imageModels.isEmpty()) "Нет image-моделей" else "Модель",
            options = state.imageModels,
            selectedOption = state.imageModels.firstOrNull { it.id == state.selectedModelId },
            optionLabel = { it.name.ifBlank { it.id } },
            onOptionSelected = { onModelSelected(it.id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ImageSettingsDialog(
    aspectRatio: String,
    resolution: String,
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
private fun ImagePromptBar(
    state: ImageGenerationUiState,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onClearEditSource: () -> Unit,
    onGenerate: () -> Unit
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
        state.editingMessageId?.let {
            AssistChip(
                onClick = onClearEditSource,
                label = { Text("Редактируется выбранная картинка") },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Сбросить")
                }
            )
        }
        OutlinedTextField(
            value = state.sourceImageUrl,
            onValueChange = onSourceImageUrlChange,
            enabled = state.editingMessageId == null,
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
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (!state.isGenerating &&
                            state.prompt.isNotBlank() &&
                            state.selectedModelId != null
                        ) {
                            generateAndHideKeyboard()
                        }
                    }
                ),
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = generateAndHideKeyboard,
                enabled = !state.isGenerating &&
                    state.prompt.isNotBlank() &&
                    state.selectedModelId != null,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun ImageMessageCard(
    message: ImageChatMessage,
    isSaving: Boolean,
    isGenerating: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onUpdateUserMessage: (String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onSave: () -> Unit
) {
    var isDeleteConfirmationOpen by remember(message.id) { mutableStateOf(false) }
    var isTextEditOpen by remember(message.id) { mutableStateOf(false) }
    var editedText by remember(message.id) { mutableStateOf(message.content) }

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
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (message.generatedImage == null) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            message.generatedImage?.let { image ->
                ImagePreview(image = image)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Редактировать")
                    }
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
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous version")
                    }
                    IconButton(
                        onClick = onNextVersion,
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next version")
                    }
                }
                if (message.content.isNotBlank()) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                }
                if (message.role == MessageRole.USER) {
                    IconButton(
                        onClick = {
                            editedText = message.content
                            isTextEditOpen = true
                        },
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                }
                if (message.role == MessageRole.ASSISTANT) {
                    IconButton(
                        onClick = onRegenerate,
                        enabled = !isGenerating
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                    }
                }
                IconButton(
                    onClick = { isDeleteConfirmationOpen = true },
                    enabled = !isGenerating
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
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
                    minLines = 3,
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateUserMessage(editedText)
                        isTextEditOpen = false
                    },
                    enabled = editedText.isNotBlank()
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
private fun ImagePreview(image: GeneratedImage) {
    val bitmap = remember(image) {
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Не удалось открыть изображение")
        }
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

private val aspectRatios = listOf(
    "auto",
    "1:1",
    "16:9",
    "9:16",
    "4:3",
    "3:4",
    "3:2",
    "2:3",
    "2:1",
    "1:2"
)

private val resolutions = listOf("1k", "2k")
