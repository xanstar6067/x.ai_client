package com.adam.xai_client.data.remote.client

import com.adam.xai_client.data.remote.api.XaiApiClient
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
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
