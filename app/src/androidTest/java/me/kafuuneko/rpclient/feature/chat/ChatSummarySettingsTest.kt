package me.kafuuneko.rpclient.feature.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.ChatSettingField
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.ui.ChatSettingsPage
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 覆盖单聊设置页曾将聚焦的空草稿自动写回的问题。 */
class ChatSummarySettingsTest {
    @get:Rule
    val rule = createComposeRule()

    /** 摘要回填能更新聚焦的输入框，只有用户实际清空正文才发送保存事件。 */
    @Test
    fun generatedSummaryReplacesFocusedDraftWithoutSavingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val summaryLabel = context.getString(R.string.current_summary)
        val session = mutableStateOf(
            ChatSessionItem(
                id = 1L,
                title = "Chat",
                summarize = "",
                userNote = "",
                userName = "",
                userDescription = "",
                creatorNotes = "",
                autoSummaryPaused = true,
                messageCount = 20,
                enabledLorebookEntryIds = emptySet(),
            )
        )
        val savedEdits = mutableListOf<String>()
        rule.setContent {
            AppTheme(dynamicColor = false) {
                ChatSettingsPage(
                    session = session.value,
                    lorebookState = ChatLorebookState(groups = emptyList()),
                    loadState = ChatLoadState.None,
                    showCharacterExamplesSwitch = true,
                    emit = {
                        if (this is ChatUiIntent.SaveSetting && field == ChatSettingField.Summary) {
                            savedEdits.add(edit.value)
                        }
                    },
                )
            }
        }
        // 保持空输入框聚焦并模拟摘要入库后的外部刷新。
        val field = hasSetTextAction() and hasText(summaryLabel)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(field)
        rule.onNode(field).performClick().assertIsFocused()
        rule.runOnIdle {
            session.value =
                session.value.copy(summarize = "Generated memory", canResummarize = true)
        }
        rule.mainClock.advanceTimeBy(600L)
        rule.onNode(field).assertIsFocused().assertTextContains("Generated memory")
        rule.onNode(hasText(context.getString(R.string.resummarize))).assertExists()
        rule.runOnIdle { assertTrue(savedEdits.isEmpty()) }

        // 显式清空仍是可持久化的用户操作。
        rule.onNode(field).performTextReplacement("")
        rule.mainClock.advanceTimeBy(600L)
        rule.runOnIdle { assertEquals(listOf(""), savedEdits) }
    }
}
