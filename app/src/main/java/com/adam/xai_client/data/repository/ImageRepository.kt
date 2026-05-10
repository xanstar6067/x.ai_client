package com.adam.xai_client.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.entity.ImageChatEntity
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.local.entity.ImageMessageSummary
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.isImageGenerationModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Base64

class ImageRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) {
    private val imageChatDao = database.imageChatDao()
    private val imageMessageDao = database.imageMessageDao()
    private val aiModelDao = database.aiModelDao()

    val imageChats: Flow<List<ImageChat>> = imageChatDao.observeChats()
        .map { entities -> entities.map { it.asDomain() } }

    val imageModels: Flow<List<AiModel>> = aiModelDao.observeEnabledModels()
        .map { entities ->
            entities
                .map { it.asDomain() }
                .filter { it.isImageGenerationModel() }
        }

    fun observeMessages(chatId: Long?): Flow<List<ImageChatMessage>> {
        return if (chatId == null) {
            flowOf(emptyList())
        } else {
            imageMessageDao.observeMessages(chatId).map { entities ->
                entities.activePath().mapActivePathWithVersions(entities)
            }
        }
    }

    suspend fun generateImage(options: ImageGenerationOptions): GeneratedImage {
        val prompt = options.prompt.trim()
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Введите описание изображения.")
        }
        if (options.modelId.isBlank()) {
            throw IllegalArgumentException("Выберите модель для генерации изображений.")
        }

        val settings = settingsRepository.currentApiSettings()
        if (settings.apiKey.isBlank()) {
            throw IllegalStateException("API-ключ не задан.")
        }

        return apiClient.generateImage(
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl,
            options = options.copy(prompt = prompt)
        )
    }

    suspend fun recoverLegacyStoredImages() {
        imageMessageDao.getLegacyImageRefs().forEach { legacyImage ->
            val file = legacyImageFile(legacyImage.id, legacyImage.imageMimeType)
            runCatching {
                val parentDir = file.parentFile
                    ?: throw IllegalStateException("Cannot resolve image storage directory.")
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    throw IllegalStateException("Cannot create image storage directory.")
                }
                file.outputStream().use { output ->
                    var offset = 1
                    while (offset <= legacyImage.byteCount) {
                        val chunkSize = minOf(LEGACY_IMAGE_CHUNK_SIZE, legacyImage.byteCount - offset + 1)
                        val chunk = imageMessageDao.getImageBytesChunk(
                            messageId = legacyImage.id,
                            start = offset,
                            length = chunkSize
                        ) ?: throw IllegalStateException("Cannot read image chunk.")
                        output.write(chunk)
                        offset += chunk.size
                    }
                }
                imageMessageDao.moveImageToFile(legacyImage.id, file.absolutePath)
            }.onFailure {
                file.delete()
            }
        }
    }

    suspend fun createChat(
        title: String,
        selectedModelId: String?,
        now: Long = System.currentTimeMillis()
    ): Long {
        return imageChatDao.insertChat(
            ImageChatEntity(
                title = title.ifBlank { NEW_CHAT_TITLE },
                createdAt = now,
                updatedAt = now,
                selectedModelId = selectedModelId
            )
        )
    }

    suspend fun deleteChat(chatId: Long) {
        imageChatDao.deleteChatById(chatId)
    }

    suspend fun duplicateChat(chatId: Long, now: Long = System.currentTimeMillis()): Long {
        return database.withTransaction {
            val chat = imageChatDao.getChat(chatId) ?: return@withTransaction 0L
            val newChatId = imageChatDao.insertChat(
                chat.copy(
                    id = 0,
                    title = chat.title.asCopyTitle(),
                    createdAt = now,
                    updatedAt = now
                )
            )
            val idMap = mutableMapOf<Long, Long>()
            val messages = imageMessageDao.getMessageEntities(chatId)
            messages.forEach { message ->
                val newMessageId = imageMessageDao.insertMessage(
                    message.copy(
                        id = 0,
                        chatId = newChatId,
                        sourceMessageId = message.sourceMessageId?.let(idMap::get) ?: message.sourceMessageId,
                        parentMessageId = message.parentMessageId?.let(idMap::get),
                        activeChildMessageId = null
                    )
                )
                idMap[message.id] = newMessageId
            }
            messages.forEach { message ->
                val newMessageId = idMap[message.id] ?: return@forEach
                message.activeChildMessageId
                    ?.let(idMap::get)
                    ?.let { activeChildId -> imageMessageDao.updateActiveChild(newMessageId, activeChildId) }
            }
            newChatId
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        database.withTransaction {
            val message = imageMessageDao.getMessage(messageId) ?: return@withTransaction
            val children = imageMessageDao.getChildMessages(messageId)
            val replacementChildId = message.activeChildMessageId
                ?.takeIf { activeChildId -> children.any { it.id == activeChildId } }
                ?: children.firstOrNull()?.id
                ?: message.parentMessageId?.let { parentId ->
                    imageMessageDao.getChildMessages(parentId)
                        .firstOrNull { it.id != messageId }
                        ?.id
                }

            imageMessageDao.updateParentForChildren(
                oldParentMessageId = messageId,
                parentMessageId = message.parentMessageId
            )
            message.parentMessageId?.let { parentId ->
                val parent = imageMessageDao.getMessage(parentId)
                if (parent?.activeChildMessageId == messageId) {
                    imageMessageDao.updateActiveChild(parentId, replacementChildId)
                }
            }
            imageMessageDao.deleteMessageById(messageId)
        }
    }

    suspend fun updateUserMessageText(messageId: Long, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) {
            throw IllegalArgumentException("Нельзя сохранить пустое сообщение.")
        }
        val message = imageMessageDao.getMessage(messageId) ?: return
        if (message.role != MessageRole.USER) {
            return
        }
        imageMessageDao.updateMessageContent(messageId, trimmedContent)
    }

    suspend fun updateChatSelection(
        chatId: Long,
        selectedModelId: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val current = imageChatDao.getChat(chatId) ?: return
        imageChatDao.updateChat(
            current.copy(
                selectedModelId = selectedModelId,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun addUserMessage(
        chatId: Long,
        content: String,
        parentMessageId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        return database.withTransaction {
            val messageId = imageMessageDao.insertMessage(
                ImageMessageEntity(
                    chatId = chatId,
                    role = MessageRole.USER,
                    content = content.trim(),
                    parentMessageId = parentMessageId,
                    createdAt = now
                )
            )
            parentMessageId?.let { imageMessageDao.updateActiveChild(it, messageId) }
            messageId
        }
    }

    suspend fun addAssistantImageMessage(
        chatId: Long,
        content: String,
        image: GeneratedImage,
        sourceMessageId: Long?,
        parentMessageId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        val imageFile = persistGeneratedImage(image, now)
        return database.withTransaction {
            val messageId = imageMessageDao.insertMessage(
                ImageMessageEntity(
                    chatId = chatId,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    imageBytes = null,
                    imageFilePath = imageFile.absolutePath,
                    imageMimeType = image.mimeType,
                    sourceMessageId = sourceMessageId,
                    parentMessageId = parentMessageId,
                    createdAt = now
                )
            )
            parentMessageId?.let { imageMessageDao.updateActiveChild(it, messageId) }
            messageId
        }
    }

    suspend fun updateChatAfterGeneration(
        chatId: Long,
        title: String,
        selectedModelId: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val current = imageChatDao.getChat(chatId) ?: return
        imageChatDao.updateChat(
            current.copy(
                title = if (current.title == NEW_CHAT_TITLE) title else current.title,
                selectedModelId = selectedModelId,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun getMessages(chatId: Long): List<ImageChatMessage> {
        val entities = imageMessageDao.getMessages(chatId)
        return entities.activePath().mapActivePathWithVersions(entities)
    }

    suspend fun getVisibleTailMessageId(chatId: Long): Long? {
        return imageMessageDao.getMessages(chatId).activePath().lastOrNull()?.id
    }

    suspend fun switchToSiblingVersion(messageId: Long, direction: Int) {
        if (direction == 0) return
        database.withTransaction {
            val message = imageMessageDao.getMessage(messageId) ?: return@withTransaction
            val parentId = message.parentMessageId ?: return@withTransaction
            val siblings = imageMessageDao.getMessages(message.chatId)
                .filter { it.parentMessageId == parentId && it.role == message.role }
                .sortedWith(compareBy<ImageMessageSummary> { it.createdAt }.thenBy { it.id })
            if (siblings.size <= 1) return@withTransaction
            val currentIndex = siblings.indexOfFirst { it.id == messageId }
            if (currentIndex < 0) return@withTransaction
            val nextIndex = Math.floorMod(currentIndex + direction, siblings.size)
            imageMessageDao.updateActiveChild(parentId, siblings[nextIndex].id)
            val currentChat = imageChatDao.getChat(message.chatId) ?: return@withTransaction
            imageChatDao.updateChat(currentChat.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun imageAsDataUrl(image: GeneratedImage): String {
        val base64 = Base64.getEncoder().encodeToString(image.readBytes())
        return "data:${image.mimeType};base64,$base64"
    }

    fun saveImage(image: GeneratedImage): Uri {
        val extension = when (image.mimeType.substringAfterLast('/')) {
            "png" -> "png"
            "webp" -> "webp"
            else -> "jpg"
        }
        val fileName = "xai_image_${System.currentTimeMillis()}.$extension"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageScoped(image = image, fileName = fileName)
        } else {
            saveImageLegacy(image = image, fileName = fileName)
        }
    }

    private fun saveImageScoped(image: GeneratedImage, fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$APP_PICTURES_DIR"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать файл изображения.")

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(image.readBytes())
            } ?: throw IllegalStateException("Не удалось открыть файл изображения.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure { throwable ->
            resolver.delete(uri, null, null)
            throw throwable
        }

        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveImageLegacy(image: GeneratedImage, fileName: String): Uri {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, APP_PICTURES_DIR)
        if (!appDir.exists() && !appDir.mkdirs()) {
            throw IllegalStateException("Не удалось создать папку Pictures/$APP_PICTURES_DIR.")
        }
        val file = File(appDir, fileName)
        file.outputStream().use { output -> output.write(image.readBytes()) }
        return Uri.fromFile(file)
    }

    private fun persistGeneratedImage(image: GeneratedImage, createdAt: Long): File {
        val extension = when (image.mimeType.substringAfterLast('/')) {
            "png" -> "png"
            "webp" -> "webp"
            else -> "jpg"
        }
        val imagesDir = File(context.filesDir, GENERATED_IMAGES_DIR)
        if (!imagesDir.exists() && !imagesDir.mkdirs()) {
            throw IllegalStateException("РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕР·РґР°С‚СЊ РїР°РїРєСѓ РґР»СЏ РёР·РѕР±СЂР°Р¶РµРЅРёР№.")
        }
        val file = File(imagesDir, "image_${createdAt}_${System.nanoTime()}.$extension")
        file.outputStream().use { output -> output.write(image.readBytes()) }
        return file
    }

    private fun legacyImageFile(messageId: Long, mimeType: String?): File {
        val extension = when (mimeType?.substringAfterLast('/')) {
            "png" -> "png"
            "webp" -> "webp"
            else -> "jpg"
        }
        return File(File(context.filesDir, GENERATED_IMAGES_DIR), "legacy_image_$messageId.$extension")
    }

    private fun GeneratedImage.readBytes(): ByteArray {
        bytes?.let { return it }
        val path = filePath?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Р¤Р°Р№Р» РёР·РѕР±СЂР°Р¶РµРЅРёСЏ РЅРµ РЅР°Р№РґРµРЅ.")
        return File(path).readBytes()
    }

    private companion object {
        const val APP_PICTURES_DIR = "xAI Chat"
        const val GENERATED_IMAGES_DIR = "generated_images"
        const val LEGACY_IMAGE_CHUNK_SIZE = 512 * 1024
        const val NEW_CHAT_TITLE = "Новый image-чат"
    }
    private fun List<ImageMessageSummary>.activePath(): List<ImageMessageSummary> {
        if (isEmpty()) return emptyList()
        val byId = associateBy { it.id }
        val byParent = groupBy { it.parentMessageId }
        val result = mutableListOf<ImageMessageSummary>()
        var parentId: Long? = null
        val visited = mutableSetOf<Long>()
        while (true) {
            val children = byParent[parentId]
                ?.sortedWith(compareBy<ImageMessageSummary> { it.createdAt }.thenBy { it.id })
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

    private fun List<ImageMessageSummary>.mapActivePathWithVersions(
        allMessages: List<ImageMessageSummary>
    ): List<ImageChatMessage> {
        val siblingGroups = allMessages
            .groupBy { Pair(it.parentMessageId, it.role) }
            .mapValues { (_, siblings) ->
                siblings.sortedWith(compareBy<ImageMessageSummary> { it.createdAt }.thenBy { it.id })
            }
        return map { entity ->
            val siblings = siblingGroups[Pair(entity.parentMessageId, entity.role)].orEmpty()
            entity.asDomain().copy(
                versionIndex = siblings.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.plus(1) ?: 1,
                versionCount = siblings.size.coerceAtLeast(1)
            )
        }
    }
}

private fun String.asCopyTitle(): String = "$this (копия)"
