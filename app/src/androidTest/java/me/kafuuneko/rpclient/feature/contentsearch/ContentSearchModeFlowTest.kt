package me.kafuuneko.rpclient.feature.contentsearch

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

/** 真实 Compose 弹窗与 Activity 重建验证，不以 ViewModel 属性赋值替代用户流程。 */
class ContentSearchModeFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** 开关均需确认；旋转保留弹窗或任务，取消关闭不清理索引。 */
    @Test
    fun confirmationRotationCancellationAndDisableFollowPersistentLifecycle() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val index = GlobalContext.get().get<ContentSearchRepository>()
        index.disable()
        val character = database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Mode fixture",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "[]",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
            ),
        )
        val chat = database.getChatSessionDao().insertOrReplace(
            data = ChatSession(
                characterId = character,
                createTime = 1,
                latestTime = 1,
                lorebookEntrySet = "[]",
                title = "Mode fixture",
                userNote = "",
            ),
        )
        // 拆分大样本，避免主页恢复时读取单条预览超过 CursorWindow 容量。
        val content = "needle " + "雪".repeat(100_000)
        database.getChatMessageDao().insertOrReplaceAll(
            data = (0 until 80).map { position ->
            ChatMessage(
                sessionId = chat,
                createTime = position.toLong(),
                source = ChatMessage.Source.User,
                content = content,
            )
        },
        )
        try {
            rule.waitForIdle()
            rule.onNodeWithContentDescription(label(R.string.settings)).performClick()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.content_search_index_title)))
            click(R.string.content_search_index_title)
            rule.onNodeWithText(label(R.string.content_search_index_confirm_title)).assertIsDisplayed()
            assertEquals(SearchIndexState.Disabled, index.indexState.value)
            capture("search-mode-confirm.png")
            click(R.string.cancel)
            rule.onNodeWithText(label(R.string.content_search_index_confirm_title)).assertDoesNotExist()
            click(R.string.content_search_index_title)
            click(R.string.confirm)
            rule.waitUntil(timeoutMillis = 10_000) { index.indexState.value is SearchIndexState.Building }
            // 重建页面不启动第二个构建；返回与取消必须进入真实清理流程。
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText(label(R.string.cancel)).assertIsDisplayed()
            capture("search-mode-building.png")
            pressBack()
            rule.waitForIdle()
            rule.waitUntil(timeoutMillis = 30_000) { index.indexState.value == SearchIndexState.Disabled }
            assertEquals(0L, database.getContentSearchDao().indexedSourceCount())
            database.getChatMessageDao().deleteMessagesBySessionId(chat)
            database.getChatMessageDao().insertOrReplace(
                data = ChatMessage(
                    sessionId = chat,
                    createTime = 1,
                    source = ChatMessage.Source.User,
                    content = "needle",
                ),
            )
            click(R.string.content_search_index_title)
            click(R.string.confirm)
            rule.waitUntil(timeoutMillis = 15_000) { index.indexState.value == SearchIndexState.Ready }
            capture("search-mode-enabled.png")
            val indexedCount = database.getContentSearchDao().indexedSourceCount()
            assertTrue(indexedCount > 0L)
            // 关闭前显示确认，旋转与取消都不能启动持久清理。
            click(R.string.content_search_index_title)
            rule.onNodeWithText(label(R.string.content_search_index_disable_confirm_title)).assertIsDisplayed()
            assertEquals(SearchIndexState.Ready, index.indexState.value)
            assertEquals(indexedCount, database.getContentSearchDao().indexedSourceCount())
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText(label(R.string.content_search_index_disable_confirm_title)).assertIsDisplayed()
            capture("search-mode-disable-confirm.png")
            click(R.string.cancel)
            rule.onNodeWithText(label(R.string.content_search_index_disable_confirm_title)).assertDoesNotExist()
            assertEquals(SearchIndexState.Ready, index.indexState.value)
            assertEquals(indexedCount, database.getContentSearchDao().indexedSourceCount())
            // 返回与取消具有相同效果；只有确认按钮才能进入清理。
            click(R.string.content_search_index_title)
            pressBack()
            rule.waitForIdle()
            rule.onNodeWithText(label(R.string.content_search_index_disable_confirm_title)).assertDoesNotExist()
            assertEquals(SearchIndexState.Ready, index.indexState.value)
            assertEquals(indexedCount, database.getContentSearchDao().indexedSourceCount())
            click(R.string.content_search_index_title)
            click(R.string.confirm)
            rule.waitUntil(timeoutMillis = 15_000) { index.indexState.value == SearchIndexState.Disabled }
            assertEquals(0L, database.getContentSearchDao().indexedSourceCount())
            capture("search-mode-disabled.png")
            // 设置完成后从首页进入搜索，页内不再出现加速开关或更多菜单。
            pressBack()
            rule
                .onNodeWithContentDescription(label(R.string.content_search_title))
                .performScrollTo()
                .performClick()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule
                    .onAllNodes(hasSetTextAction())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            rule.onNodeWithText(label(R.string.content_search_index_title)).assertDoesNotExist()
            rule.onNodeWithContentDescription(label(R.string.content_search_index_title)).assertDoesNotExist()
            capture("search-without-more-menu.png")
        } finally {
            index.disable()
            database.openHelper.writableDatabase.execSQL("DELETE FROM character WHERE id = ?", arrayOf(character))
        }
    }

    private fun label(id: Int): String = rule.activity.getString(id)
    private fun click(id: Int) {
        rule.onNodeWithText(label(id)).performClick()
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        instrumentation.targetContext.cacheDir.resolve(name).outputStream().use { output -> bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            output,
        ) }
        bitmap.recycle()
    }
}
