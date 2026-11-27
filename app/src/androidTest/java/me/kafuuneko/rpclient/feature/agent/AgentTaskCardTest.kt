package me.kafuuneko.rpclient.feature.agent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentQuestionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentQuestionKind
import me.kafuuneko.rpclient.feature.agent.model.AgentTaskItem
import me.kafuuneko.rpclient.feature.agent.model.AgentUiAction
import me.kafuuneko.rpclient.feature.agent.model.AgentUiState
import me.kafuuneko.rpclient.feature.agent.ui.AgentSettingsPanel
import me.kafuuneko.rpclient.feature.agent.ui.AgentTaskCard
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验收默认折叠、等待快捷交互及流式刷新期间的展开稳定性，并导出主题截图。 */
class AgentTaskCardTest {
    @get:Rule
    val rule = createComposeRule()
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun questionShortcutDoesNotGrantWriteConfirmationAndExpandedStateSurvivesProgress() {
        val state = mutableStateOf(
            AgentUiState(
                enabled = true,
                tasks = listOf(task()),
                question = AgentQuestionItem("run", "Which road?", emptyList(), false, "")
            )
        )
        val actions = mutableListOf<AgentUiAction>()
        rule.setContent {
            AppTheme {
                AgentTaskCard(state.value.tasks.single(), state.value) { action ->
                    actions.add(action)
                    when (action) {
                        is AgentUiAction.ChangeAnswer -> state.value =
                            state.value.copy(answerDraft = action.value)

                        is AgentUiAction.ToggleTask -> state.value =
                            state.value.copy(expandedRunIds = setOf(action.runId))

                        else -> Unit
                    }
                }
            }
        }
        // 默认折叠仍可回答，不需要先展开步骤，也不会把普通答案变成写入授权。
        rule.onNodeWithText("Which road?").assertIsDisplayed()
        rule.onNodeWithText(mContext.getString(R.string.agent_answer)).performTextInput("North")
        rule.onNodeWithText(mContext.getString(R.string.agent_resume)).performClick()
        assertEquals(
            false,
            actions.filterIsInstance<AgentUiAction.SubmitAnswer>().single().confirmed
        )
        rule.onNodeWithContentDescription(mContext.getString(R.string.agent_expand)).performClick()
        rule.runOnIdle {
            state.value = state.value.copy(tasks = listOf(task().copy(tokens = 2048)))
        }
        rule.onNodeWithText(mContext.getString(R.string.agent_usage, 2, 1, 2048L))
            .assertIsDisplayed()
    }

    @Test
    fun narrowLargeFontLayoutsRemainReadableInBothThemes() {
        val dark = mutableStateOf(false)
        val state = AgentUiState(
            enabled = true,
            settingsLoaded = true,
            tasks = listOf(task()),
            expandedRunIds = setOf("run"),
            question = AgentQuestionItem(
                "run",
                "",
                emptyList(),
                true,
                "Promote a verified preference to character memory."
            )
        )
        var confirmed = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                AppTheme(darkTheme = dark.value) {
                    Column(
                        Modifier.width(320.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        AgentTaskCard(
                            state.tasks.single(),
                            state
                        ) { if (it is AgentUiAction.SubmitAnswer) confirmed = it.confirmed }
                        AgentSettingsPanel(state) { }
                    }
                }
            }
        }
        // 大字号窄屏下，确认按钮和正文必须仍然可见且可点。
        rule.onNodeWithText(mContext.getString(R.string.confirm)).assertIsDisplayed().performClick()
        assertTrue(confirmed)
        screenshot("agent-light-large-font.png")
        rule.runOnIdle { dark.value = true }
        rule.onNodeWithText(mContext.getString(R.string.confirm)).assertIsDisplayed()
        screenshot("agent-dark-large-font.png")
    }

    @Test
    fun externalWriteConfirmationShowsExternalCopyHostAndParameters() {
        val state = AgentUiState(
            enabled = true,
            tasks = listOf(task()),
            question = AgentQuestionItem(
                runId = "run",
                question = "",
                options = emptyList(),
                confirmation = true,
                details = "issue_tracker -> api.fixture.example",
                kind = AgentQuestionKind.ExternalWriteConfirmation,
                toolName = "issue_tracker",
                destinationHost = "api.fixture.example",
                parameterLines = listOf("title" to "Update notes"),
            )
        )
        var confirmed = false
        rule.setContent {
            AppTheme {
                AgentTaskCard(state.tasks.single(), state) { action ->
                    if (action is AgentUiAction.SubmitAnswer) confirmed = action.confirmed
                }
            }
        }
        // 外发请求确认必须展示外发标题、目标主机与明文参数，不能误报为本地记忆变更。
        rule.onNodeWithText(mContext.getString(R.string.agent_confirm_external_write))
            .assertIsDisplayed()
        rule.onNodeWithText(
            mContext.getString(R.string.agent_confirm_host_label, "api.fixture.example")
        ).assertIsDisplayed()
        rule.onNodeWithText("title: Update notes").assertIsDisplayed()
        rule.onNodeWithText(mContext.getString(R.string.confirm)).performClick()
        assertTrue(confirmed)
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val root =
            File(mContext.getExternalFilesDir(null), "agent-ui-acceptance").apply { mkdirs() }
        File(root, name).outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun task() =
        AgentTaskItem("run", null, R.string.agent_state_waiting, true, true, 2, 1, 100, emptyList())
}
