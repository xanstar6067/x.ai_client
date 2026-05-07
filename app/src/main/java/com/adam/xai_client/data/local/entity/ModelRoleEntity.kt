package com.adam.xai_client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_roles")
data class ModelRoleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val prompt: String,
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = false
)
