package com.adam.xai_client.domain.model

data class ApiSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.x.ai/v1"
    }
}
