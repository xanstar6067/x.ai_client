package com.adam.xai_client.data.repository

import com.adam.xai_client.data.local.entity.AiModelEntity
import com.adam.xai_client.data.local.entity.ChatModelSettingsEntity
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.ImageChatEntity
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.local.entity.ImageMessageSummary
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity
import com.adam.xai_client.data.local.entity.VideoChatEntity
import com.adam.xai_client.data.local.entity.VideoMessageEntity
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.Chat
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.ModelRole
import com.adam.xai_client.domain.model.VideoChat
import com.adam.xai_client.domain.model.VideoChatMessage

internal fun ChatEntity.asDomain(): Chat = Chat(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId,
    selectedRoleId = selectedRoleId,
    cachedTokenCount = cachedTokenCount,
    lastPromptTokenCount = lastPromptTokenCount,
    lastCompletionTokenCount = lastCompletionTokenCount,
    lastCachedTokenCount = lastCachedTokenCount,
    lastReasoningTokenCount = lastReasoningTokenCount
)

internal fun MessageEntity.asDomain(): Message = Message(
    id = id,
    chatId = chatId,
    role = role,
    content = content,
    attachments = attachments,
    reasoningContent = reasoningContent,
    responseId = responseId,
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
    imageFilePath = imageFilePath,
    imageMimeType = imageMimeType,
    sourceMessageId = sourceMessageId,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

internal fun ImageMessageSummary.asDomain(): ImageChatMessage = ImageChatMessage(
    id = id,
    chatId = chatId,
    role = role,
    content = content,
    imageFilePath = imageFilePath,
    imageMimeType = imageMimeType,
    hasImage = hasImage,
    sourceMessageId = sourceMessageId,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

internal fun VideoChatEntity.asDomain(): VideoChat = VideoChat(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId
)

internal fun VideoMessageEntity.asDomain(): VideoChatMessage = VideoChatMessage(
    id = id,
    chatId = chatId,
    role = role,
    content = content,
    sourceImageUrl = sourceImageUrl,
    videoFilePath = videoFilePath,
    videoMimeType = videoMimeType,
    videoDurationSeconds = videoDurationSeconds,
    videoRespectModeration = videoRespectModeration,
    requestId = requestId,
    aspectRatio = aspectRatio,
    resolution = resolution,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

internal fun AiModelEntity.asDomain(): AiModel = AiModel(
    id = id,
    name = name,
    isEnabledForChat = isEnabledForChat,
    aliases = aliases.toStringList(),
    fingerprint = fingerprint,
    version = version,
    inputModalities = inputModalities.toStringList(),
    outputModalities = outputModalities.toStringList(),
    maxPromptLength = maxPromptLength,
    promptTextTokenPrice = promptTextTokenPrice,
    cachedPromptTextTokenPrice = cachedPromptTextTokenPrice,
    completionTextTokenPrice = completionTextTokenPrice,
    promptImageTokenPrice = promptImageTokenPrice,
    searchPrice = searchPrice,
    imagePrice = imagePrice
)

internal fun AiModel.asEntity(updatedAt: Long): AiModelEntity = AiModelEntity(
    id = id,
    name = name,
    isEnabledForChat = isEnabledForChat,
    aliases = aliases.toStorageString(),
    fingerprint = fingerprint,
    version = version,
    inputModalities = inputModalities.toStorageString(),
    outputModalities = outputModalities.toStorageString(),
    maxPromptLength = maxPromptLength,
    promptTextTokenPrice = promptTextTokenPrice,
    cachedPromptTextTokenPrice = cachedPromptTextTokenPrice,
    completionTextTokenPrice = completionTextTokenPrice,
    promptImageTokenPrice = promptImageTokenPrice,
    searchPrice = searchPrice,
    imagePrice = imagePrice,
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
    contextMessageLimit = contextMessageLimit,
    webSearchEnabled = webSearchEnabled
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
    webSearchEnabled = webSearchEnabled,
    updatedAt = updatedAt
)

private fun String?.toStringList(): List<String> {
    return this
        ?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
}

private fun List<String>.toStorageString(): String? {
    return map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n")
}
