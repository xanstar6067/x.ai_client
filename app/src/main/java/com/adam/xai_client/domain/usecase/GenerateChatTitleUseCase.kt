package com.adam.xai_client.domain.usecase

import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.MessageRole
import kotlinx.coroutines.flow.first

class GenerateChatTitleUseCase(
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) {
    suspend operator fun invoke(firstMessage: String): String? {
        val source = firstMessage.trim()
        if (source.isBlank()) return null

        val modelId = settingsRepository.chatNamingModelIdValue() ?: return null
        val settings = settingsRepository.currentApiSettings()
        if (settings.apiKey.isBlank()) return null

        val rawTitle = apiClient.sendChatRequest(
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl,
            modelId = modelId,
            messages = listOf(
                ApiChatMessage(
                    role = MessageRole.SYSTEM.apiName,
                    content = TITLE_SYSTEM_PROMPT
                ),
                ApiChatMessage(
                    role = MessageRole.USER.apiName,
                    content = source.take(MAX_SOURCE_CHARS)
                )
            ),
            modelSettings = ChatModelSettings(
                maxTokens = 24,
                temperature = 0.2
            )
        )

        return rawTitle.sanitizeChatTitle()
    }

    private suspend fun SettingsRepository.chatNamingModelIdValue(): String? {
        return chatNamingModelId.first()?.trim()?.ifBlank { null }
    }

    private fun String.sanitizeChatTitle(): String? {
        return lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('"', '\'', '`', '«', '»')
            ?.replace(Regex("\\s+"), " ")
            ?.take(MAX_TITLE_CHARS)
            ?.ifBlank { null }
    }

    private companion object {
        const val MAX_SOURCE_CHARS = 4_000
        const val MAX_TITLE_CHARS = 64
        const val TITLE_SYSTEM_PROMPT =
            "Назови чат по первому сообщению пользователя. Верни только короткое название без кавычек, пояснений и знаков списка. Максимум 6 слов."
    }
}
