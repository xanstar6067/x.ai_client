package com.adam.xai_client.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: String
)
