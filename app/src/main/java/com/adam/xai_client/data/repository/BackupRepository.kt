package com.adam.xai_client.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.entity.ChatEntity
import com.adam.xai_client.data.local.entity.ChatModelSettingsEntity
import com.adam.xai_client.data.local.entity.ImageChatEntity
import com.adam.xai_client.data.local.entity.ImageMessageEntity
import com.adam.xai_client.data.local.entity.MessageEntity
import com.adam.xai_client.data.local.entity.ModelRoleEntity
import com.adam.xai_client.data.local.entity.VideoChatEntity
import com.adam.xai_client.data.local.entity.VideoMessageEntity
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ReasoningEffort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

class BackupRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportBackup(): Uri {
        val backup = database.withTransaction {
            ChatBackupDto(
                exportedAt = System.currentTimeMillis(),
                chats = database.chatDao().getAllChats().map { it.toBackup() },
                messages = database.messageDao().getAllMessages().map { it.toBackup() },
                chatModelSettings = database.chatModelSettingsDao().getAllSettings().map { it.toBackup() },
                roles = database.modelRoleDao().getAllRoles().map { it.toBackup() },
                imageChats = database.imageChatDao().getAllChats().map { it.toBackup() },
                imageMessages = database.imageMessageDao().getAllMessages().map { it.toBackup() },
                videoChats = database.videoChatDao().getAllChats().map { it.toBackup() },
                videoMessages = database.videoMessageDao().getAllMessages().map { it.toBackup() }
            )
        }
        val content = json.encodeToString(backup)
        val fileName = "xai_chat_backup_${fileTimestamp()}.json"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportScoped(fileName, content)
        } else {
            exportLegacy(fileName, content)
        }
    }

    suspend fun importBackup(uri: Uri): BackupImportSummary {
        val content = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        } ?: throw IllegalStateException("Unable to open backup file.")
        val backup = json.decodeFromString<ChatBackupDto>(content)
        val existingVideoPaths = database.videoMessageDao().getAllMessages()
            .mapNotNull { it.videoFilePath }
            .toSet()
        val restoredVideoPaths = mutableSetOf<String>()
        database.withTransaction {
            database.messageDao().deleteAllMessages()
            database.imageMessageDao().deleteAllMessages()
            database.videoMessageDao().deleteAllMessages()
            database.chatModelSettingsDao().deleteAllSettings()
            database.chatDao().deleteAllChats()
            database.imageChatDao().deleteAllChats()
            database.videoChatDao().deleteAllChats()
            if (backup.roles.isNotEmpty()) {
                database.modelRoleDao().deleteAllRoles()
                database.modelRoleDao().insertRoles(backup.roles.map { it.toEntity() })
            }

            database.chatDao().insertChats(backup.chats.map { it.toEntity() })
            database.imageChatDao().insertChats(backup.imageChats.map { it.toEntity() })
            database.chatModelSettingsDao().upsertSettings(backup.chatModelSettings.map { it.toEntity() })
            database.messageDao().insertMessages(backup.messages.map { it.toEntity() })
            database.imageMessageDao().insertMessages(backup.imageMessages.map { it.toEntity() })
            database.videoChatDao().insertChats(backup.videoChats.map { it.toEntity() })
            database.videoMessageDao().insertMessages(
                backup.videoMessages.map { message ->
                    val videoFilePath = restoreVideoFile(message.videoBase64, message.videoMimeType)
                        ?: message.videoFilePath
                    message.toEntity(videoFilePath).also { entity ->
                        entity.videoFilePath?.let(restoredVideoPaths::add)
                    }
                }
            )
        }
        existingVideoPaths
            .filterNot { it in restoredVideoPaths }
            .forEach(::deleteVideoFile)
        return BackupImportSummary(
            chatCount = backup.chats.size,
            imageChatCount = backup.imageChats.size,
            videoChatCount = backup.videoChats.size
        )
    }

    private fun exportScoped(fileName: String, content: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Unable to create backup file.")
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Unable to write backup file.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure { throwable ->
            resolver.delete(uri, null, null)
            throw throwable
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(fileName: String, content: String): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadsDir, BACKUP_DIR)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IllegalStateException("Unable to create Downloads/$BACKUP_DIR.")
        }
        val file = File(backupDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    private fun fileTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun restoreVideoFile(videoBase64: String?, mimeType: String?): String? {
        val bytes = videoBase64?.let { Base64.getDecoder().decode(it) } ?: return null
        val dir = File(context.filesDir, APP_VIDEOS_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Unable to create video storage directory.")
        }
        val file = File(
            dir,
            "restored_${System.currentTimeMillis()}_${System.nanoTime()}.${mimeType.toVideoExtension()}"
        )
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun deleteVideoFile(path: String) {
        runCatching {
            val storageRoot = File(context.filesDir, APP_VIDEOS_DIR).canonicalFile
            val target = File(path).canonicalFile
            if (target.path.startsWith(storageRoot.path) && target.exists()) {
                target.delete()
            }
        }
    }

    private companion object {
        const val BACKUP_DIR = "xAI Chat Backups"
        const val APP_VIDEOS_DIR = "generated_videos"
    }
}

data class BackupImportSummary(
    val chatCount: Int,
    val imageChatCount: Int,
    val videoChatCount: Int
)

@Serializable
private data class ChatBackupDto(
    val schemaVersion: Int = 2,
    val exportedAt: Long,
    val chats: List<BackupChatDto>,
    val messages: List<BackupMessageDto>,
    val chatModelSettings: List<BackupChatModelSettingsDto>,
    val roles: List<BackupModelRoleDto> = emptyList(),
    val imageChats: List<BackupImageChatDto>,
    val imageMessages: List<BackupImageMessageDto>,
    val videoChats: List<BackupVideoChatDto> = emptyList(),
    val videoMessages: List<BackupVideoMessageDto> = emptyList()
)

@Serializable
private data class BackupChatDto(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?,
    val selectedRoleId: Long?
)

@Serializable
private data class BackupMessageDto(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val reasoningContent: String?,
    val tokenCount: Int?,
    val parentMessageId: Long?,
    val activeChildMessageId: Long?,
    val createdAt: Long
)

@Serializable
private data class BackupChatModelSettingsDto(
    val chatId: Long,
    val maxTokens: Int?,
    val temperature: Double?,
    val topP: Double?,
    val frequencyPenalty: Double?,
    val presencePenalty: Double?,
    val reasoningEffort: String?,
    val contextMessageLimit: Int,
    val updatedAt: Long
)

@Serializable
private data class BackupModelRoleDto(
    val id: Long,
    val name: String,
    val prompt: String,
    val isDefault: Boolean,
    val isBuiltIn: Boolean
)

@Serializable
private data class BackupImageChatDto(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?
)

@Serializable
private data class BackupImageMessageDto(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val imageBase64: String?,
    val imageMimeType: String?,
    val sourceMessageId: Long?,
    val parentMessageId: Long?,
    val activeChildMessageId: Long?,
    val createdAt: Long
)

@Serializable
private data class BackupVideoChatDto(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?
)

@Serializable
private data class BackupVideoMessageDto(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val sourceImageUrl: String?,
    val videoBase64: String?,
    val videoFilePath: String?,
    val videoMimeType: String?,
    val videoDurationSeconds: Int?,
    val videoRespectModeration: Boolean?,
    val requestId: String?,
    val aspectRatio: String?,
    val resolution: String?,
    val parentMessageId: Long?,
    val activeChildMessageId: Long?,
    val createdAt: Long
)

private fun ChatEntity.toBackup(): BackupChatDto = BackupChatDto(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId,
    selectedRoleId = selectedRoleId
)

private fun BackupChatDto.toEntity(): ChatEntity = ChatEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId,
    selectedRoleId = selectedRoleId
)

private fun MessageEntity.toBackup(): BackupMessageDto = BackupMessageDto(
    id = id,
    chatId = chatId,
    role = role.name,
    content = content,
    reasoningContent = reasoningContent,
    tokenCount = tokenCount,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

private fun BackupMessageDto.toEntity(): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    reasoningContent = reasoningContent,
    tokenCount = tokenCount,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

private fun ChatModelSettingsEntity.toBackup(): BackupChatModelSettingsDto = BackupChatModelSettingsDto(
    chatId = chatId,
    maxTokens = maxTokens,
    temperature = temperature,
    topP = topP,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    reasoningEffort = reasoningEffort?.name,
    contextMessageLimit = contextMessageLimit,
    updatedAt = updatedAt
)

private fun BackupChatModelSettingsDto.toEntity(): ChatModelSettingsEntity = ChatModelSettingsEntity(
    chatId = chatId,
    maxTokens = maxTokens,
    temperature = temperature,
    topP = topP,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    reasoningEffort = reasoningEffort?.let { runCatching { ReasoningEffort.valueOf(it) }.getOrNull() },
    contextMessageLimit = contextMessageLimit,
    updatedAt = updatedAt
)

private fun ModelRoleEntity.toBackup(): BackupModelRoleDto = BackupModelRoleDto(
    id = id,
    name = name,
    prompt = prompt,
    isDefault = isDefault,
    isBuiltIn = isBuiltIn
)

private fun BackupModelRoleDto.toEntity(): ModelRoleEntity = ModelRoleEntity(
    id = id,
    name = name,
    prompt = prompt,
    isDefault = isDefault,
    isBuiltIn = isBuiltIn
)

private fun ImageChatEntity.toBackup(): BackupImageChatDto = BackupImageChatDto(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId
)

private fun BackupImageChatDto.toEntity(): ImageChatEntity = ImageChatEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId
)

private fun ImageMessageEntity.toBackup(): BackupImageMessageDto = BackupImageMessageDto(
    id = id,
    chatId = chatId,
    role = role.name,
    content = content,
    imageBase64 = imageBytes?.let { Base64.getEncoder().encodeToString(it) },
    imageMimeType = imageMimeType,
    sourceMessageId = sourceMessageId,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

private fun BackupImageMessageDto.toEntity(): ImageMessageEntity = ImageMessageEntity(
    id = id,
    chatId = chatId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    imageBytes = imageBase64?.let { Base64.getDecoder().decode(it) },
    imageMimeType = imageMimeType,
    sourceMessageId = sourceMessageId,
    parentMessageId = parentMessageId,
    activeChildMessageId = activeChildMessageId,
    createdAt = createdAt
)

private fun VideoChatEntity.toBackup(): BackupVideoChatDto = BackupVideoChatDto(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId
)

private fun BackupVideoChatDto.toEntity(): VideoChatEntity = VideoChatEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId
)

private fun VideoMessageEntity.toBackup(): BackupVideoMessageDto {
    val videoBytes = videoFilePath
        ?.let { path -> runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull() }
    return BackupVideoMessageDto(
        id = id,
        chatId = chatId,
        role = role.name,
        content = content,
        sourceImageUrl = sourceImageUrl,
        videoBase64 = videoBytes?.let { Base64.getEncoder().encodeToString(it) },
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
}

private fun BackupVideoMessageDto.toEntity(restoredVideoFilePath: String?): VideoMessageEntity = VideoMessageEntity(
    id = id,
    chatId = chatId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    sourceImageUrl = sourceImageUrl,
    videoFilePath = restoredVideoFilePath,
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

private fun String?.toVideoExtension(): String {
    return when (orEmpty().substringAfterLast('/').lowercase()) {
        "quicktime", "mov" -> "mov"
        "webm" -> "webm"
        else -> "mp4"
    }
}
