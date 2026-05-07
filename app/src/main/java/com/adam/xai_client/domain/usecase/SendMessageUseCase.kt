package com.adam.xai_client.domain.usecase

import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.repository.ChatRepository
import com.adam.xai_client.data.repository.RoleRepository
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.MessageRole
import kotlinx.coroutines.flow.collect

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
        selectedRoleId: Long?,
        modelSettings: ChatModelSettings = ChatModelSettings(),
        onChatReady: suspend (Long) -> Unit = {},
        addUserMessage: Boolean = true,
        parentMessageId: Long? = null
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
        val effectiveModelSettings = modelSettings.copy(chatId = targetChatId)

        if (chatId != null) {
            chatRepository.updateChatSelection(
                chatId = targetChatId,
                selectedModelId = modelId,
                selectedRoleId = effectiveRoleId,
                updatedAt = now
            )
        }
        if (effectiveModelSettings.hasCustomValues) {
            chatRepository.updateModelSettings(targetChatId, effectiveModelSettings, now)
        }

        val userMessageId = if (addUserMessage) {
            val userParentMessageId = parentMessageId ?: chatRepository.getVisibleTailMessageId(targetChatId)
            chatRepository.addMessage(
                chatId = targetChatId,
                role = MessageRole.USER,
                content = text,
                parentMessageId = userParentMessageId,
                now = now
            )
        } else {
            parentMessageId
        }
        onChatReady(targetChatId)
        settingsRepository.setLastSelectedModelId(modelId)
        settingsRepository.setLastSelectedRoleId(effectiveRoleId)

        val history = userMessageId?.let { chatRepository.getMessageLineage(targetChatId, it) }
            ?: chatRepository.getMessages(targetChatId)
        val contextHistory = effectiveModelSettings.contextMessageLimit
            .takeIf { it > 0 }
            ?.let { limit -> history.takeLast(limit) }
            ?: history
        val requestMessages = buildList {
            val systemPrompt = effectiveRole?.prompt.orEmpty().trim()
            if (systemPrompt.isNotBlank()) {
                add(ApiChatMessage(role = MessageRole.SYSTEM.apiName, content = systemPrompt))
            }
            contextHistory
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

        val assistantMessageId = chatRepository.addMessage(
            chatId = targetChatId,
            role = MessageRole.ASSISTANT,
            content = "",
            parentMessageId = userMessageId,
            now = System.currentTimeMillis()
        )

        val assistantReply = StringBuilder()
        val reasoningContent = StringBuilder()
        try {
            apiClient.streamChatRequest(
                apiKey = settings.apiKey,
                baseUrl = settings.baseUrl,
                modelId = modelId,
                messages = requestMessages,
                modelSettings = effectiveModelSettings
            ).collect { delta ->
                if (delta.content.isNotEmpty()) {
                    assistantReply.append(delta.content)
                }
                if (delta.reasoningContent.isNotEmpty()) {
                    reasoningContent.append(delta.reasoningContent)
                }
                chatRepository.updateMessageContent(
                    messageId = assistantMessageId,
                    content = assistantReply.toString(),
                    reasoningContent = reasoningContent.toString().ifBlank { null }
                )
            }
        } catch (exception: Exception) {
            chatRepository.deleteMessage(assistantMessageId)
            chatRepository.touchChat(targetChatId)
            throw MessageSendFailedException(
                chatId = targetChatId,
                message = exception.message ?: "Не удалось получить ответ модели.",
                cause = exception
            )
        }

        val finalReply = assistantReply.toString().trim()
        val finalReasoning = reasoningContent.toString().trim()
        if (finalReply.isBlank() && finalReasoning.isBlank()) {
            chatRepository.deleteMessage(assistantMessageId)
            chatRepository.touchChat(targetChatId)
            throw MessageSendFailedException(
                chatId = targetChatId,
                message = "Модель вернула пустой ответ.",
                cause = null
            )
        }

        chatRepository.updateMessageContent(
            messageId = assistantMessageId,
            content = finalReply,
            reasoningContent = finalReasoning.ifBlank { null }
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
