package me.kafuuneko.rpclient.feature.storyeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditedTextRange
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.feature.storyeditor.ui.LocalStoryMarkdownEditorController
import me.kafuuneko.rpclient.feature.storyeditor.ui.MarkdownModeToolbarAction
import me.kafuuneko.rpclient.feature.storyeditor.ui.StoryTextEditor
import me.kafuuneko.rpclient.feature.storyeditor.ui.rememberStoryMarkdownController
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 验证搜索原文定位、输入快照和 Markdown 预览之间的 Compose 集成。 */
class StoryMarkdownEditorTest {
    @get:Rule
    val compose = createComposeRule()

    /** 搜索命中编辑后清除定位范围，仍应留在原文；确认后展示刚输入的渲染内容。 */
    @Test
    fun searchEditKeepsSourceModeUntilConfirmation() {
        val initial = "Before **found** after"
        val document = mutableStateOf(
            value = StoryEditorDocument(
                storyId = 1,
                chapterId = 2,
                content = initial,
                syncVersion = 0,
                searchRange = StoryEditedTextRange(start = 9, end = 14),
            ),
        )
        val editor = TextFieldState(initial)
        var publishedText = initial
        compose.setContent {
            AppTheme(dynamicColor = false) {
                val controller =
                    rememberStoryMarkdownController(document = document.value, enabled = true)
                CompositionLocalProvider(LocalStoryMarkdownEditorController provides controller) {
                    Column(Modifier.fillMaxSize()) {
                        MarkdownModeToolbarAction()
                        StoryTextEditor(
                            modifier = Modifier.weight(1f),
                            document = document.value,
                            editorState = editor,
                            editable = true,
                            generationState = StoryGenerationState.Idle,
                            emit = {
                                if (this is StoryEditorUiIntent.EditorSnapshotChanged) {
                                    publishedText = snapshot.content
                                    if (snapshot.content != document.value.content) {
                                        document.value = document.value.copy(
                                            content = snapshot.content,
                                            searchRange = null,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        // 原文搜索范围必须仍对应 Markdown 源偏移，不能选中渲染后的错误位置。
        compose.runOnIdle {
            assertEquals(9, editor.selection.start)
            assertEquals(14, editor.selection.end)
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("**Revised** paragraph")
        compose.onNode(hasSetTextAction()).assertIsEnabled()
        val confirm =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.confirm)
        compose.onNodeWithText(confirm).performClick()
        compose.onNodeWithText("Revised paragraph").assertIsDisplayed()
        compose.runOnIdle { assertEquals("**Revised** paragraph", publishedText) }
    }
}
