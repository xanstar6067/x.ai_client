package com.adam.xai_client.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.entity.VideoChatEntity
import com.adam.xai_client.data.local.entity.VideoMessageEntity
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.GeneratedVideo
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.VideoChat
import com.adam.xai_client.domain.model.VideoChatMessage
import com.adam.xai_client.domain.model.VideoGenerationOptions
import com.adam.xai_client.domain.model.VideoGenerationProgress
import com.adam.xai_client.domain.model.isVideoGenerationModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File

class VideoRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) {
    private val videoChatDao = database.videoChatDao()
    private val videoMessageDao = database.videoMessageDao()
    private val aiModelDao = database.aiModelDao()

    private val videoStorageDir: File
        get() = File(context.filesDir, APP_VIDEOS_DIR)

    val videoChats: Flow<List<VideoChat>> = videoChatDao.observeChats()
        .map { entities -> entities.map { it.asDomain() } }

    val videoModels: Flow<List<AiModel>> = aiModelDao.observeEnabledModels()
        .map { entities ->
            entities
                .map { it.asDomain() }
                .filter { it.isVideoGenerationModel() }
        }

    fun observeMessages(chatId: Long?): Flow<List<VideoChatMessage>> {
        return if (chatId == null) {
            flowOf(emptyList())
        } else {
            videoMessageDao.observeMessages(chatId).map { entities ->
                entities.activePath().mapActivePathWithVersions(entities)
            }
        }
    }

    suspend fun generateVideo(
        options: VideoGenerationOptions,
        onProgress: (VideoGenerationProgress) -> Unit
    ): Pair<GeneratedVideo, String> {
        val prompt = options.prompt.trim()
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Введите описание видео.")
        }
        if (options.modelId.isBlank()) {
            throw IllegalArgumentException("Выберите модель для генерации видео.")
        }

        val settings = settingsRepository.currentApiSettings()
        if (settings.apiKey.isBlank()) {
            throw IllegalStateException("API-ключ не задан.")
        }

        val remote = apiClient.generateVideo(
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl,
            options = options.copy(prompt = prompt),
            onProgress = onProgress
        )
        val downloaded = apiClient.downloadGeneratedVideo(remote.url)
        val file = persistVideo(downloaded.bytes, downloaded.mimeType)
        return GeneratedVideo(
            filePath = file.absolutePath,
            mimeType = downloaded.mimeType,
            durationSeconds = remote.durationSeconds,
            respectModeration = remote.respectModeration
        ) to remote.requestId
    }

    suspend fun createChat(
        title: String,
        selectedModelId: String?,
        now: Long = System.currentTimeMillis()
    ): Long {
        return videoChatDao.insertChat(
            VideoChatEntity(
                title = title.ifBlank { NEW_CHAT_TITLE },
                createdAt = now,
                updatedAt = now,
                selectedModelId = selectedModelId
            )
        )
    }

    suspend fun deleteChat(chatId: Long) {
        val messages = videoMessageDao.getMessages(chatId)
        messages.forEach { deleteVideoFile(it.videoFilePath) }
        videoChatDao.deleteChatById(chatId)
    }

    suspend fun duplicateChat(chatId: Long, now: Long = System.currentTimeMillis()): Long {
        return database.withTransaction {
            val chat = videoChatDao.getChat(chatId) ?: return@withTransaction 0L
            val newChatId = videoChatDao.insertChat(
                chat.copy(
                    id = 0,
                    title = chat.title.asCopyTitle(),
                    createdAt = now,
                    updatedAt = now
                )
            )
            val idMap = mutableMapOf<Long, Long>()
            val messages = videoMessageDao.getMessages(chatId)
            messages.forEach { message ->
                val newMessageId = videoMessageDao.insertMessage(
                    message.copy(
                        id = 0,
                        chatId = newChatId,
                        videoFilePath = copyVideoFile(message.videoFilePath),
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
                    ?.let { activeChildId -> videoMessageDao.updateActiveChild(newMessageId, activeChildId) }
            }
            newChatId
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        database.withTransaction {
            val message = videoMessageDao.getMessage(messageId) ?: return@withTransaction
            val children = videoMessageDao.getChildMessages(messageId)
            val replacementChildId = message.activeChildMessageId
                ?.takeIf { activeChildId -> children.any { it.id == activeChildId } }
                ?: children.firstOrNull()?.id
                ?: message.parentMessageId?.let { parentId ->
                    videoMessageDao.getChildMessages(parentId)
                        .firstOrNull { it.id != messageId }
                        ?.id
                }

            videoMessageDao.updateParentForChildren(
                oldParentMessageId = messageId,
                parentMessageId = message.parentMessageId
            )
            message.parentMessageId?.let { parentId ->
                val parent = videoMessageDao.getMessage(parentId)
                if (parent?.activeChildMessageId == messageId) {
                    videoMessageDao.updateActiveChild(parentId, replacementChildId)
                }
            }
            videoMessageDao.deleteMessageById(messageId)
            deleteVideoFile(message.videoFilePath)
        }
    }

    suspend fun updateUserMessageText(messageId: Long, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) {
            throw IllegalArgumentException("Нельзя сохранить пустое сообщение.")
        }
        val message = videoMessageDao.getMessage(messageId) ?: return
        if (message.role != MessageRole.USER) {
            return
        }
        videoMessageDao.updateMessageContent(messageId, trimmedContent)
    }

    suspend fun updateChatSelection(
        chatId: Long,
        selectedModelId: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val current = videoChatDao.getChat(chatId) ?: return
        videoChatDao.updateChat(
            current.copy(
                selectedModelId = selectedModelId,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun addUserMessage(
        chatId: Long,
        content: String,
        sourceImageUrl: String?,
        parentMessageId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        return database.withTransaction {
            val messageId = videoMessageDao.insertMessage(
                VideoMessageEntity(
                    chatId = chatId,
                    role = MessageRole.USER,
                    content = content.trim(),
                    sourceImageUrl = sourceImageUrl?.trim()?.ifBlank { null },
                    parentMessageId = parentMessageId,
                    createdAt = now
                )
            )
            parentMessageId?.let { videoMessageDao.updateActiveChild(it, messageId) }
            messageId
        }
    }

    suspend fun addAssistantVideoMessage(
        chatId: Long,
        content: String,
        video: GeneratedVideo,
        requestId: String?,
        sourceImageUrl: String?,
        aspectRatio: String?,
        resolution: String?,
        parentMessageId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        return database.withTransaction {
            val messageId = videoMessageDao.insertMessage(
                VideoMessageEntity(
                    chatId = chatId,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    sourceImageUrl = sourceImageUrl?.trim()?.ifBlank { null },
                    videoFilePath = video.filePath,
                    videoMimeType = video.mimeType,
                    videoDurationSeconds = video.durationSeconds,
                    videoRespectModeration = video.respectModeration,
                    requestId = requestId,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    parentMessageId = parentMessageId,
                    createdAt = now
                )
            )
            parentMessageId?.let { videoMessageDao.updateActiveChild(it, messageId) }
            messageId
        }
    }

    suspend fun updateChatAfterGeneration(
        chatId: Long,
        title: String,
        selectedModelId: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val current = videoChatDao.getChat(chatId) ?: return
        videoChatDao.updateChat(
            current.copy(
                title = if (current.title == NEW_CHAT_TITLE) title else current.title,
                selectedModelId = selectedModelId,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun getVisibleTailMessageId(chatId: Long): Long? {
        return videoMessageDao.getMessages(chatId).activePath().lastOrNull()?.id
    }

    suspend fun switchToSiblingVersion(messageId: Long, direction: Int) {
        if (direction == 0) return
        database.withTransaction {
            val message = videoMessageDao.getMessage(messageId) ?: return@withTransaction
            val parentId = message.parentMessageId ?: return@withTransaction
            val siblings = videoMessageDao.getMessages(message.chatId)
                .filter { it.parentMessageId == parentId && it.role == message.role }
                .sortedWith(compareBy<VideoMessageEntity> { it.createdAt }.thenBy { it.id })
            if (siblings.size <= 1) return@withTransaction
            val currentIndex = siblings.indexOfFirst { it.id == messageId }
            if (currentIndex < 0) return@withTransaction
            val nextIndex = Math.floorMod(currentIndex + direction, siblings.size)
            videoMessageDao.updateActiveChild(parentId, siblings[nextIndex].id)
            val currentChat = videoChatDao.getChat(message.chatId) ?: return@withTransaction
            videoChatDao.updateChat(currentChat.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun saveVideo(video: GeneratedVideo): Uri {
        val source = File(video.filePath)
        if (!source.exists()) {
            throw IllegalStateException("Видео больше не найдено в памяти приложения.")
        }
        val fileName = "xai_video_${System.currentTimeMillis()}.${video.mimeType.toVideoExtension()}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveVideoScoped(source = source, mimeType = video.mimeType, fileName = fileName)
        } else {
            saveVideoLegacy(source = source, fileName = fileName)
        }
    }

    private fun persistVideo(bytes: ByteArray, mimeType: String): File {
        val dir = videoStorageDir
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Не удалось создать папку для видео.")
        }
        val file = File(dir, "generated_${System.currentTimeMillis()}_${System.nanoTime()}.${mimeType.toVideoExtension()}")
        file.writeBytes(bytes)
        return file
    }

    private fun saveVideoScoped(source: File, mimeType: String, fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$APP_GALLERY_DIR"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать видеофайл.")

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Не удалось открыть видеофайл.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure { throwable ->
            resolver.delete(uri, null, null)
            throw throwable
        }

        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveVideoLegacy(source: File, fileName: String): Uri {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDir = File(moviesDir, APP_GALLERY_DIR)
        if (!appDir.exists() && !appDir.mkdirs()) {
            throw IllegalStateException("Не удалось создать папку Movies/$APP_GALLERY_DIR.")
        }
        val file = File(appDir, fileName)
        source.inputStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(file)
    }

    private fun deleteVideoFile(path: String?) {
        val file = path?.let { File(it) } ?: return
        runCatching {
            val storageRoot = videoStorageDir.canonicalFile
            val target = file.canonicalFile
            if (target.path.startsWith(storageRoot.path) && target.exists()) {
                target.delete()
            }
        }
    }

    private fun copyVideoFile(path: String?): String? {
        val source = path?.let { File(it) } ?: return null
        if (!source.exists()) return path
        val extension = source.extension.ifBlank { "mp4" }
        val target = File(videoStorageDir, "copy_${System.currentTimeMillis()}_${System.nanoTime()}.$extension")
        if (!videoStorageDir.exists() && !videoStorageDir.mkdirs()) {
            throw IllegalStateException("Cannot create video storage directory.")
        }
        source.copyTo(target, overwrite = false)
        return target.absolutePath
    }

    private fun List<VideoMessageEntity>.activePath(): List<VideoMessageEntity> {
        if (isEmpty()) return emptyList()
        val byId = associateBy { it.id }
        val byParent = groupBy { it.parentMessageId }
        val result = mutableListOf<VideoMessageEntity>()
        var parentId: Long? = null
        val visited = mutableSetOf<Long>()
        while (true) {
            val children = byParent[parentId]
                ?.sortedWith(compareBy<VideoMessageEntity> { it.createdAt }.thenBy { it.id })
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

    private fun List<VideoMessageEntity>.mapActivePathWithVersions(
        allMessages: List<VideoMessageEntity>
    ): List<VideoChatMessage> {
        val siblingGroups = allMessages
            .groupBy { Pair(it.parentMessageId, it.role) }
            .mapValues { (_, siblings) ->
                siblings.sortedWith(compareBy<VideoMessageEntity> { it.createdAt }.thenBy { it.id })
            }
        return map { entity ->
            val siblings = siblingGroups[Pair(entity.parentMessageId, entity.role)].orEmpty()
            entity.asDomain().copy(
                versionIndex = siblings.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.plus(1) ?: 1,
                versionCount = siblings.size.coerceAtLeast(1)
            )
        }
    }

    private companion object {
        const val APP_VIDEOS_DIR = "generated_videos"
        const val APP_GALLERY_DIR = "xAI Chat"
        const val NEW_CHAT_TITLE = "Новый video-чат"
    }
}

private fun String.asCopyTitle(): String = "$this (копия)"

private fun String.toVideoExtension(): String {
    return when (substringAfterLast('/').lowercase()) {
        "quicktime", "mov" -> "mov"
        "webm" -> "webm"
        else -> "mp4"
    }
}
