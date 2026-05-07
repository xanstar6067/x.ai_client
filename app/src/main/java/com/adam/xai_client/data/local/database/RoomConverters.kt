package com.adam.xai_client.data.local.database

import androidx.room.TypeConverter
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.model.ReasoningEffort

class RoomConverters {
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
}
