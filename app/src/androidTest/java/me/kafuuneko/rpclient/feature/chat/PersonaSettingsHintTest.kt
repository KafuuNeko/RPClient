package me.kafuuneko.rpclient.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.ui.ChatSettingsPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.ui.GroupChatSettingsPage
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

/** 在真实设置表单上验证未聚焦的空输入框也会显示继承值。 */
class PersonaSettingsHintTest {
    @get:Rule
    val rule = createComposeRule()
    private val mSelection = PersonaSelection(selectedId = -1L, followDefaultName = "Alice")
    private val mDescription = "An archivist"

    /** 单聊自动保存输入框初次渲染时即显示默认人设内容。 */
    @Test
    fun singleChatShowsInheritedHintsBeforeFocus() = checkHints {
        ChatSettingsPage(
            session = ChatSessionItem(
                id = 1,
                title = "Chat",
                summarize = "",
                userNote = "",
                userName = "",
                userDescription = "",
                creatorNotes = "",
                autoSummaryPaused = false,
                messageCount = 0,
                enabledLorebookEntryIds = emptySet(),
                persona = mSelection,
                inheritedDescription = mDescription,
            ),
            lorebookState = ChatLorebookState(groups = emptyList()),
            loadState = ChatLoadState.None,
            showCharacterExamplesSwitch = true,
            emit = {},
        )
    }

    /** 群聊草稿字段同样在获得焦点前显示继承内容。 */
    @Test
    fun groupChatShowsInheritedHintsBeforeFocus() = checkHints {
        GroupChatSettingsPage(
            state = GroupChatSettingsState(
                persona = mSelection, inheritedDescription = mDescription,
                activationStrategy = GroupChatActivationStrategy.Manual,
            ),
            members = emptyList(),
            emitIntent = {},
        )
    }

    private fun checkHints(content: @Composable () -> Unit) {
        rule.setContent { AppTheme(dynamicColor = false) { content() } }
        for (hint in listOf("Alice", mDescription)) {
            val field = hasSetTextAction() and hasText(hint)
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(field)
            rule.onNode(field).assertIsDisplayed().assertIsNotFocused()
        }
    }
}
