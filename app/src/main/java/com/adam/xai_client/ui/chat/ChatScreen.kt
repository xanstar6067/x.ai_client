package com.adam.xai_client.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.ui.components.DropdownSelector
import com.adam.xai_client.ui.components.MessageBubble
import com.adam.xai_client.ui.components.TransientSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onRoleSelected: (Long) -> Unit,
    onSend: () -> Unit,
    onRegenerate: () -> Unit,
    onResendMessage: (Long) -> Unit,
    onBack: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    TransientSnackbar(
        message = state.error,
        snackbarHostState = snackbarHostState,
        onShown = onErrorShown
    )

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.chatId == null) "Новый чат" else "Чат",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
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
                                isLastMessage &&
                                message.role == MessageRole.ASSISTANT,
                            canResend = !state.isSending &&
                                message.role == MessageRole.USER,
                            onCopy = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                            },
                            onRegenerate = onRegenerate,
                            onResend = { onResendMessage(message.id) }
                        )
                    }
                }
            }
            ChatInput(
                inputText = state.inputText,
                isSending = state.isSending,
                onInputChange = onInputChange,
                onSend = onSend
            )
        }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = if (models.isEmpty()) "Нет включенных моделей" else "Модель",
            options = models,
            selectedOption = selectedModel,
            optionLabel = { it.name },
            onOptionSelected = { onModelSelected(it.id) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownSelector(
            label = if (roles.isEmpty()) "Нет ролей" else "Роль",
            options = roles,
            selectedOption = selectedRole,
            optionLabel = { it.name },
            onOptionSelected = { onRoleSelected(it.id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChatInput(
    inputText: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            label = { Text("Сообщение") },
            minLines = 1,
            maxLines = 6
        )
        IconButton(
            onClick = onSend,
            enabled = !isSending && inputText.isNotBlank(),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
        }
    }
}
