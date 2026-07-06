package com.adam.xai_client.ui.chatlist

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adam.xai_client.domain.model.Chat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingTheChatTitleOpensItOnTheFirstTap() {
        var openedChatId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                ChatListScreen(
                    state = ChatListUiState(chats = listOf(chat())),
                    onOpenChat = { openedChatId = it },
                    onNewChat = {},
                    onDeleteChat = {},
                    onDuplicateChat = {},
                    onOpenSettings = {},
                    onOpenRoles = {},
                    onOpenImages = {},
                    onOpenVideos = {},
                    onOpenBackups = {},
                    onErrorShown = {}
                )
            }
        }

        composeRule.onNodeWithText(CHAT_TITLE).performClick()

        composeRule.runOnIdle { assertEquals(CHAT_ID, openedChatId) }
    }

    private fun chat() = Chat(
        id = CHAT_ID,
        title = CHAT_TITLE,
        createdAt = 1,
        updatedAt = 2,
        selectedModelId = null,
        selectedRoleId = null
    )

    private companion object {
        const val CHAT_ID = 42L
        const val CHAT_TITLE = "First tap target"
    }
}
