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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.ui.components.DropdownSelector
import com.adam.xai_client.ui.components.TransientSnackbar

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
    onDeleteChat: () -> Unit,
    onEditFromMessage: (Long) -> Unit,
    onClearEditSource: () -> Unit,
    onGenerate: () -> Unit,
    onSave: (Long) -> Unit,
    onStoragePermissionDenied: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingSaveMessageId by remember { mutableLongStateOf(0L) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ImageGen чаты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Default.Add, contentDescription = "Новый image-чат")
                    }
                    IconButton(
                        onClick = onDeleteChat,
                        enabled = state.selectedChatId != null
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить image-чат")
                    }
                }
            )
        },
        bottomBar = {
            ImagePromptBar(
                state = state,
                onPromptChange = onPromptChange,
                onSourceImageUrlChange = onSourceImageUrlChange,
                onClearEditSource = onClearEditSource,
                onGenerate = onGenerate
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ImageChatControls(
                state = state,
                onModelSelected = onModelSelected,
                onChatSelected = onChatSelected,
                onAspectRatioChange = onAspectRatioChange,
                onResolutionChange = onResolutionChange
            )

            if (state.imageModels.isEmpty()) {
                EmptyHint("Включите модель с image или imagine в названии на странице моделей.")
            } else if (state.messages.isEmpty()) {
                EmptyHint("Опишите изображение ниже. После генерации чат сохранится здесь.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.messages,
                        key = { it.id }
                    ) { message ->
                        ImageMessageCard(
                            message = message,
                            isSaving = state.isSavingMessageId == message.id,
                            onEdit = { onEditFromMessage(message.id) },
                            onSave = {
                                if (requiresWritePermission(context)) {
                                    pendingSaveMessageId = message.id
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    onSave(message.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageChatControls(
    state: ImageGenerationUiState,
    onModelSelected: (String) -> Unit,
    onChatSelected: (Long?) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownSelector(
            label = "Модель",
            options = state.imageModels,
            selectedOption = state.imageModels.firstOrNull { it.id == state.selectedModelId },
            optionLabel = { it.name.ifBlank { it.id } },
            onOptionSelected = { onModelSelected(it.id) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DropdownSelector(
                label = "Чат",
                options = listOf<ImageChat?>(null) + state.chats,
                selectedOption = state.chats.firstOrNull { it.id == state.selectedChatId },
                optionLabel = { it?.title ?: "Новый image-чат" },
                onOptionSelected = { onChatSelected(it?.id) },
                modifier = Modifier.weight(1f)
            )
            DropdownSelector(
                label = "Формат",
                options = aspectRatios,
                selectedOption = state.aspectRatio,
                optionLabel = { it },
                onOptionSelected = onAspectRatioChange,
                modifier = Modifier.weight(1f)
            )
        }

        DropdownSelector(
            label = "Разрешение",
            options = resolutions,
            selectedOption = state.resolution,
            optionLabel = { it },
            onOptionSelected = onResolutionChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ImagePromptBar(
    state: ImageGenerationUiState,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onClearEditSource: () -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onGenerate,
                enabled = !state.isGenerating &&
                    state.prompt.isNotBlank() &&
                    state.selectedModelId != null
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.padding(3.dp))
                Text("Отправить")
            }
        }
    }
}

@Composable
private fun ImageMessageCard(
    message: ImageChatMessage,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onSave: () -> Unit
) {
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
        }
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
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
