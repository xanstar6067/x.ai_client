package com.adam.xai_client.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.BackupRepository
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class BackupViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun exportBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, error = null) }
            runCatching { backupRepository.exportBackup() }
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = "Резервная копия сохранена в Downloads/xAI Chat Backups.",
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isBusy = false, error = throwable.toUserMessage())
                    }
                }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, error = null) }
            runCatching { backupRepository.importBackup(uri) }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = "Восстановлено чатов: ${summary.chatCount}, чатов с изображениями: ${summary.imageChatCount}.",
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isBusy = false, error = throwable.toUserMessage())
                    }
                }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BackupViewModel(backupRepository = container.backupRepository)
            }
        }
    }
}
