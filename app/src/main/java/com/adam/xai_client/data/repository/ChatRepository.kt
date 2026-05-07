package com.adam.xai_client.data.repository

import androidx.room.withTransaction
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.domain.model.Chat
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val database: AppDatabase
) {
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()
    private val chatModelSettingsDao = database.chatModelSettingsDao()

    val chats: Flow<List<Chat>> = chatDao.observeChats()
        .map { entities -> entities.map { it.asDomain() } }

    fun observeChat(chatId: Long?): Flow<Chat?> {
        return if (chatId == null) {
            flowOf(null)
        } else {
            chatDao.observeChat(chatId).map { it?.asDomain() }
        }
    }

    fun observeMessages(chatId: Long?): Flow<List<Message>> {
        return if (chatId == null) {
            flowOf(emptyList())
        } else {
            messageDao.observeMessages(chatId).map { entities ->
                entities.map { it.asDomain() }
            }
        }
    }

    fun observeModelSettings(chatId: Long?): Flow<ChatModelSettings> {
        return if (chatId == null) {
            flowOf(ChatModelSettings())
        } else {
            chatModelSettingsDao.observeSettings(chatId).map { entity ->
                entity?.asDomain() ?: ChatModelSettings(chatId = chatId)
            }
        }
    }

    suspend fun createChat(
        title: String,
        selectedModelId: String?,
        selectedRoleId: Long?,
        now: Long = System.currentTimeMillis()
    ): Long {
        return chatDao.insertChat(
            ChatEntity(
                title = title,
                createdAt = now,
                updatedAt = now,
                selectedModelId = selectedModelId,
                selectedRoleId = selectedRoleId
            )
        )
    }

    suspend fun addMessage(
        chatId: Long,
        role: MessageRole,
        content: String,
        reasoningContent: String? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        return messageDao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = role,
                content = content,
                reasoningContent = reasoningContent,
                createdAt = now
            )
        )
    }

    suspend fun updateMessageContent(
        messageId: Long,
        content: String,
        reasoningContent: String?
    ) {
        messageDao.updateMessageContent(messageId, content, reasoningContent)
    }

    suspend fun deleteMessage(messageId: Long) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun deleteMessages(messageIds: List<Long>) {
        if (messageIds.isNotEmpty()) {
            messageDao.deleteMessagesByIds(messageIds)
        }
    }

    suspend fun getMessages(chatId: Long): List<Message> {
        return messageDao.getMessages(chatId).map { it.asDomain() }
    }

    suspend fun getModelSettings(chatId: Long): ChatModelSettings {
        return chatModelSettingsDao.getSettings(chatId)?.asDomain()
            ?: ChatModelSettings(chatId = chatId)
    }

    suspend fun updateModelSettings(
        chatId: Long,
        modelSettings: ChatModelSettings,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        chatModelSettingsDao.upsertSettings(
            modelSettings.asEntity(
                chatId = chatId,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun updateChatSelection(
        chatId: Long,
        selectedModelId: String?,
        selectedRoleId: Long?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val current = chatDao.getChat(chatId) ?: return
        chatDao.updateChat(
            current.copy(
                selectedModelId = selectedModelId,
                selectedRoleId = selectedRoleId,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun touchChat(chatId: Long, updatedAt: Long = System.currentTimeMillis()) {
        val current = chatDao.getChat(chatId) ?: return
        chatDao.updateChat(current.copy(updatedAt = updatedAt))
    }

    suspend fun deleteChat(chatId: Long) {
        chatDao.deleteChatById(chatId)
    }

    suspend fun updateChatAfterFirstUserMessage(
        chatId: Long,
        title: String,
        selectedModelId: String?,
        selectedRoleId: Long?,
        updatedAt: Long
    ) {
        database.withTransaction {
            val current = chatDao.getChat(chatId) ?: return@withTransaction
            chatDao.updateChat(
                current.copy(
                    title = title,
                    selectedModelId = selectedModelId,
                    selectedRoleId = selectedRoleId,
                    updatedAt = updatedAt
                )
            )
        }
    }
}
