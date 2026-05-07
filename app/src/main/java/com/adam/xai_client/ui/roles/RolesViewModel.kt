package com.adam.xai_client.ui.roles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adam.xai_client.AppContainer
import com.adam.xai_client.data.repository.RoleRepository
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.ui.components.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RolesUiState(
    val roles: List<ModelRole> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

class RolesViewModel(
    private val roleRepository: RoleRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RolesUiState())
    val uiState: StateFlow<RolesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            roleRepository.ensureBuiltInRole()
        }
        viewModelScope.launch {
            roleRepository.roles.collect { roles ->
                _uiState.update { it.copy(roles = roles) }
            }
        }
    }

    fun createRole(name: String, prompt: String) {
        viewModelScope.launch {
            runCatching { roleRepository.createRole(name = name, prompt = prompt) }
                .onSuccess {
                    _uiState.update {
                        it.copy(message = "Роль создана.", error = null)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = throwable.toUserMessage(), message = null)
                    }
                }
        }
    }

    fun updateRole(roleId: Long, name: String, prompt: String) {
        viewModelScope.launch {
            runCatching {
                roleRepository.updateRole(roleId = roleId, name = name, prompt = prompt)
            }.onSuccess {
                _uiState.update {
                    it.copy(message = "Роль обновлена.", error = null)
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(error = throwable.toUserMessage(), message = null)
                }
            }
        }
    }

    fun deleteRole(roleId: Long) {
        viewModelScope.launch {
            runCatching { roleRepository.deleteRole(roleId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(message = "Роль удалена.", error = null)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = throwable.toUserMessage(), message = null)
                    }
                }
        }
    }

    fun setDefaultRole(roleId: Long) {
        viewModelScope.launch {
            runCatching { roleRepository.setDefaultRole(roleId) }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.toUserMessage()) }
                }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RolesViewModel(roleRepository = container.roleRepository)
            }
        }
    }
}
