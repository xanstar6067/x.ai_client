package com.adam.xai_client.domain.model

data class ModelRole(
    val id: Long,
    val name: String,
    val prompt: String,
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = false
)
