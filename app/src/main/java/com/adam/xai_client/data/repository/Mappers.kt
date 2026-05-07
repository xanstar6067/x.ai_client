package com.adam.xai_client.data.repository

import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.Chat
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
