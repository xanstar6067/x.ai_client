package com.adam.xai_client.domain.usecase

import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.repository.ChatRepository
import com.adam.xai_client.data.repository.RoleRepository
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.MessageRole

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val roleRepository: RoleRepository,
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) {
    suspend operator fun invoke(
        chatId: Long?,
        input: String,
        selectedModelId: String?,
        selectedRoleId: Long?
    ): Long {
        val text = input.trim()
        if (text.isBlank()) {
            throw UserFacingException("Нельзя отправить пустое сообщение.")
        }

        val modelId = selectedModelId ?: throw UserFacingException("Модель не выбрана.")
        val settings = settingsRepository.currentApiSettings()
        if (settings.apiKey.isBlank()) {
            throw UserFacingException("API-ключ не задан.")
        }

        val effectiveRole = selectedRoleId?.let { roleRepository.getRole(it) }
            ?: roleRepository.getDefaultRole()
        val effectiveRoleId = effectiveRole?.id
        val now = System.currentTimeMillis()
        val targetChatId = chatId ?: chatRepository.createChat(
            title = text.asChatTitle(),
            selectedModelId = modelId,
            selectedRoleId = effectiveRoleId,
            now = now
        )

        if (chatId != null) {
            chatRepository.updateChatSelection(
                chatId = targetChatId,
                selectedModelId = modelId,
                selectedRoleId = effectiveRoleId,
                updatedAt = now
            )
        }

        chatRepository.addMessage(
            chatId = targetChatId,
            role = MessageRole.USER,
            content = text,
            now = now
        )
        settingsRepository.setLastSelectedModelId(modelId)
        settingsRepository.setLastSelectedRoleId(effectiveRoleId)

        val history = chatRepository.getMessages(targetChatId)
        val requestMessages = buildList {
            val systemPrompt = effectiveRole?.prompt.orEmpty().trim()
            if (systemPrompt.isNotBlank()) {
                add(ApiChatMessage(role = MessageRole.SYSTEM.apiName, content = systemPrompt))
            }
            history
                .filter { it.role != MessageRole.SYSTEM }
                .forEach { message ->
                    add(
                        ApiChatMessage(
                            role = message.role.apiName,
                            content = message.content
                        )
                    )
                }
        }

        val assistantReply = try {
            apiClient.sendChatRequest(
                apiKey = settings.apiKey,
                baseUrl = settings.baseUrl,
                modelId = modelId,
                messages = requestMessages
            ).trim()
        } catch (exception: Exception) {
            chatRepository.touchChat(targetChatId)
            throw MessageSendFailedException(
                chatId = targetChatId,
                message = exception.message ?: "Не удалось получить ответ модели.",
                cause = exception
            )
        }

        if (assistantReply.isBlank()) {
            chatRepository.touchChat(targetChatId)
            throw MessageSendFailedException(
                chatId = targetChatId,
                message = "Модель вернула пустой ответ.",
                cause = null
            )
        }

        chatRepository.addMessage(
            chatId = targetChatId,
            role = MessageRole.ASSISTANT,
            content = assistantReply,
            now = System.currentTimeMillis()
        )
        chatRepository.touchChat(targetChatId)

        return targetChatId
    }

    private fun String.asChatTitle(): String {
        val firstLine = lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        return firstLine.take(48).ifBlank { "Новый чат" }
    }
}

class UserFacingException(
    override val message: String
) : Exception(message)

class MessageSendFailedException(
    val chatId: Long,
    override val message: String,
    cause: Throwable?
) : Exception(message, cause)
