package com.adam.xai_client.data.repository

import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.local.entity.ChatModelSettingsEntity
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.ImageChatEntity
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.Chat
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.ModelRole

internal fun ChatEntity.asDomain(): Chat = Chat(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId,
    selectedRoleId = selectedRoleId
)

internal fun MessageEntity.asDomain(): Message = Message(
    id = id,
    chatId = chatId,
    role = role,
    content = content,
    reasoningContent = reasoningContent,
    tokenCount = tokenCount ?: 0,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

internal fun ImageChatEntity.asDomain(): ImageChat = ImageChat(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId
)

internal fun ImageMessageEntity.asDomain(): ImageChatMessage = ImageChatMessage(
    id = id,
    chatId = chatId,
    role = role,
    content = content,
    imageBytes = imageBytes,
    imageMimeType = imageMimeType,
    sourceMessageId = sourceMessageId,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

internal fun AiModelEntity.asDomain(): AiModel = AiModel(
    id = id,
    name = name,
    isEnabledForChat = isEnabledForChat
)

internal fun AiModel.asEntity(updatedAt: Long): AiModelEntity = AiModelEntity(
    id = id,
    name = name,
    isEnabledForChat = isEnabledForChat,
    updatedAt = updatedAt
)

internal fun ModelRoleEntity.asDomain(): ModelRole = ModelRole(
    id = id,
    name = name,
    prompt = prompt,
    isDefault = isDefault,
    isBuiltIn = isBuiltIn
)

internal fun ChatModelSettingsEntity.asDomain(): ChatModelSettings = ChatModelSettings(
    chatId = chatId,
    maxTokens = maxTokens,
    temperature = temperature,
    topP = topP,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    reasoningEffort = reasoningEffort,
    contextMessageLimit = contextMessageLimit
)

internal fun ChatModelSettings.asEntity(
    chatId: Long,
    updatedAt: Long
): ChatModelSettingsEntity = ChatModelSettingsEntity(
    chatId = chatId,
    maxTokens = maxTokens,
    temperature = temperature,
    topP = topP,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    reasoningEffort = reasoningEffort,
    contextMessageLimit = contextMessageLimit,
    updatedAt = updatedAt
)
