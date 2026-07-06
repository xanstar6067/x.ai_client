package com.adam.xai_client.ui.chat

import com.adam.xai_client.domain.model.Message
import com.adam.xai_client.domain.model.MessageRole
import com.adam.xai_client.domain.token.TokenCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageLoadingTest {
    private val tokenCounter = TokenCounter()

    @Test
    fun `keeps persisted token counts without recalculating the whole history`() {
        val message = message(content = "short text", tokenCount = 7_777)

        val result = listOf(message).withMissingTokenCountsCalculated(tokenCounter)

        assertTrue(result === result.withMissingTokenCountsCalculated(tokenCounter))
        assertEquals(7_777, result.single().tokenCount)
    }

    @Test
    fun `calculates a missing token count for legacy messages`() {
        val message = message(content = "A legacy message without a stored count", tokenCount = 0)

        val result = listOf(message).withMissingTokenCountsCalculated(tokenCounter)

        assertTrue(result.single().tokenCount > 0)
    }

    private fun message(content: String, tokenCount: Int) = Message(
        id = 1,
        chatId = 2,
        role = MessageRole.USER,
        content = content,
        tokenCount = tokenCount,
        createdAt = 3
    )
}
