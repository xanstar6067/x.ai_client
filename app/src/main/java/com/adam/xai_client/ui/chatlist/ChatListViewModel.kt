package com.adam.xai_client.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.ChatRepository
import com.adam.xai_client.domain.model.Chat
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val error: String? = null
)

class ChatListViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.chats.collect { chats ->
                _uiState.update { it.copy(chats = chats) }
            }
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            runCatching { chatRepository.deleteChat(chatId) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun duplicateChat(chatId: Long) {
        viewModelScope.launch {
            runCatching { chatRepository.duplicateChat(chatId) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatListViewModel(chatRepository = container.chatRepository)
            }
        }
    }
}
