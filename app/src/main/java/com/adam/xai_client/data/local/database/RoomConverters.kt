package com.adam.xai_client.data.local.database

import androidx.room.TypeConverter
import com.adam.xai_client.domain.model.MessageRole

class RoomConverters {
    @TypeConverter
    fun messageRoleToString(role: MessageRole): String = role.name

    @TypeConverter
    fun stringToMessageRole(value: String): MessageRole = MessageRole.valueOf(value)
}
