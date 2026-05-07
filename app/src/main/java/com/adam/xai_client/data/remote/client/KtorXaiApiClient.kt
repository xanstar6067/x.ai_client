package com.adam.xai_client.data.remote.client

import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.api.ChatStreamDelta
import com.adam.xai_client.data.remote.dto.ApiChatMessage
import com.adam.xai_client.data.remote.dto.ChatCompletionRequestDto
import com.adam.xai_client.data.remote.dto.ChatCompletionResponseDto
import com.adam.xai_client.data.remote.dto.ModelsResponseDto
import com.adam.xai_client.domain.model.AiModel
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
        messages: List<ApiChatMessage>
    ): String {
        val response = httpClient.post(endpoint(baseUrl, "/chat/completions")) {
            bearerAuth(apiKey)
            accept(ContentType.Text.EventStream)
            headers {
                append(HttpHeaders.CacheControl, "no-cache")
            }
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequestDto(
                    model = modelId,
                    messages = messages,
                    stream = false
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
        messages: List<ApiChatMessage>
    ): Flow<ChatStreamDelta> = flow {
        val response = httpClient.post(endpoint(baseUrl, "/chat/completions")) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequestDto(
                    model = modelId,
                    messages = messages,
                    stream = true
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamDelta>.emitCompletion(data: String) {
        throwIfApiError(data)
        val completion = json.decodeFromString<ChatCompletionResponseDto>(data)
        val message = completion.choices.firstOrNull()?.message ?: return
        val content = message.content.orEmpty()
        val reasoningContent = message.reasoning_content.orEmpty()
        if (content.isNotEmpty() || reasoningContent.isNotEmpty()) {
            emit(ChatStreamDelta(content = content, reasoningContent = reasoningContent))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamDelta>.emitEvent(data: String) {
        val payload = data.trim()
        if (payload.isBlank() || payload == "[DONE]") return

        throwIfApiError(payload)
        val chunk = json.decodeFromString<ChatCompletionResponseDto>(payload)
        val delta = chunk.choices.firstOrNull()?.delta ?: return
        val content = delta.content.orEmpty()
        val reasoningContent = delta.reasoning_content.orEmpty()
        if (content.isNotEmpty() || reasoningContent.isNotEmpty()) {
            emit(
                ChatStreamDelta(
                    content = content,
                    reasoningContent = reasoningContent
                )
            )
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
