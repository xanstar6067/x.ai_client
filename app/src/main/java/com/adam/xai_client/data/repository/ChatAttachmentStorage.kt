package com.adam.xai_client.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.adam.xai_client.domain.model.MessageAttachment
import com.adam.xai_client.domain.model.MessageAttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

class ChatAttachmentStorage(
    private val context: Context
) {
    suspend fun copyAttachment(uri: Uri, expectedKind: MessageAttachmentKind): MessageAttachment {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = resolver.displayName(uri) ?: "attachment"
            val mimeType = resolver.getType(uri)
                ?: displayName.mimeTypeFromExtension()
                ?: "application/octet-stream"
            val sizeBytes = resolver.size(uri) ?: 0L
            validate(expectedKind, displayName, mimeType, sizeBytes)

            val storageDir = File(context.filesDir, APP_ATTACHMENTS_DIR)
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                throw IllegalStateException("Cannot create attachments directory.")
            }
            val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                ?: mimeType.substringAfterLast('/').ifBlank { "bin" }
            val file = File(
                storageDir,
                "attachment_${System.currentTimeMillis()}_${System.nanoTime()}.$extension"
            )
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot open selected file.")

            MessageAttachment(
                kind = expectedKind,
                displayName = displayName,
                mimeType = mimeType,
                filePath = file.absolutePath,
                sizeBytes = file.length().takeIf { it > 0L } ?: sizeBytes
            )
        }
    }

    fun deleteAttachment(attachment: MessageAttachment) {
        runCatching {
            val root = File(context.filesDir, APP_ATTACHMENTS_DIR).canonicalFile
            val file = File(attachment.filePath).canonicalFile
            if (file.path.startsWith(root.path) && file.exists()) {
                file.delete()
            }
        }
    }

    fun toDataUrl(attachment: MessageAttachment): String? {
        val file = File(attachment.filePath)
        if (!file.exists()) return null
        val base64 = Base64.getEncoder().encodeToString(file.readBytes())
        return "data:${attachment.mimeType};base64,$base64"
    }

    private fun validate(
        expectedKind: MessageAttachmentKind,
        displayName: String,
        mimeType: String,
        sizeBytes: Long
    ) {
        when (expectedKind) {
            MessageAttachmentKind.IMAGE -> {
                if (!mimeType.isSupportedImageMime() && !displayName.hasExtension("jpg", "jpeg", "png")) {
                    throw IllegalArgumentException("Поддерживаются только JPG и PNG изображения.")
                }
                if (sizeBytes > IMAGE_MAX_BYTES) {
                    throw IllegalArgumentException("Изображение должно быть не больше 20 MiB.")
                }
            }
            MessageAttachmentKind.DOCUMENT -> {
                if (!mimeType.isSupportedDocumentMime() && !displayName.hasExtension(*SUPPORTED_DOCUMENT_EXTENSIONS)) {
                    throw IllegalArgumentException("Этот тип документа пока не поддерживается.")
                }
                if (sizeBytes > DOCUMENT_MAX_BYTES) {
                    throw IllegalArgumentException("Документ должен быть не больше 48 MB.")
                }
            }
            MessageAttachmentKind.VIDEO -> {
                if (!mimeType.startsWith("video/")) {
                    throw IllegalArgumentException("Выберите видеофайл.")
                }
                if (sizeBytes > DOCUMENT_MAX_BYTES) {
                    throw IllegalArgumentException("Видео должно быть не больше 48 MB.")
                }
            }
        }
    }

    private fun android.content.ContentResolver.displayName(uri: Uri): String? {
        return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() }
    }

    private fun android.content.ContentResolver.size(uri: Uri): Long? {
        return query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    private fun String.isSupportedImageMime(): Boolean {
        return equals("image/jpeg", ignoreCase = true) || equals("image/png", ignoreCase = true)
    }

    private fun String.isSupportedDocumentMime(): Boolean {
        val normalized = lowercase()
        return normalized.startsWith("text/") ||
            normalized in setOf(
                "application/pdf",
                "application/json",
                "application/xml",
                "application/csv",
                "text/csv"
            )
    }

    private fun String.hasExtension(vararg extensions: String): Boolean {
        val extension = substringAfterLast('.', "").lowercase()
        return extension in extensions
    }

    private fun String.mimeTypeFromExtension(): String? {
        return when (substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "md", "markdown" -> "text/markdown"
            "txt", "log", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "xml", "yaml", "yml" -> "text/plain"
            else -> null
        }
    }

    private companion object {
        const val APP_ATTACHMENTS_DIR = "chat_attachments"
        const val IMAGE_MAX_BYTES = 20L * 1024L * 1024L
        const val DOCUMENT_MAX_BYTES = 48L * 1024L * 1024L
        val SUPPORTED_DOCUMENT_EXTENSIONS = arrayOf(
            "txt",
            "md",
            "markdown",
            "csv",
            "json",
            "pdf",
            "kt",
            "kts",
            "java",
            "py",
            "js",
            "ts",
            "tsx",
            "jsx",
            "html",
            "css",
            "xml",
            "yaml",
            "yml",
            "log"
        )
    }
}
