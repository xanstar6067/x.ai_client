package com.adam.xai_client.data.local.database

import androidx.room.TypeConverter
import com.adam.xai_client.domain.model.MessageAttachment
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ReasoningEffort
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class RoomConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun messageRoleToString(role: MessageRole): String = role.name

    @TypeConverter
    fun stringToMessageRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun reasoningEffortToString(reasoningEffort: ReasoningEffort?): String? = reasoningEffort?.name

    @TypeConverter
    fun stringToReasoningEffort(value: String?): ReasoningEffort? = value?.let {
        ReasoningEffort.valueOf(it)
    }

    @TypeConverter
    fun messageAttachmentsToString(value: List<MessageAttachment>): String {
        return json.encodeToString(ListSerializer(MessageAttachment.serializer()), value)
    }

    @TypeConverter
    fun stringToMessageAttachments(value: String?): List<MessageAttachment> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(MessageAttachment.serializer()), value)
        }.getOrDefault(emptyList())
    }
}
