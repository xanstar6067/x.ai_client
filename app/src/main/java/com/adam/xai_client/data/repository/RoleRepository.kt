package com.adam.xai_client.data.repository

import com.adam.xai_client.data.local.dao.ModelRoleDao
import com.adam.xai_client.data.local.entity.ModelRoleEntity
import com.adam.xai_client.domain.model.ModelRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoleRepository(
    private val modelRoleDao: ModelRoleDao,
    private val settingsRepository: SettingsRepository
) {
    val roles: Flow<List<ModelRole>> = modelRoleDao.observeRoles()
        .map { entities -> entities.map { it.asDomain() } }

    suspend fun ensureBuiltInRole() {
        val builtIn = modelRoleDao.getBuiltInRole()
        if (builtIn == null) {
            val makeDefault = modelRoleDao.getDefaultRole() == null
            val roleId = modelRoleDao.insertRole(
                ModelRoleEntity(
                    name = DEFAULT_ROLE_NAME,
                    prompt = DEFAULT_ROLE_PROMPT,
                    isDefault = makeDefault,
                    isBuiltIn = true
                )
            )
            if (makeDefault) {
                settingsRepository.setLastSelectedRoleId(roleId)
            }
        } else if (modelRoleDao.getDefaultRole() == null) {
            modelRoleDao.setDefaultRole(builtIn.id)
            settingsRepository.setLastSelectedRoleId(builtIn.id)
        }
    }

    suspend fun getRole(roleId: Long): ModelRole? {
        return modelRoleDao.getRole(roleId)?.asDomain()
    }

    suspend fun getDefaultRole(): ModelRole? {
        ensureBuiltInRole()
        return modelRoleDao.getDefaultRole()?.asDomain()
    }

    suspend fun createRole(name: String, prompt: String): Long {
        validateRole(name, prompt)
        return modelRoleDao.insertRole(
            ModelRoleEntity(
                name = name.trim(),
                prompt = prompt.trim(),
                isDefault = false,
                isBuiltIn = false
            )
        )
    }

    suspend fun updateRole(roleId: Long, name: String, prompt: String) {
        validateRole(name, prompt)
        val current = modelRoleDao.getRole(roleId) ?: return
        modelRoleDao.updateRole(
            current.copy(
                name = name.trim(),
                prompt = prompt.trim()
            )
        )
    }

    suspend fun deleteRole(roleId: Long) {
        val current = modelRoleDao.getRole(roleId) ?: return
        if (current.isBuiltIn) {
            throw IllegalStateException("Базовую роль удалить нельзя.")
        }
        val wasDefault = current.isDefault
        modelRoleDao.deleteRole(current)
        if (wasDefault) {
            val fallback = modelRoleDao.getBuiltInRole()
            if (fallback != null) {
                modelRoleDao.setDefaultRole(fallback.id)
                settingsRepository.setLastSelectedRoleId(fallback.id)
            }
        }
    }

    suspend fun setDefaultRole(roleId: Long) {
        modelRoleDao.setDefaultRole(roleId)
        settingsRepository.setLastSelectedRoleId(roleId)
    }

    private fun validateRole(name: String, prompt: String) {
        require(name.isNotBlank()) { "Название роли не может быть пустым." }
        require(prompt.isNotBlank()) { "Prompt роли не может быть пустым." }
    }

    companion object {
        const val DEFAULT_ROLE_NAME = "Полезный ассистент"
        const val DEFAULT_ROLE_PROMPT = "Ты полезный ассистент"
    }
}
