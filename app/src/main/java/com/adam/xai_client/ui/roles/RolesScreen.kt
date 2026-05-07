package com.adam.xai_client.ui.roles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.ui.components.TransientSnackbar

private data class RoleEditorState(
    val id: Long? = null,
    val name: String = "",
    val prompt: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesScreen(
    state: RolesUiState,
    onCreateRole: (String, String) -> Unit,
    onUpdateRole: (Long, String, String) -> Unit,
    onDeleteRole: (Long) -> Unit,
    onSetDefaultRole: (Long) -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var editorState by remember { mutableStateOf<RoleEditorState?>(null) }
    TransientSnackbar(
        message = state.error ?: state.message,
        snackbarHostState = snackbarHostState,
        onShown = onMessageShown
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Роли") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorState = RoleEditorState() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новая роль")
            }
        }
    ) { padding ->
        if (state.roles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Роли загружаются",
                    style = MaterialTheme.typography.titleMedium
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
                    items = state.roles,
                    key = { it.id }
                ) { role ->
                    RoleRow(
                        role = role,
                        onEdit = {
                            editorState = RoleEditorState(
                                id = role.id,
                                name = role.name,
                                prompt = role.prompt
                            )
                        },
                        onDelete = { onDeleteRole(role.id) },
                        onSetDefault = { onSetDefaultRole(role.id) }
                    )
                }
            }
        }
    }

    editorState?.let { current ->
        RoleEditorDialog(
            state = current,
            onStateChange = { editorState = it },
            onDismiss = { editorState = null },
            onConfirm = {
                val id = current.id
                if (id == null) {
                    onCreateRole(current.name, current.prompt)
                } else {
                    onUpdateRole(id, current.name, current.prompt)
                }
                editorState = null
            }
        )
    }
}

@Composable
private fun RoleRow(
    role: ModelRole,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = role.isDefault,
                    onClick = onSetDefault
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = role.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать роль")
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !role.isBuiltIn
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить роль")
                }
            }
            if (role.isBuiltIn) {
                AssistChip(
                    onClick = {},
                    label = { Text("Базовая") },
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RoleEditorDialog(
    state: RoleEditorState,
    onStateChange: (RoleEditorState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.id == null) "Новая роль" else "Редактировать роль")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onStateChange(state.copy(name = it)) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { onStateChange(state.copy(prompt = it)) },
                    label = { Text("Prompt") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
