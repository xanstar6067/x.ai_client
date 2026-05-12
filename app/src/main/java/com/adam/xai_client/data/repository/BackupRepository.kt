package com.adam.xai_client.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import com.adam.xai_client.domain.model.MessageAttachment
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ReasoningEffort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        val export = database.withTransaction {
            val mediaEntries = mutableListOf<BackupMediaEntry>()
            val messages = database.messageDao().getAllMessages().map { message ->
                message.toBackup(mediaEntries)
            }
            val imageMessages = database.imageMessageDao().getAllMessages().map { message ->
                message.toBackup(mediaEntries)
            }
            val videoMessages = database.videoMessageDao().getAllMessages().map { message ->
                message.toBackup(mediaEntries)
            }
            BackupExport(
                backup = ChatBackupDto(
                    exportedAt = System.currentTimeMillis(),
                    chats = database.chatDao().getAllChats().map { it.toBackup() },
                    messages = messages,
                    chatModelSettings = database.chatModelSettingsDao().getAllSettings().map { it.toBackup() },
                    roles = database.modelRoleDao().getAllRoles().map { it.toBackup() },
                    imageChats = database.imageChatDao().getAllChats().map { it.toBackup() },
                    imageMessages = imageMessages,
                    videoChats = database.videoChatDao().getAllChats().map { it.toBackup() },
                    videoMessages = videoMessages
                ),
                mediaEntries = mediaEntries
            )
        }
        val fileName = "xai_chat_backup_${fileTimestamp()}.zip"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportScoped(fileName) { output -> writeZipBackup(output, export) }
        } else {
            exportLegacy(fileName) { output -> writeZipBackup(output, export) }
        }
    }

    suspend fun importBackup(uri: Uri): BackupImportSummary {
        val importedBackup = readBackup(uri)
        val backup = importedBackup.backup
        val existingImagePaths = database.imageMessageDao().getAllMessages()
            .mapNotNull { it.imageFilePath }
            .toSet()
        val existingVideoPaths = database.videoMessageDao().getAllMessages()
            .mapNotNull { it.videoFilePath }
            .toSet()
        val existingAttachmentPaths = database.messageDao().getAllMessages()
            .flatMap { it.attachments }
            .map { it.filePath }
            .toSet()

        val restoredImagePaths = mutableSetOf<String>()
        val restoredVideoPaths = mutableSetOf<String>()
        val restoredAttachmentPaths = mutableSetOf<String>()

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

            database.messageDao().insertMessages(
                backup.messages.map { messageDto ->
                    val restoredAttachments = messageDto.attachments.map { attachmentDto ->
                        val restoredPath = restoreAttachmentFile(
                            archivePath = attachmentDto.archivePath,
                            importedMedia = importedBackup.media,
                            originalFilePath = attachmentDto.filePath,
                            displayName = attachmentDto.displayName
                        )
                        com.adam.xai_client.domain.model.MessageAttachment(
                            kind = attachmentDto.kind,
                            displayName = attachmentDto.displayName,
                            mimeType = attachmentDto.mimeType,
                            filePath = restoredPath ?: attachmentDto.filePath,
                            sizeBytes = attachmentDto.sizeBytes
                        )
                    }
                    messageDto.toEntity(restoredAttachments).also { entity ->
                        entity.attachments.forEach { restoredAttachmentPaths.add(it.filePath) }
                    }
                }
            )

            database.imageMessageDao().insertMessages(
                backup.imageMessages.map { message ->
                    val imageFilePath = restoreImageFile(
                        archivePath = message.imageArchivePath,
                        importedMedia = importedBackup.media,
                        imageBase64 = message.imageBase64,
                        mimeType = message.imageMimeType
                    ) ?: message.imageFilePath
                    message.toEntity(imageFilePath).also { entity ->
                        entity.imageFilePath?.let(restoredImagePaths::add)
                    }
                }
            )
            database.videoChatDao().insertChats(backup.videoChats.map { it.toEntity() })
            database.videoMessageDao().insertMessages(
                backup.videoMessages.map { message ->
                    val videoFilePath = restoreVideoFile(
                        archivePath = message.videoArchivePath,
                        importedMedia = importedBackup.media,
                        videoBase64 = message.videoBase64,
                        mimeType = message.videoMimeType
                    ) ?: message.videoFilePath

                    val restoredSourceImageUrl = if (message.sourceImageArchivePath != null) {
                        restoreAttachmentFile(
                            archivePath = message.sourceImageArchivePath,
                            importedMedia = importedBackup.media,
                            originalFilePath = message.sourceImageUrl ?: "",
                            displayName = "source_image"
                        )
                    } else {
                        null
                    }

                    message.toEntity(videoFilePath, restoredSourceImageUrl).also { entity ->
                        entity.videoFilePath?.let(restoredVideoPaths::add)
                        entity.sourceImageUrl?.let { path ->
                            if (path.startsWith("/") || path.contains(APP_ATTACHMENTS_DIR)) {
                                restoredAttachmentPaths.add(path)
                            }
                        }
                    }
                }
            )
        }
        existingImagePaths
            .filterNot { it in restoredImagePaths }
            .forEach { deleteAppFile(it, APP_IMAGES_DIR) }
        existingVideoPaths
            .filterNot { it in restoredVideoPaths }
            .forEach { deleteAppFile(it, APP_VIDEOS_DIR) }
        existingAttachmentPaths
            .filterNot { it in restoredAttachmentPaths }
            .forEach { deleteAppFile(it, APP_ATTACHMENTS_DIR) }

        importedBackup.cleanup()
        return BackupImportSummary(
            chatCount = backup.chats.size,
            imageChatCount = backup.imageChats.size,
            videoChatCount = backup.videoChats.size
        )
    }

    private suspend fun readJsonBackup(input: InputStream): ImportedBackup {
        val content = input.bufferedReader().readText()
        return ImportedBackup(json.decodeFromString<ChatBackupDto>(content), emptyMap())
    }

    private suspend fun readZipBackup(input: InputStream): ImportedBackup {
        val media = mutableMapOf<String, File>()
        var backup: ChatBackupDto? = null
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory) {
                    when (entry.name) {
                        BACKUP_JSON_ENTRY -> {
                            backup = json.decodeFromString<ChatBackupDto>(zip.readEntryText())
                        }
                        else -> if (entry.name.startsWith(MEDIA_ENTRY_PREFIX)) {
                            media[entry.name] = copyZipEntryToTempFile(zip, entry.name)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return ImportedBackup(
            backup = backup ?: throw IllegalStateException("Backup archive does not contain $BACKUP_JSON_ENTRY."),
            media = media
        )
    }

    private suspend fun readBackup(uri: Uri): ImportedBackup {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            if (isZipBackup(uri)) {
                readZipBackup(input)
            } else {
                readJsonBackup(input)
            }
        } ?: throw IllegalStateException("Unable to open backup file.")
    }

    private fun isZipBackup(uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri).orEmpty()
        return type == ZIP_MIME_TYPE ||
            uri.toString().endsWith(".zip", ignoreCase = true) ||
            getDisplayName(uri)?.endsWith(".zip", ignoreCase = true) == true
    }

    private fun getDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
    }

    private fun writeZipBackup(output: OutputStream, export: BackupExport) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
            zip.write(json.encodeToString(export.backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            export.mediaEntries.forEach { media ->
                zip.putNextEntry(ZipEntry(media.archivePath))
                media.writeTo(zip)
                zip.closeEntry()
            }
        }
    }

    private fun copyZipEntryToTempFile(zip: ZipInputStream, archivePath: String): File {
        val tempDir = File(context.cacheDir, IMPORT_TEMP_DIR)
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw IllegalStateException("Unable to create backup import cache.")
        }
        val file = File(tempDir, "${System.currentTimeMillis()}_${System.nanoTime()}_${File(archivePath).name}")
        file.outputStream().use { output -> zip.copyTo(output) }
        return file
    }

    private fun ZipInputStream.readEntryText(): String {
        val bytes = readBytes()
        return bytes.toString(Charsets.UTF_8)
    }

    private fun exportScoped(fileName: String, writer: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, ZIP_MIME_TYPE)
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
            resolver.openOutputStream(uri)?.use(writer)
                ?: throw IllegalStateException("Unable to write backup file.")
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
    private fun exportLegacy(fileName: String, writer: (OutputStream) -> Unit): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadsDir, BACKUP_DIR)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IllegalStateException("Unable to create Downloads/$BACKUP_DIR.")
        }
        val file = File(backupDir, fileName)
        file.outputStream().use(writer)
        return Uri.fromFile(file)
    }

    private fun fileTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun restoreImageFile(
        archivePath: String?,
        importedMedia: Map<String, File>,
        imageBase64: String?,
        mimeType: String?
    ): String? {
        val sourceFile = archivePath?.let(importedMedia::get)
        val bytes = if (sourceFile == null) {
            imageBase64?.let { Base64.getDecoder().decode(it) }
        } else {
            null
        }
        return restoreMediaFile(
            sourceFile = sourceFile,
            bytes = bytes,
            storageDirName = APP_IMAGES_DIR,
            filePrefix = "restored_image",
            extension = mimeType.toImageExtension()
        )
    }

    private fun restoreVideoFile(
        archivePath: String?,
        importedMedia: Map<String, File>,
        videoBase64: String?,
        mimeType: String?
    ): String? {
        val sourceFile = archivePath?.let(importedMedia::get)
        val bytes = if (sourceFile == null) {
            videoBase64?.let { Base64.getDecoder().decode(it) }
        } else {
            null
        }
        return restoreMediaFile(
            sourceFile = sourceFile,
            bytes = bytes,
            storageDirName = APP_VIDEOS_DIR,
            filePrefix = "restored_video",
            extension = mimeType.toVideoExtension()
        )
    }

    private fun restoreAttachmentFile(
        archivePath: String?,
        importedMedia: Map<String, File>,
        originalFilePath: String,
        displayName: String
    ): String? {
        val sourceFile = archivePath?.let(importedMedia::get) ?: return null

        return restoreMediaFile(
            sourceFile = sourceFile,
            bytes = null,
            storageDirName = APP_ATTACHMENTS_DIR,
            filePrefix = "restored_attachment",
            extension = displayName.substringAfterLast('.', "bin")
        )
    }

    private fun restoreMediaFile(
        sourceFile: File?,
        bytes: ByteArray?,
        storageDirName: String,
        filePrefix: String,
        extension: String
    ): String? {
        if (sourceFile == null && bytes == null) return null
        val dir = File(context.filesDir, storageDirName)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Unable to create media storage directory.")
        }
        val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}_${System.nanoTime()}.$extension")
        if (sourceFile != null) {
            sourceFile.inputStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            file.writeBytes(bytes ?: return null)
        }
        return file.absolutePath
    }

    private fun deleteAppFile(path: String, storageDirName: String) {
        runCatching {
            val storageRoot = File(context.filesDir, storageDirName).canonicalFile
            val target = File(path).canonicalFile
            if (target.path.startsWith(storageRoot.path) && target.exists()) {
                target.delete()
            }
        }
    }

    private companion object {
        const val BACKUP_DIR = "xAI Chat Backups"
        const val BACKUP_JSON_ENTRY = "backup.json"
        const val MEDIA_ENTRY_PREFIX = "media/"
        const val ZIP_MIME_TYPE = "application/zip"
        const val IMPORT_TEMP_DIR = "backup_import"
        const val APP_IMAGES_DIR = "generated_images"
        const val APP_VIDEOS_DIR = "generated_videos"
        const val APP_ATTACHMENTS_DIR = "chat_attachments"
    }
}

private data class BackupExport(
    val backup: ChatBackupDto,
    val mediaEntries: List<BackupMediaEntry>
)

private data class ImportedBackup(
    val backup: ChatBackupDto,
    val media: Map<String, File>
) {
    fun cleanup() {
        media.values.forEach { it.delete() }
    }
}

private data class BackupMediaEntry(
    val archivePath: String,
    val sourceFile: File? = null,
    val bytes: ByteArray? = null
) {
    fun writeTo(output: OutputStream) {
        if (sourceFile != null) {
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } else {
            output.write(bytes ?: ByteArray(0))
        }
    }
}

data class BackupImportSummary(
    val chatCount: Int,
    val imageChatCount: Int,
    val videoChatCount: Int
)

@Serializable
private data class ChatBackupDto(
    val schemaVersion: Int = 5,
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
private data class BackupAttachmentDto(
    val kind: com.adam.xai_client.domain.model.MessageAttachmentKind,
    val displayName: String,
    val mimeType: String,
    val filePath: String,
    val sizeBytes: Long,
    val archivePath: String? = null
)

@Serializable
private data class BackupChatDto(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selectedModelId: String?,
    val selectedRoleId: Long?,
    val cachedTokenCount: Int = 0
)

@Serializable
private data class BackupMessageDto(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val attachments: List<BackupAttachmentDto> = emptyList(),
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
    val webSearchEnabled: Boolean = false,
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
    val imageArchivePath: String? = null,
    val imageFilePath: String? = null,
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
    val videoArchivePath: String? = null,
    val videoFilePath: String?,
    val videoMimeType: String?,
    val videoDurationSeconds: Int?,
    val videoRespectModeration: Boolean?,
    val requestId: String?,
    val aspectRatio: String?,
    val resolution: String?,
    val parentMessageId: Long?,
    val activeChildMessageId: Long?,
    val createdAt: Long,
    val sourceImageArchivePath: String? = null
)

private fun ChatEntity.toBackup(): BackupChatDto = BackupChatDto(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId,
    selectedRoleId = selectedRoleId,
    cachedTokenCount = cachedTokenCount
)

private fun BackupChatDto.toEntity(): ChatEntity = ChatEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    selectedModelId = selectedModelId,
    selectedRoleId = selectedRoleId,
    cachedTokenCount = cachedTokenCount
)

private fun MessageEntity.toBackup(mediaEntries: MutableList<BackupMediaEntry>): BackupMessageDto {
    val backupAttachments = attachments.mapIndexed { index, attachment ->
        val file = File(attachment.filePath)
        val archivePath = if (file.exists()) {
            val extension = attachment.displayName.substringAfterLast('.', "")
            "media/attachments/msg_${id}_att_${index}.$extension"
        } else {
            null
        }
        if (archivePath != null) {
            mediaEntries += BackupMediaEntry(
                archivePath = archivePath,
                sourceFile = file
            )
        }
        BackupAttachmentDto(
            kind = attachment.kind,
            displayName = attachment.displayName,
            mimeType = attachment.mimeType,
            filePath = attachment.filePath,
            sizeBytes = attachment.sizeBytes,
            archivePath = archivePath
        )
    }
    return BackupMessageDto(
        id = id,
        chatId = chatId,
        role = role.name,
        content = content,
        attachments = backupAttachments,
        reasoningContent = reasoningContent,
        tokenCount = tokenCount,
        parentMessageId = parentMessageId,
        activeChildMessageId = activeChildMessageId,
        createdAt = createdAt
    )
}

private fun BackupMessageDto.toEntity(restoredAttachments: List<com.adam.xai_client.domain.model.MessageAttachment>): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    attachments = restoredAttachments,
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
    webSearchEnabled = webSearchEnabled,
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
    webSearchEnabled = webSearchEnabled,
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

private fun ImageMessageEntity.toBackup(mediaEntries: MutableList<BackupMediaEntry>): BackupImageMessageDto {
    val archivePath = imageMediaArchivePath()
    if (archivePath != null) {
        val sourceFile = imageFilePath?.let(::File)?.takeIf { it.exists() }
        mediaEntries += BackupMediaEntry(
            archivePath = archivePath,
            sourceFile = sourceFile,
            bytes = if (sourceFile == null) imageBytes else null
        )
    }
    return BackupImageMessageDto(
        id = id,
        chatId = chatId,
        role = role.name,
        content = content,
        imageBase64 = null,
        imageArchivePath = archivePath,
        imageFilePath = imageFilePath,
        imageMimeType = imageMimeType,
        sourceMessageId = sourceMessageId,
        parentMessageId = parentMessageId,
        activeChildMessageId = activeChildMessageId,
        createdAt = createdAt
    )
}

private fun BackupImageMessageDto.toEntity(restoredImageFilePath: String?): ImageMessageEntity = ImageMessageEntity(
    id = id,
    chatId = chatId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    imageBytes = if (restoredImageFilePath == null) imageBase64?.let { Base64.getDecoder().decode(it) } else null,
    imageFilePath = restoredImageFilePath,
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

private fun VideoMessageEntity.toBackup(mediaEntries: MutableList<BackupMediaEntry>): BackupVideoMessageDto {
    val videoSourceFile = videoFilePath?.let(::File)?.takeIf { it.exists() }
    val videoArchivePath = videoSourceFile?.let {
        "media/videos/video_message_${id}.${videoMimeType.toVideoExtension()}"
    }
    if (videoArchivePath != null) {
        mediaEntries += BackupMediaEntry(
            archivePath = videoArchivePath,
            sourceFile = videoSourceFile
        )
    }

    val sourceImageFile = sourceImageUrl?.let(::File)?.takeIf { it.exists() }
    val sourceImageArchivePath = sourceImageFile?.let {
        "media/videos/video_source_${id}.${it.extension}"
    }
    if (sourceImageArchivePath != null) {
        mediaEntries += BackupMediaEntry(
            archivePath = sourceImageArchivePath,
            sourceFile = sourceImageFile
        )
    }

    return BackupVideoMessageDto(
        id = id,
        chatId = chatId,
        role = role.name,
        content = content,
        sourceImageUrl = sourceImageUrl,
        videoBase64 = null,
        videoArchivePath = videoArchivePath,
        videoFilePath = videoFilePath,
        videoMimeType = videoMimeType,
        videoDurationSeconds = videoDurationSeconds,
        videoRespectModeration = videoRespectModeration,
        requestId = requestId,
        aspectRatio = aspectRatio,
        resolution = resolution,
        parentMessageId = parentMessageId,
        activeChildMessageId = activeChildMessageId,
        createdAt = createdAt,
        sourceImageArchivePath = sourceImageArchivePath
    )
}

private fun BackupVideoMessageDto.toEntity(
    restoredVideoFilePath: String?,
    restoredSourceImageUrl: String?
): VideoMessageEntity = VideoMessageEntity(
    id = id,
    chatId = chatId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    content = content,
    sourceImageUrl = restoredSourceImageUrl ?: sourceImageUrl,
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

private fun ImageMessageEntity.imageMediaArchivePath(): String? {
    if (imageBytes == null && imageFilePath?.let { File(it).exists() } != true) return null
    return "media/images/image_message_${id}.${imageMimeType.toImageExtension()}"
}

private fun String?.toImageExtension(): String {
    return when (orEmpty().substringAfterLast('/').lowercase()) {
        "png" -> "png"
        "webp" -> "webp"
        else -> "jpg"
    }
}

private fun String?.toVideoExtension(): String {
    return when (orEmpty().substringAfterLast('/').lowercase()) {
        "quicktime", "mov" -> "mov"
        "webm" -> "webm"
        else -> "mp4"
    }
}
