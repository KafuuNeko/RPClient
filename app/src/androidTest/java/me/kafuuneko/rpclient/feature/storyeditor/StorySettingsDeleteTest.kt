package me.kafuuneko.rpclient.feature.storyeditor

import android.content.Intent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

/** 从真实编辑器验证整篇删除的确认边界和退出时机。 */
class StorySettingsDeleteTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    /** 取消时保留故事和设置页，确认后删除并结束 Activity。 */
    @Test
    fun cancelKeepsStoryAndConfirmClosesEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stories = GlobalContext.get().get<StoryRepository>()
        val title = "Delete fixture"
        val id = runBlocking { stories.createStory(title) }
        val intent = Intent(context, StoryEditorActivity::class.java)
            .putExtra(StoryEditorActivity.EXTRA_STORY_ID, id)
        try {
            ActivityScenario.launch<StoryEditorActivity>(intent).use { scenario ->
                // 从编辑器进入设置，确认框必须标明整个故事的名称。
                val settings = hasContentDescription(context.getString(R.string.story_settings))
                rule.waitUntil(10_000) {
                    rule.onAllNodes(settings).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onAllNodes(settings)[0].performClick()
                val deleteStory = context.getString(R.string.story_delete_story)
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText(deleteStory).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText(deleteStory).performClick()
                rule.onNodeWithText(context.getString(R.string.story_delete_story_message, title))
                    .assertExists()
                rule.onNodeWithText(context.getString(R.string.cancel)).performClick()
                assertNotNull(runBlocking { stories.getStory(id) })

                // 再次发起操作并确认，编辑器结束后检查持久化结果。
                rule.onNodeWithText(deleteStory).performClick()
                rule.onNodeWithText(context.getString(R.string.delete)).performClick()
                rule.waitUntil(10_000) { scenario.state == Lifecycle.State.DESTROYED }
                assertNull(runBlocking { stories.getStory(id) })
            }
        } finally {
            runBlocking { stories.deleteStory(id) }
        }
    }
}
