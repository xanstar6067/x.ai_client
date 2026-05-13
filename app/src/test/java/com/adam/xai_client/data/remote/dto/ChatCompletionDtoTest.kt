package com.adam.xai_client.data.remote.dto

import com.adam.xai_client.domain.model.ChatModelSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionDtoTest {
    @Test
    fun `chat completions request skips messages without api content`() {
        val request = chatCompletionRequestDto(
            model = "grok-test",
            messages = listOf(
                ApiChatMessage(role = "user", content = "Hello"),
                ApiChatMessage(role = "assistant", content = "", reasoningContent = "thinking"),
                ApiChatMessage(role = "user", content = "", attachments = emptyList())
            ),
            stream = false,
            settings = ChatModelSettings()
        )

        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages.single().role)
    }

    @Test
    fun `chat completions request serializes text messages as content arrays`() {
        val request = chatCompletionRequestDto(
            model = "grok-test",
            messages = listOf(
                ApiChatMessage(role = "user", content = "Hello")
            ),
            stream = false,
            settings = ChatModelSettings()
        )

        val content = request.messages.single().content
        assertTrue(content is JsonArray)
        val block = (content as JsonArray).single() as JsonObject
        assertEquals("\"text\"", block["type"].toString())
        assertEquals("\"Hello\"", block["text"].toString())
    }

    @Test
    fun `multi agent responses request keeps messages with usable attachment payloads`() {
        val request = responsesRequestDto(
            model = "grok-4.20-multi-agent",
            messages = listOf(
                ApiChatMessage(role = "assistant", content = ""),
                ApiChatMessage(
                    role = "user",
                    content = "",
                    attachments = listOf(
                        ApiMessageAttachment(
                            kind = ApiMessageAttachmentKind.DOCUMENT,
                            fileId = "file-123"
                        )
                    )
                )
            ),
            stream = false,
            settings = ChatModelSettings()
        )

        val input = request.input as JsonArray
        val message = input.single() as JsonObject
        assertEquals("\"user\"", message["role"].toString())
        assertEquals(1, (message["content"] as JsonArray).size)
    }

    @Test
    fun `multi agent responses request serializes text messages as strings`() {
        val request = responsesRequestDto(
            model = "grok-4.20-multi-agent",
            messages = listOf(
                ApiChatMessage(role = "user", content = "Hello")
            ),
            stream = false,
            settings = ChatModelSettings()
        )

        val input = request.input as JsonArray
        val message = input.single() as JsonObject
        val content = message["content"]
        assertTrue(content is JsonPrimitive)
        assertEquals("\"Hello\"", content.toString())
    }

    @Test
    fun `multi agent stateless request keeps non empty assistant history messages`() {
        val request = responsesRequestDto(
            model = "grok-4.20-multi-agent",
            messages = listOf(
                ApiChatMessage(role = "system", content = "Be concise"),
                ApiChatMessage(role = "user", content = "Hello"),
                ApiChatMessage(role = "assistant", content = "Hi"),
                ApiChatMessage(role = "user", content = "Next question")
            ),
            stream = false,
            settings = ChatModelSettings()
        )

        val input = request.input as JsonArray
        assertEquals(
            listOf("\"system\"", "\"user\"", "\"assistant\"", "\"user\""),
            input.map { (it as JsonObject)["role"].toString() }
        )
    }

    @Test
    fun `multi agent continuation serializes follow up as input string`() {
        val request = responsesRequestDto(
            model = "grok-4.20-multi-agent",
            messages = listOf(
                ApiChatMessage(role = "user", content = "Follow up")
            ),
            stream = false,
            settings = ChatModelSettings(),
            previousResponseId = "resp_123"
        )

        val input = request.input
        assertTrue(input is JsonPrimitive)
        assertEquals("\"Follow up\"", input.toString())
    }

    @Test
    fun `chat completions request skips messages that become empty content arrays`() {
        val request = chatCompletionRequestDto(
            model = "grok-test",
            messages = listOf(
                ApiChatMessage(
                    role = "user",
                    content = "",
                    attachments = listOf(
                        ApiMessageAttachment(
                            kind = ApiMessageAttachmentKind.DOCUMENT,
                            fileId = "file-123"
                        )
                    )
                ),
                ApiChatMessage(role = "user", content = "Next question")
            ),
            stream = false,
            settings = ChatModelSettings()
        )

        assertEquals(1, request.messages.size)
        val content = request.messages.single().content as JsonArray
        val block = content.single() as JsonObject
        assertEquals("\"Next question\"", block["text"].toString())
    }
}
