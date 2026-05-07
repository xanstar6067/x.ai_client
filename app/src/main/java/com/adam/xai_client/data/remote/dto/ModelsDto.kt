package com.adam.xai_client.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponseDto(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String
)
