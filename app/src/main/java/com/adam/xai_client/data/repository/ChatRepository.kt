package com.adam.xai_client.data.repository

import androidx.room.withTransaction
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.domain.model.Chat
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.token.TokenCounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val database: AppDatabase,
    private val tokenCounter: TokenCounter
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
                entities.activePath().mapActivePathWithVersions(entities)
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
        parentMessageId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        return database.withTransaction {
            val messageId = messageDao.insertMessage(
                MessageEntity(
                    chatId = chatId,
                    role = role,
                    content = content,
                    reasoningContent = reasoningContent,
                    tokenCount = tokenCounter.countMessage(content, reasoningContent),
                    parentMessageId = parentMessageId,
                    createdAt = now
                )
            )
            parentMessageId?.let { parentId ->
                messageDao.updateActiveChild(parentId, messageId)
            }
            messageId
        }
    }

    suspend fun updateMessageContent(
        messageId: Long,
        content: String,
        reasoningContent: String?
    ) {
        messageDao.updateMessageContent(
            messageId = messageId,
            content = content,
            reasoningContent = reasoningContent,
            tokenCount = tokenCounter.countMessage(content, reasoningContent)
        )
    }

    suspend fun updateMessageText(
        messageId: Long,
        content: String,
        reasoningContent: String?
    ) {
        val trimmedContent = content.trim()
        messageDao.updateMessageContent(
            messageId = messageId,
            content = trimmedContent,
            reasoningContent = reasoningContent,
            tokenCount = tokenCounter.countMessage(trimmedContent, reasoningContent)
        )
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
        val entities = messageDao.getMessages(chatId)
        return entities.activePath().mapActivePathWithVersions(entities)
    }

    suspend fun getMessageLineage(chatId: Long, messageId: Long): List<Message> {
        val entities = messageDao.getMessages(chatId)
        val byId = entities.associateBy { it.id }
        val lineage = ArrayDeque<MessageEntity>()
        var current = byId[messageId]
        val visited = mutableSetOf<Long>()
        while (current != null && visited.add(current.id)) {
            lineage.addFirst(current)
            current = current.parentMessageId?.let { byId[it] }
        }
        return lineage.toList().mapActivePathWithVersions(entities)
    }

    suspend fun getVisibleTailMessageId(chatId: Long): Long? {
        return messageDao.getMessages(chatId).activePath().lastOrNull()?.id
    }

    suspend fun switchToSiblingVersion(messageId: Long, direction: Int) {
        if (direction == 0) return
        database.withTransaction {
            val message = messageDao.getMessage(messageId) ?: return@withTransaction
            val parentId = message.parentMessageId ?: return@withTransaction
            val siblings = messageDao.getMessages(message.chatId)
                .filter { it.parentMessageId == parentId && it.role == message.role }
                .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
            if (siblings.size <= 1) return@withTransaction
            val currentIndex = siblings.indexOfFirst { it.id == messageId }
            if (currentIndex < 0) return@withTransaction
            val nextIndex = Math.floorMod(currentIndex + direction, siblings.size)
            messageDao.updateActiveChild(parentId, siblings[nextIndex].id)
            touchChat(message.chatId)
        }
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

    private fun MessageEntity.asDomainWithTokens(): Message {
        return asDomain().copy(
            tokenCount = tokenCount ?: tokenCounter.countMessage(content, reasoningContent)
        )
    }

    private fun List<MessageEntity>.activePath(): List<MessageEntity> {
        if (isEmpty()) return emptyList()
        val byId = associateBy { it.id }
        val byParent = groupBy { it.parentMessageId }
        val result = mutableListOf<MessageEntity>()
        var parentId: Long? = null
        val visited = mutableSetOf<Long>()
        while (true) {
            val children = byParent[parentId]
                ?.sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
                .orEmpty()
            if (children.isEmpty()) break
            val activeChildId = parentId?.let { byId[it]?.activeChildMessageId }
            val next = activeChildId
                ?.let { id -> children.firstOrNull { it.id == id } }
                ?: children.first()
            if (!visited.add(next.id)) break
            result += next
            parentId = next.id
        }
        return result
    }

    private fun List<MessageEntity>.mapActivePathWithVersions(
        allMessages: List<MessageEntity>
    ): List<Message> {
        val siblingGroups = allMessages
            .groupBy { Pair(it.parentMessageId, it.role) }
            .mapValues { (_, siblings) ->
                siblings.sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
            }
        return map { entity ->
            val siblings = siblingGroups[Pair(entity.parentMessageId, entity.role)].orEmpty()
            entity.asDomainWithTokens().copy(
                versionIndex = siblings.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.plus(1) ?: 1,
                versionCount = siblings.size.coerceAtLeast(1)
            )
        }
    }
}
