package com.adam.xai_client.domain.usecase

import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.remote.dto.ApiMessageAttachment
import com.adam.xai_client.data.remote.dto.ApiMessageAttachmentKind
import com.adam.xai_client.data.repository.ChatRepository
import com.adam.xai_client.data.repository.ModelRepository
import com.adam.xai_client.data.repository.RoleRepository
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageAttachment
import com.adam.xai_client.domain.model.MessageAttachmentKind
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.estimatedTokenUsageCostMicros
import com.adam.xai_client.domain.model.withKnownTokenPricingFallback
import com.adam.xai_client.domain.token.TokenCounter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.io.File
import java.util.Base64

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val roleRepository: RoleRepository,
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient,
    private val modelRepository: ModelRepository,
    private val generateChatTitleUseCase: GenerateChatTitleUseCase,
    private val tokenCounter: TokenCounter
) {
    suspend operator fun invoke(
        chatId: Long?,
        input: String,
        selectedModelId: String?,
        selectedRoleId: Long?,
        modelSettings: ChatModelSettings = ChatModelSettings(),
        onChatReady: suspend (Long) -> Unit = {},
        addUserMessage: Boolean = true,
        parentMessageId: Long? = null,
        attachments: List<MessageAttachment> = emptyList()
    ): Long {
        val text = input.trim()
        if (text.isBlank() && attachments.isEmpty()) {
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
        val isFirstUserMessage = chatId == null && addUserMessage
        val titleSeed = text.ifBlank { attachments.firstOrNull()?.displayName.orEmpty() }
        val targetChatId = chatId ?: chatRepository.createChat(
            title = titleSeed.asChatTitle(),
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
                attachments = attachments,
                tokenCount = tokenCounter.countMessage(text),
                parentMessageId = userParentMessageId,
                now = now
            )
        } else {
            parentMessageId
        }
        onChatReady(targetChatId)
        settingsRepository.setLastSelectedModelId(modelId)
        settingsRepository.setLastSelectedRoleId(effectiveRoleId)
        val selectedModel = (modelRepository.getModel(modelId) ?: AiModel(id = modelId))
            .withKnownTokenPricingFallback()

        val history = userMessageId?.let { chatRepository.getMessageLineage(targetChatId, it) }
            ?: chatRepository.getMessages(targetChatId)
        val contextHistory = effectiveModelSettings.contextMessageLimit
            .takeIf { it > 0 }
            ?.let { limit -> history.takeLast(limit) }
            ?: history
        val canUseResponsesContinuation = modelId.usesResponsesApiPath(effectiveModelSettings, contextHistory)
        val responseContinuation = if (canUseResponsesContinuation && effectiveModelSettings.contextMessageLimit == 0) {
            contextHistory.responseContinuation()
        } else {
            null
        }
        val requestHistory = responseContinuation?.messagesAfterPreviousResponse ?: contextHistory
        val requestMessages = buildList {
            val systemPrompt = effectiveRole?.prompt.orEmpty().trim()
            if (systemPrompt.isNotBlank() && responseContinuation == null) {
                add(ApiChatMessage(role = MessageRole.SYSTEM.apiName, content = systemPrompt))
            }
            requestHistory
                .filter { it.role != MessageRole.SYSTEM }
                .forEach { message ->
                    add(
                        ApiChatMessage(
                            role = message.role.apiName,
                            content = message.content,
                            reasoningContent = message.reasoningContent.takeIf {
                                message.role == MessageRole.ASSISTANT
                            },
                            attachments = message.attachments.toApiAttachments(
                                apiKey = settings.apiKey,
                                baseUrl = settings.baseUrl
                            )
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
        var assistantResponseId: String? = null
        var tokenUsageApplied = false
        try {
            apiClient.streamChatRequest(
                apiKey = settings.apiKey,
                baseUrl = settings.baseUrl,
                modelId = modelId,
                messages = requestMessages,
                modelSettings = effectiveModelSettings,
                promptCacheKey = targetChatId.toPromptCacheKey().takeIf { settings.promptCachingEnabled },
                previousResponseId = responseContinuation?.previousResponseId
            ).collect { delta ->
                if (delta.content.isNotEmpty()) {
                    assistantReply.append(delta.content)
                }
                if (delta.reasoningContent.isNotEmpty()) {
                    reasoningContent.append(delta.reasoningContent)
                }
                val responseId = delta.responseId?.takeIf { it.isNotBlank() }
                    ?.also { assistantResponseId = it }
                delta.tokenUsage?.takeUnless { tokenUsageApplied }?.let { usage ->
                    val costMicros = estimatedTokenUsageCostMicros(
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        cachedTokens = usage.cachedTokens,
                        imageTokens = usage.imageTokens,
                        model = selectedModel
                    )
                    chatRepository.updateTokenUsage(targetChatId, usage, costMicros)
                    tokenUsageApplied = true
                }
                if (delta.content.isNotEmpty() || delta.reasoningContent.isNotEmpty()) {
                    chatRepository.updateMessageContent(
                        messageId = assistantMessageId,
                        content = assistantReply.toString(),
                        reasoningContent = reasoningContent.toString().ifBlank { null }
                    )
                }
                responseId?.let {
                    chatRepository.updateMessageContent(
                        messageId = assistantMessageId,
                        content = assistantReply.toString(),
                        reasoningContent = reasoningContent.toString().ifBlank { null },
                        responseId = it
                    )
                }
            }
        } catch (exception: CancellationException) {
            chatRepository.deleteMessage(assistantMessageId)
            chatRepository.touchChat(targetChatId)
            throw exception
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
            reasoningContent = finalReasoning.ifBlank { null },
            responseId = assistantResponseId,
            tokenCount = tokenCounter.countMessage(finalReply, finalReasoning)
        )
        chatRepository.touchChat(targetChatId)
        if (isFirstUserMessage && text.isNotBlank()) {
            runCatching { generateChatTitleUseCase(text) }
                .getOrNull()
                ?.let { title -> chatRepository.updateChatTitle(targetChatId, title) }
        }

        return targetChatId
    }

    private fun String.asChatTitle(): String {
        val firstLine = lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        return firstLine.take(48).ifBlank { "Новый чат" }
    }

    private fun Long.toPromptCacheKey(): String = "xai-chat-$this"

    private fun String.usesResponsesApiPath(
        settings: ChatModelSettings,
        history: List<Message>
    ): Boolean {
        return lowercase().startsWith("grok-4") ||
            settings.webSearchEnabled ||
            history.any { message ->
                message.attachments.any { it.kind == MessageAttachmentKind.DOCUMENT }
            }
    }

    private data class ResponseContinuation(
        val previousResponseId: String,
        val messagesAfterPreviousResponse: List<Message>
    )

    private fun List<Message>.responseContinuation(): ResponseContinuation? {
        if (isEmpty() || last().role != MessageRole.USER) return null
        val previousResponseIndex = dropLast(1).indexOfLast { message ->
            message.role == MessageRole.ASSISTANT && !message.responseId.isNullOrBlank()
        }
        if (previousResponseIndex < 0) return null
        val previousResponseId = this[previousResponseIndex].responseId ?: return null
        val messagesAfterPreviousResponse = drop(previousResponseIndex + 1)
        if (messagesAfterPreviousResponse.none { it.role == MessageRole.USER }) return null
        return ResponseContinuation(
            previousResponseId = previousResponseId,
            messagesAfterPreviousResponse = messagesAfterPreviousResponse
        )
    }

    private suspend fun List<MessageAttachment>.toApiAttachments(
        apiKey: String,
        baseUrl: String
    ): List<ApiMessageAttachment> {
        return mapNotNull { attachment ->
            when (attachment.kind) {
                MessageAttachmentKind.IMAGE -> ApiMessageAttachment(
                    kind = ApiMessageAttachmentKind.IMAGE,
                    dataUrl = attachment.toDataUrl()
                )
                MessageAttachmentKind.DOCUMENT -> {
                    val file = File(attachment.filePath)
                    if (!file.exists()) return@mapNotNull null
                    val uploaded = apiClient.uploadFile(
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        fileName = attachment.displayName,
                        mimeType = attachment.mimeType,
                        bytes = file.readBytes()
                    )
                    ApiMessageAttachment(
                        kind = ApiMessageAttachmentKind.DOCUMENT,
                        fileId = uploaded.id
                    )
                }
                MessageAttachmentKind.VIDEO -> null
            }
        }
    }

    private fun MessageAttachment.toDataUrl(): String? {
        val file = File(filePath)
        if (!file.exists()) return null
        val base64 = Base64.getEncoder().encodeToString(file.readBytes())
        return "data:$mimeType;base64,$base64"
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
