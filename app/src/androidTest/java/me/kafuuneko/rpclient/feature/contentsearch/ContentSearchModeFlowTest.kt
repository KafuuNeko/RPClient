package me.kafuuneko.rpclient.feature.contentsearch

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
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
    @get:Rule val rule = createAndroidComposeRule<ContentSearchActivity>()

    /** 开关均需确认；旋转保留弹窗或任务，取消关闭不清理索引。 */
    @Test
    fun confirmationRotationCancellationAndDisableFollowPersistentLifecycle() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val index = GlobalContext.get().get<ContentSearchRepository>()
        index.disable()
        val character = database.getCharacterDao().insertOrReplace(Character(name = "Mode fixture", avatar = "", characterTags = "[]",
            description = "", personality = "", scenario = "", firstMessages = "[]", examplesOfDialogue = "", postHistoryInstructions = ""))
        val chat = database.getChatSessionDao().insertOrReplace(ChatSession(characterId = character, createTime = 1, latestTime = 1,
            lorebookEntrySet = "[]", title = "Mode fixture", userNote = ""))
        val message = database.getChatMessageDao().insertOrReplace(ChatMessage(sessionId = chat, createTime = 1,
            source = ChatMessage.Source.User, content = "needle " + "雪".repeat(2_000_000)))
        try {
            rule.waitForIdle()
            rule.onNode(hasSetTextAction()).performTextInput("needle")
            click(R.string.content_search_submit)
            click(R.string.content_search_index_enable)
            rule.onNodeWithText(label(R.string.content_search_index_confirm_title)).assertIsDisplayed()
            assertEquals(SearchIndexState.Disabled, index.indexState.value)
            capture("search-mode-confirm.png")
            click(R.string.cancel)
            rule.onNodeWithText(label(R.string.stop)).assertDoesNotExist()
            click(R.string.content_search_index_enable)
            click(R.string.confirm)
            rule.waitUntil(10_000) { index.indexState.value is SearchIndexState.Building }
            // 重建页面不启动第二个构建；返回与取消必须进入真实清理流程。
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText(label(R.string.content_search_index_title)).assertIsDisplayed()
            capture("search-mode-building.png")
            pressBack()
            rule.waitForIdle()
            rule.waitUntil(30_000) { index.indexState.value == SearchIndexState.Disabled }
            assertEquals(0L, database.getContentSearchDao().indexedSourceCount())
            database.getChatMessageDao().updateMessageContent(message, "needle")
            click(R.string.content_search_index_enable)
            click(R.string.confirm)
            rule.waitUntil(15_000) { index.indexState.value == SearchIndexState.Ready }
            capture("search-mode-enabled.png")
            val indexedCount = database.getContentSearchDao().indexedSourceCount()
            assertTrue(indexedCount > 0L)
            // 关闭前显示确认，旋转与取消都不能启动持久清理。
            click(R.string.content_search_index_disable)
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
            click(R.string.content_search_index_disable)
            pressBack()
            rule.waitForIdle()
            rule.onNodeWithText(label(R.string.content_search_index_disable_confirm_title)).assertDoesNotExist()
            assertEquals(SearchIndexState.Ready, index.indexState.value)
            assertEquals(indexedCount, database.getContentSearchDao().indexedSourceCount())
            click(R.string.content_search_index_disable)
            click(R.string.confirm)
            rule.waitUntil(15_000) { index.indexState.value == SearchIndexState.Disabled }
            assertEquals(0L, database.getContentSearchDao().indexedSourceCount())
            capture("search-mode-disabled.png")
        } finally {
            index.disable()
            database.openHelper.writableDatabase.execSQL("DELETE FROM character WHERE id = ?", arrayOf(character))
        }
    }

    private fun label(id: Int): String = rule.activity.getString(id)
    private fun click(id: Int) { rule.onNodeWithText(label(id)).performClick(); rule.waitForIdle() }

    private fun capture(name: String) {
        rule.waitForIdle()
        SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        instrumentation.targetContext.cacheDir.resolve(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
