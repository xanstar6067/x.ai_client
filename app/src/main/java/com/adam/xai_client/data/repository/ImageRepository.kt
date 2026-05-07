package com.adam.xai_client.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import java.io.File

class ImageRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val apiClient: XaiApiClient
) {
    suspend fun generateImage(options: ImageGenerationOptions): GeneratedImage {
        val prompt = options.prompt.trim()
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Введите описание изображения.")
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

    fun saveImage(image: GeneratedImage): Uri {
        val fileName = "xai_image_${System.currentTimeMillis()}.jpg"
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
    }
}
