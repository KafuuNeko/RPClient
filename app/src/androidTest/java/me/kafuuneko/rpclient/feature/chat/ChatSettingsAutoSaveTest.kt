package me.kafuuneko.rpclient.feature.chat

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.ChatSettingField
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.ui.ChatSettingsPage
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 在真实输入框中覆盖自动保存回显、规范化和外部摘要更新之间的竞争。 */
class ChatSettingsAutoSaveTest {
    @get:Rule
    val rule = createComposeRule()

    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mSession = mutableStateOf(
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
    private val mSettings = mutableStateOf(ChatSettingsState())
    private val mSavedEdits = mutableListOf<ChatUiIntent.SaveSetting>()

    /** 标题去除首尾空白后的旧回显不能覆盖新输入，最新回执仍能展示规范化结果。 */
    @Test
    fun normalizedTitleAcknowledgementKeepsNewerDraft() {
        showSettings()
        val field = field(R.string.title)
        field.performTextReplacement("  draft")
        rule.mainClock.advanceTimeBy(600L)
        rule.runOnIdle { assertEquals("  draft", mSavedEdits.single().edit.value) }

        // 旧保存返回前继续输入，新的防抖保存尚未提交。
        field.performTextReplacement("  draft B")
        acknowledgeTitle(index = 0, storedValue = "draft")
        field.assertInput("  draft B")
        rule.mainClock.advanceTimeBy(600L)
        rule.runOnIdle {
            assertEquals(listOf("  draft", "  draft B"), mSavedEdits.map { it.edit.value })
        }

        // 最新保存确认后可以同步规范化文本，不触发额外保存。
        acknowledgeTitle(index = 1, storedValue = "draft B")
        field.assertInput("draft B")
        rule.mainClock.advanceTimeBy(600L)
        rule.runOnIdle { assertEquals(2, mSavedEdits.size) }
    }

    /** 连续保存合并回显且入库值未变时，也必须确认正确的提交并保留更新的输入。 */
    @Test
    fun unchangedTitleAcknowledgementCanSkipOlderSubmissions() {
        showSettings()
        val field = field(R.string.title)
        field.performTextReplacement(" Chat ")
        rule.mainClock.advanceTimeBy(600L)
        field.performTextReplacement("  Chat ")
        rule.mainClock.advanceTimeBy(600L)

        // 只观察到较后一次回执，标题入库前后的展示值都为 Chat。
        field.performTextReplacement("Newest")
        acknowledgeTitle(index = 1, storedValue = "Chat")
        field.assertInput("Newest")
        rule.mainClock.advanceTimeBy(600L)
        rule.runOnIdle {
            assertEquals(listOf(" Chat ", "  Chat ", "Newest"), mSavedEdits.map { it.edit.value })
            assertEquals(3, mSavedEdits.map { it.edit.id }.toSet().size)
        }
    }

    /** 回执和生成结果合并到同次刷新时，生成内容仍会替换并取消未提交的摘要草稿。 */
    @Test
    fun generatedSummaryIsNotMistakenForAnEarlierSave() {
        showSettings()
        val field = field(R.string.current_summary)
        field.performTextReplacement("Manual memory")
        rule.mainClock.advanceTimeBy(600L)
        field.performTextReplacement("Pending draft")

        // 保存回执对应 Manual memory，当前正文已由之后的生成更新。
        rule.runOnIdle {
            val submitted = mSavedEdits.single()
            mSettings.value =
                ChatSettingsState(savedEdits = mapOf(submitted.field to submitted.edit))
            mSession.value = mSession.value.copy(summarize = "Generated memory")
        }
        field.assertInput("Generated memory")
        rule.mainClock.advanceTimeBy(600L)
        rule.runOnIdle { assertEquals(listOf("Manual memory"), mSavedEdits.map { it.edit.value }) }
    }

    /** 用真实设置页面接收回执，保存请求留在测试中控制完成顺序。 */
    private fun showSettings() {
        // 仅替换保存完成时序，输入与外部回显仍使用生产页面的同一套状态同步逻辑。
        rule.setContent {
            AppTheme(dynamicColor = false) {
                ChatSettingsPage(
                    session = mSession.value,
                    settingsState = mSettings.value,
                    lorebookState = ChatLorebookState(groups = emptyList()),
                    loadState = ChatLoadState.None,
                    showCharacterExamplesSwitch = true,
                    emit = { if (this is ChatUiIntent.SaveSetting) mSavedEdits.add(this) },
                )
            }
        }
    }

    /** 滚动至真实表单中的目标字段。 */
    private fun field(@StringRes labelResId: Int): SemanticsNodeInteraction {
        val matcher = hasSetTextAction() and hasText(mContext.getString(labelResId))
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return rule.onNode(matcher)
    }

    /** 同时发布标题的规范化结果与对应编辑标识，模拟业务层的原子回显。 */
    private fun acknowledgeTitle(index: Int, storedValue: String) {
        rule.runOnIdle {
            val submitted = mSavedEdits[index]
            assertEquals(ChatSettingField.Title, submitted.field)
            mSession.value = mSession.value.copy(title = storedValue)
            mSettings.value = mSettings.value.copy(
                savedEdits = mSettings.value.savedEdits +
                    (submitted.field to submitted.edit.copy(value = storedValue)),
            )
        }
    }

    /** 精确比较可编辑正文，避免子串断言漏掉首尾空白被覆盖的问题。 */
    private fun SemanticsNodeInteraction.assertInput(expected: String): SemanticsNodeInteraction =
        assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString(expected)
            )
        )
}
