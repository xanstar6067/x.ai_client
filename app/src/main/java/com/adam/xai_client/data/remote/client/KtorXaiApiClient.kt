package com.adam.xai_client.data.remote.client

import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.api.ChatStreamDelta
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.remote.dto.ChatCompletionResponseDto
import com.adam.xai_client.data.remote.dto.ImageEditRequestDto
import com.adam.xai_client.data.remote.dto.ImageGenerationRequestDto
import com.adam.xai_client.data.remote.dto.ImageReferenceDto
import com.adam.xai_client.data.remote.dto.ImageResponseDto
import com.adam.xai_client.data.remote.dto.ModelsResponseDto
import com.adam.xai_client.data.remote.dto.ResponsesResponseDto
import com.adam.xai_client.data.remote.dto.ResponsesStreamEventDto
import com.adam.xai_client.data.remote.dto.asDomain
import com.adam.xai_client.data.remote.dto.chatCompletionRequestDto
import com.adam.xai_client.data.remote.dto.outputTextContent
import com.adam.xai_client.data.remote.dto.responsesRequestDto
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.domain.model.ChatModelSettings
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.domain.model.ImageGenerationOptions
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class KtorXaiApiClient(
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 360_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 360_000
        }
    }
) : XaiApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getModels(apiKey: String, baseUrl: String): List<AiModel> {
        val response = httpClient.get(endpoint(baseUrl, "/models")) {
            bearerAuth(apiKey)
        }
        response.ensureSuccess()
        return response.body<ModelsResponseDto>().data.map { dto ->
            AiModel(id = dto.id, name = dto.id)
        }
    }

    override suspend fun sendChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings
    ): String {
        if (modelId.usesResponsesApi()) {
            return sendResponsesRequest(
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelId = modelId,
                messages = messages,
                modelSettings = modelSettings
            )
        }

        val response = httpClient.post(endpoint(baseUrl, "/chat/completions")) {
            bearerAuth(apiKey)
            accept(ContentType.Text.EventStream)
            headers {
                append(HttpHeaders.CacheControl, "no-cache")
            }
            contentType(ContentType.Application.Json)
            setBody(
                chatCompletionRequestDto(
                    model = modelId,
                    messages = messages,
                    stream = false,
                    settings = modelSettings
                )
            )
        }
        response.ensureSuccess()
        val completion = response.body<ChatCompletionResponseDto>()
        return completion.choices.firstOrNull()?.message?.content.orEmpty()
    }

    override fun streamChatRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings
    ): Flow<ChatStreamDelta> {
        return if (modelId.usesResponsesApi()) {
            streamResponsesRequest(
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelId = modelId,
                messages = messages,
                modelSettings = modelSettings
            )
        } else {
            streamChatCompletionsRequest(
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelId = modelId,
                messages = messages,
                modelSettings = modelSettings
            )
        }
    }

    override suspend fun generateImage(
        apiKey: String,
        baseUrl: String,
        options: ImageGenerationOptions
    ): GeneratedImage {
        val sourceImageUrl = options.sourceImageUrl?.trim().orEmpty()
        val path = if (sourceImageUrl.isBlank()) "/images/generations" else "/images/edits"
        val response = httpClient.post(endpoint(baseUrl, path)) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            if (sourceImageUrl.isBlank()) {
                setBody(
                    ImageGenerationRequestDto(
                        model = options.modelId,
                        prompt = options.prompt,
                        n = 1,
                        aspectRatio = options.aspectRatio,
                        resolution = options.resolution
                    )
                )
            } else {
                setBody(
                    ImageEditRequestDto(
                        model = options.modelId,
                        prompt = options.prompt,
                        image = ImageReferenceDto(url = sourceImageUrl),
                        aspectRatio = options.aspectRatio,
                        resolution = options.resolution
                    )
                )
            }
        }
        response.ensureSuccess()
        val body = response.body<ImageResponseDto>()
        val imageData = body.data.firstOrNull()
            ?: throw XaiApiException(200, "Image API did not return image data.")
        imageData.b64Json?.let { base64 ->
            return GeneratedImage(bytes = Base64.getDecoder().decode(base64))
        }
        val imageUrl = imageData.url
            ?: throw XaiApiException(200, "Image API did not return image data.")
        return downloadGeneratedImage(imageUrl)
    }

    private suspend fun downloadGeneratedImage(url: String): GeneratedImage {
        val response = httpClient.get(url)
        response.ensureSuccess()
        val mimeType = response.headers[HttpHeaders.ContentType]
            ?.substringBefore(";")
            ?.ifBlank { null }
            ?: "image/jpeg"
        return GeneratedImage(
            bytes = response.body(),
            mimeType = mimeType
        )
    }

    private suspend fun sendResponsesRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings
    ): String {
        val response = httpClient.post(endpoint(baseUrl, "/responses")) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                responsesRequestDto(
                    model = modelId,
                    messages = messages,
                    stream = false,
                    settings = modelSettings.forResponsesApi(modelId)
                )
            )
        }
        response.ensureSuccess()
        return response.body<ResponsesResponseDto>().outputTextContent()
    }

    private fun streamChatCompletionsRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings
    ): Flow<ChatStreamDelta> = flow {
        val response = httpClient.post(endpoint(baseUrl, "/chat/completions")) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                chatCompletionRequestDto(
                    model = modelId,
                    messages = messages,
                    stream = true,
                    settings = modelSettings
                )
            )
        }
        response.ensureSuccess()

        val channel = response.bodyAsChannel()
        val eventLines = mutableListOf<String>()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.startsWith("{") && eventLines.isEmpty()) {
                emitCompletion(line)
                return@flow
            }
            if (line.isBlank()) {
                emitEvent(eventLines.joinToString(separator = "\n"))
                eventLines.clear()
            } else if (line.startsWith("data:")) {
                eventLines += line.removePrefix("data:").trimStart()
            }
        }
        if (eventLines.isNotEmpty()) {
            emitEvent(eventLines.joinToString(separator = "\n"))
        }
    }

    private fun streamResponsesRequest(
        apiKey: String,
        baseUrl: String,
        modelId: String,
        messages: List<ApiChatMessage>,
        modelSettings: ChatModelSettings
    ): Flow<ChatStreamDelta> = flow {
        val response = httpClient.post(endpoint(baseUrl, "/responses")) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                responsesRequestDto(
                    model = modelId,
                    messages = messages,
                    stream = true,
                    settings = modelSettings.forResponsesApi(modelId)
                )
            )
        }
        response.ensureSuccess()

        val channel = response.bodyAsChannel()
        val eventLines = mutableListOf<String>()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.startsWith("{") && eventLines.isEmpty()) {
                throwIfApiError(line)
                val completion = json.decodeFromString<ResponsesResponseDto>(line)
                val text = completion.outputTextContent()
                val tokenUsage = completion.usage?.asDomain()
                if (text.isNotEmpty() || tokenUsage != null) {
                    emit(ChatStreamDelta(content = text, tokenUsage = tokenUsage))
                }
                return@flow
            }
            if (line.isBlank()) {
                emitResponsesEvent(eventLines.joinToString(separator = "\n"))
                eventLines.clear()
            } else if (line.startsWith("data:")) {
                eventLines += line.removePrefix("data:").trimStart()
            }
        }
        if (eventLines.isNotEmpty()) {
            emitResponsesEvent(eventLines.joinToString(separator = "\n"))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamDelta>.emitCompletion(data: String) {
        throwIfApiError(data)
        val completion = json.decodeFromString<ChatCompletionResponseDto>(data)
        val message = completion.choices.firstOrNull()?.message ?: return
        val content = message.content.orEmpty()
        val reasoningContent = message.reasoning_content.orEmpty()
        val tokenUsage = completion.usage?.asDomain()
        if (content.isNotEmpty() || reasoningContent.isNotEmpty() || tokenUsage != null) {
            emit(
                ChatStreamDelta(
                    content = content,
                    reasoningContent = reasoningContent,
                    tokenUsage = tokenUsage
                )
            )
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamDelta>.emitEvent(data: String) {
        val payload = data.trim()
        if (payload.isBlank() || payload == "[DONE]") return

        throwIfApiError(payload)
        val chunk = json.decodeFromString<ChatCompletionResponseDto>(payload)
        val delta = chunk.choices.firstOrNull()?.delta
        val content = delta?.content.orEmpty()
        val reasoningContent = delta?.reasoning_content.orEmpty()
        val tokenUsage = chunk.usage?.asDomain()
        if (content.isNotEmpty() || reasoningContent.isNotEmpty() || tokenUsage != null) {
            emit(
                ChatStreamDelta(
                    content = content,
                    reasoningContent = reasoningContent,
                    tokenUsage = tokenUsage
                )
            )
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamDelta>.emitResponsesEvent(data: String) {
        val payload = data.trim()
        if (payload.isBlank() || payload == "[DONE]") return

        throwIfApiError(payload)
        val event = json.decodeFromString<ResponsesStreamEventDto>(payload)
        when (event.type) {
            "response.output_text.delta" -> event.delta?.takeIf { it.isNotEmpty() }?.let {
                emit(ChatStreamDelta(content = it))
            }
            "response.completed" -> event.response?.usage?.asDomain()?.let {
                emit(ChatStreamDelta(tokenUsage = it))
            }
        }
    }

    private fun throwIfApiError(payload: String) {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        val error = root["error"]?.jsonObjectOrNull() ?: return
        val message = error["message"]?.jsonPrimitive?.content
            ?: error["code"]?.jsonPrimitive?.content
            ?: payload.take(500)
        throw XaiApiException(200, message)
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun endpoint(baseUrl: String, path: String): String {
        return baseUrl.trim().trimEnd('/') + path
    }

    private fun String.usesResponsesApi(): Boolean {
        return lowercase().startsWith("grok-4.20-multi-agent")
    }

    private fun ChatModelSettings.forResponsesApi(modelId: String): ChatModelSettings {
        return if (modelId.usesResponsesApi()) copy(maxTokens = null) else this
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.isSuccess()) return

        val rawBody = bodyAsText().take(500)
        val readable = when (status.value) {
            401, 403 -> "Ошибка авторизации: проверьте API-ключ."
            in 500..599 -> "Ошибка сервера xAI (${status.value}). Попробуйте позже."
            else -> rawBody.ifBlank { "HTTP ${status.value}: ${status.description}" }
        }
        throw XaiApiException(status.value, readable)
    }
}

class XaiApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)
