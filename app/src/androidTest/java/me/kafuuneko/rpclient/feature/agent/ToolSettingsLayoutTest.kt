package me.kafuuneko.rpclient.feature.agent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsContentState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsEditorMode
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState
import me.kafuuneko.rpclient.feature.toolsettings.ui.ToolSettingsLayout
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 小屏大字号验收工具列表与编辑弹窗，测试派发必须经过显式确认按钮且可取消。 */
class ToolSettingsLayoutTest {
    @get:Rule
    val rule = createComposeRule()
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun narrowLargeFontLayoutsKeepActionsReachableInBothThemes() {
        val dark = mutableStateOf(false)
        val state = mutableStateOf(
            ToolSettingsUiState.Normal(
                content = ToolSettingsContentState(
                    tools = listOf(
                        HttpToolListItem(
                            id = "fixture",
                            name = "custom_search_with_a_long_model_function_name",
                            description = "Search remote documents",
                            kind = "Http",
                            enabled = true,
                            needsConfiguration = true,
                        )
                    ),
                    connections = listOf(
                        ToolConnectionListItem(
                            id = "connection",
                            name = "Fixture connection",
                            url = "https://tool.fixture/search",
                            method = "POST",
                            authMode = "Bearer",
                            hasCredential = true,
                            allowPrivateAddresses = false,
                            boundToolCount = 1,
                        )
                    ),
                )
            )
        )
        val actions = mutableListOf<ToolSettingsUiIntent>()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                AppTheme(darkTheme = dark.value, dynamicColor = false) {
                    Box(modifier = Modifier.width(320.dp).fillMaxSize()) {
                        ToolSettingsLayout(uiState = state.value, emit = { actions.add(this) })
                    }
                }
            }
        }
        // 列表在窄屏和大字号下仍保留多行操作，不依赖横向滚动触达编辑入口。
        rule.onNodeWithText(mContext.getString(R.string.edit)).assertIsDisplayed().performClick()
        assertEquals(ToolSettingsUiIntent.EditTool("fixture"), actions.single())
        screenshot("tool-settings-light.png")
        rule.runOnIdle { dark.value = true }
        rule.waitForIdle()
        screenshot("tool-settings-dark.png")
        rule.runOnIdle {
            state.value = state.value.copy(
                dialog = ToolSettingsDialogState.ToolEditor(
                    id = "fixture",
                    mode = ToolSettingsEditorMode.Edit,
                    name = "web_search",
                    description = "Search public web",
                )
            )
        }
        rule.onNodeWithText(mContext.getString(R.string.save)).assertIsDisplayed()
        screenshot("tool-editor-dark.png", dialog = true)
    }

    @Test
    fun testDialogShowsDestinationRequiresExplicitSendAndSupportsCancel() {
        val actions = mutableListOf<ToolSettingsUiIntent>()
        val state = mutableStateOf(
            ToolSettingsUiState.Normal(
                dialog = ToolSettingsDialogState.Test(
                    toolId = "fixture",
                    toolName = "web_search",
                    destination = "POST tool.fixture",
                    arguments = "{\"query\":\"public reference\"}",
                )
            )
        )
        rule.setContent {
            AppTheme(dynamicColor = false) {
                ToolSettingsLayout(
                    uiState = state.value,
                    emit = { actions.add(this) },
                )
            }
        }
        // 展示目标与参数后仍不派发，只有用户点击测试按钮才产生运行意图。
        rule.onNodeWithText("POST tool.fixture").assertIsDisplayed()
        assertTrue(actions.isEmpty())
        rule.onNodeWithText(mContext.getString(R.string.tool_settings_run_test)).performClick()
        assertEquals(ToolSettingsUiIntent.RunTest, actions.single())

        // 请求进行中时允许用户显式取消，避免长超时锁死弹窗。
        rule.runOnIdle {
            val dialog = state.value.dialog as ToolSettingsDialogState.Test
            state.value = state.value.copy(dialog = dialog.copy(running = true))
        }
        rule.onNodeWithText(mContext.getString(R.string.tool_settings_test_cancel))
            .assertIsDisplayed()
            .performClick()
        assertEquals(ToolSettingsUiIntent.CancelTest, actions.last())
    }

    private fun screenshot(name: String, dialog: Boolean = false) {
        val target = File(mContext.cacheDir, name)
        val node = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
