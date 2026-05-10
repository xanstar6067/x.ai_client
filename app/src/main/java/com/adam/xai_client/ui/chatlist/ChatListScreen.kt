package com.adam.xai_client.ui.chatlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.Chat
import com.adam.xai_client.ui.components.SafeSnackbarHost
import com.adam.xai_client.ui.components.TransientSnackbar
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    state: ChatListUiState,
    onOpenChat: (Long) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onDuplicateChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenRoles: () -> Unit,
    onOpenImages: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenBackups: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var chatPendingDeletion by remember { mutableStateOf<Chat?>(null) }
    var chatPendingDuplication by remember { mutableStateOf<Chat?>(null) }
    TransientSnackbar(
        message = state.error,
        snackbarHostState = snackbarHostState,
        onShown = onErrorShown
    )

    Scaffold(
        snackbarHost = { SafeSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("xAI Chat") },
                actions = {
                    IconButton(onClick = onOpenImages) {
                        Icon(Icons.Default.Image, contentDescription = "Изображения")
                    }
                    IconButton(onClick = onOpenVideos) {
                        Icon(Icons.Default.Movie, contentDescription = "Видео")
                    }
                    IconButton(onClick = onOpenModels) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Модели")
                    }
                    IconButton(onClick = onOpenRoles) {
                        Icon(Icons.Default.Psychology, contentDescription = "Роли")
                    }
                    IconButton(onClick = onOpenBackups) {
                        Icon(Icons.Default.Backup, contentDescription = "Резервное копирование и восстановление")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
        }
    ) { padding ->
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
                    text = "Чатов пока нет",
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
                    ChatCard(
                        chat = chat,
                        onClick = { onOpenChat(chat.id) },
                        onDuplicate = { chatPendingDuplication = chat },
                        onDelete = { chatPendingDeletion = chat }
                    )
                }
            }
        }
    }

    chatPendingDeletion?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatPendingDeletion = null },
            title = { Text("Удалить чат?") },
            text = {
                Text("Чат \"${chat.title}\" и вся история сообщений будут удалены без восстановления.")
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
            text = { Text("Будет создана копия чата \"${chat.title}\" со всеми сообщениями и настройками.") },
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
private fun ChatCard(
    chat: Chat,
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
