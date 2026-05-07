package com.adam.xai_client.ui.components

import com.adam.xai_client.data.remote.client.XaiApiException
import com.adam.xai_client.domain.usecase.MessageSendFailedException
import com.adam.xai_client.domain.usecase.UserFacingException
import java.io.IOException

fun Throwable.toUserMessage(): String {
    return when (this) {
        is UserFacingException -> message
        is MessageSendFailedException -> message
        is XaiApiException -> message
        is IllegalArgumentException -> message ?: "Некорректные данные."
        is IllegalStateException -> message ?: "Действие сейчас недоступно."
        is IOException -> "Нет интернета или сервер недоступен."
        else -> message ?: "Что-то пошло не так."
    }
}
