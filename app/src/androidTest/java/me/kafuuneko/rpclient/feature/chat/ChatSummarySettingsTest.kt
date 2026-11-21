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
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.ui.ChatSettingsPage
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 确保外部摘要回填不会被聚焦输入框当作用户编辑重新提交。 */
class ChatSummarySettingsTest {
    @get:Rule
    val rule = createComposeRule()

    /** 摘要回填能更新聚焦的输入框，只有用户实际清空正文才发送草稿变更。 */
    @Test
    fun generatedSummaryReplacesFocusedDraftWithoutSubmittingAnEdit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val summaryLabel = context.getString(R.string.current_summary)
        val settings = mutableStateOf(ChatSettingsState(titleDraft = "Chat"))
        val summaryChanges = mutableListOf<String>()
        rule.setContent {
            AppTheme(dynamicColor = false) {
                ChatSettingsPage(
                    state = settings.value,
                    lorebookState = ChatLorebookState(groups = emptyList()),
                    loadState = ChatLoadState.None,
                    showCharacterExamplesSwitch = true,
                    emit = {
                        if (this is ChatUiIntent.ChangeSummary) {
                            summaryChanges.add(value)
                            settings.value = settings.value.copy(summaryDraft = value)
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
            settings.value = settings.value.copy(
                summaryDraft = "Generated memory",
                savedSummary = "Generated memory",
                canResummarize = true,
            )
        }
        rule.onNode(field).assertIsFocused().assertTextContains("Generated memory")
        rule.onNode(hasText(context.getString(R.string.resummarize))).assertExists()
        rule.runOnIdle { assertTrue(summaryChanges.isEmpty()) }

        // 清空立即更新受控草稿，等待显式保存。
        rule.onNode(field).performTextReplacement("")
        rule.runOnIdle { assertEquals(listOf(""), summaryChanges) }
    }
}
