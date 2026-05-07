package com.adam.xai_client.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.entity.ImageChatEntity
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageChat
import com.adam.xai_client.domain.model.ImageChatMessage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import com.adam.xai_client.domain.model.MessageRole
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
                entities.map { it.asDomain() }
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

    suspend fun deleteMessage(messageId: Long) {
        imageMessageDao.deleteMessageById(messageId)
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
        now: Long = System.currentTimeMillis()
    ): Long {
        return imageMessageDao.insertMessage(
            ImageMessageEntity(
                chatId = chatId,
                role = MessageRole.USER,
                content = content.trim(),
                createdAt = now
            )
        )
    }

    suspend fun addAssistantImageMessage(
        chatId: Long,
        content: String,
        image: GeneratedImage,
        sourceMessageId: Long?,
        now: Long = System.currentTimeMillis()
    ): Long {
        return imageMessageDao.insertMessage(
            ImageMessageEntity(
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                content = content,
                imageBytes = image.bytes,
                imageMimeType = image.mimeType,
                sourceMessageId = sourceMessageId,
                createdAt = now
            )
        )
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
        return imageMessageDao.getMessages(chatId).map { it.asDomain() }
    }

    fun imageAsDataUrl(image: GeneratedImage): String {
        val base64 = Base64.getEncoder().encodeToString(image.bytes)
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
                output.write(image.bytes)
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
        file.outputStream().use { output -> output.write(image.bytes) }
        return Uri.fromFile(file)
    }

    private companion object {
        const val APP_PICTURES_DIR = "xAI Chat"
        const val NEW_CHAT_TITLE = "Новый image-чат"
    }
}

private fun AiModel.isImageGenerationModel(): Boolean {
    val normalizedId = id.lowercase()
    val normalizedName = name.lowercase()
    return normalizedId.startsWith("grok-imagine-image") ||
        normalizedName.startsWith("grok-imagine-image")
}
