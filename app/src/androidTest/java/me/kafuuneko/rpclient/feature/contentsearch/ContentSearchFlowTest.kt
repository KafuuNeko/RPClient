package me.kafuuneko.rpclient.feature.contentsearch

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chat.ChatViewModel
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchResultState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.items
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.math.abs

/** 从首页入口到历史消息窗口的跨 Activity 集成回归，验证真实导航和列表定位。 */
class ContentSearchFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** 数据完整的搜索结果仍可分多页，真实按钮必须追加后续结果并在末页消失。 */
    @Test
    fun completeSearchResultsKeepPaginationUntilLastPage() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val character = createCharacter(database)
        val sessionIds = mutableListOf<Long>()
        try {
            // 标题检索始终返回完整数据，超过一页的命中不能因此丢失分页能力。
            repeat(times = 25) { index ->
                sessionIds += database.getChatSessionDao().insertOrReplace(
                    data = ChatSession(
                        characterId = character,
                        title = "rppaginationfixture $index",
                        createTime = index.toLong(),
                        latestTime = index.toLong(),
                        lorebookEntrySet = "[]",
                        userNote = "",
                    ),
                )
            }
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                ContentSearchActivity::class.java,
            ).putExtra(ContentSearchActivity.EXTRA_KIND, SearchKind.Chat.code)
            ActivityScenario.launch<ContentSearchActivity>(intent).use { scenario ->
                lateinit var model: ContentSearchViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[ContentSearchViewModel::class.java]
                }
                rule.waitUntil(timeoutMillis = 10_000) {
                    val state = model.uiStateFlow.value as? ContentSearchUiState.Normal
                    state != null && !state.indexState.busy
                }
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                // 从页面筛选标题并提交，覆盖仓库到 ViewModel 的完整结果转换。
                rule.onNodeWithText(context.getString(R.string.content_search_all))
                    .performClick()
                rule.onNodeWithText(context.getString(R.string.content_search_metadata))
                    .performClick()
                rule.onNode(hasSetTextAction()).performTextInput("rppaginationfixture")
                rule.onNodeWithText(context.getString(R.string.content_search_submit))
                    .performClick()
                val result = {
                    (model.uiStateFlow.value as? ContentSearchUiState.Normal)?.resultState
                }
                rule.waitUntil(timeoutMillis = 10_000) {
                    val current = result()
                    current is ContentSearchResultState.Complete && current.items.size == 20
                }
                assertEquals(sessionIds.reversed().take(20), result()?.items()?.map { it.parentId })
                // 必须点击真实的加载更多按钮，避免只验证领域层游标而漏掉页面回归。
                val more = context.getString(R.string.content_search_more)
                rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(more))
                rule.onNodeWithText(more).performClick()
                rule.waitUntil(timeoutMillis = 10_000) {
                    val current = result()
                    current is ContentSearchResultState.Complete && current.items.size == 25
                }
                assertEquals(sessionIds.reversed(), result()?.items()?.map { it.parentId })
                rule.onNodeWithText(more).assertDoesNotExist()
            }
        } finally {
            sessionIds.forEach { session ->
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM chat_sessions WHERE id = ?",
                    arrayOf(session),
                )
            }
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM character WHERE id = ?",
                arrayOf(character),
            )
        }
    }

    @Test
    fun homeSearchOpensOldMessageAndKeepsLatestOutsideWindow() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val character = database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Search fixture",
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
        val session = database.getChatSessionDao().insertOrReplace(
            data = ChatSession(
                characterId = character,
                title = "A rainy evening",
                createTime = 1,
                latestTime = 1,
                lorebookEntrySet = "[]",
                userNote = "",
            ),
        )
        try {
            database.getChatMessageDao().insertOrReplaceAll(
                data = (0..120).map { index ->
                    ChatMessage(
                        sessionId = session,
                        createTime = index.toLong(),
                        source = ChatMessage.Source.User,
                        content = if (index == 40) "The needle is in this old message." else "Ordinary message $index",
                    )
                },
            )
            withTimeout(timeMillis = 20_000) {
                while (database.getContentSearchDao().hasPending()) delay(timeMillis = 50)
            }
            // 首页仅有按钮，实际输入和长任务发生在独立页面。
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.content_search_title))
                .performScrollTo()
            capture("content-search-home.png")
            rule.onNodeWithContentDescription(rule.activity.getString(R.string.content_search_title))
                .performClick()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule
                    .onAllNodes(hasSetTextAction())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            rule.onNode(hasSetTextAction()).performTextInput("needle")
            rule.onNodeWithText(rule.activity.getString(R.string.content_search_submit))
                .performClick()
            rule.waitForIdle()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule
                    .onAllNodesWithText("A rainy evening")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            rule.onNodeWithText("A rainy evening").assertExists()
            capture("content-search-results.png")
            rule
                .onNodeWithText("the needle is in this old message.")
                .performScrollTo()
                .performClick()
            rule.waitForIdle()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule
                    .onAllNodesWithText(
                        text = "The needle is in this old message.",
                        substring = true
                    )
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            rule.onNodeWithText(text = "The needle is in this old message.", substring = true)
                .assertIsDisplayed()
            rule.onNodeWithText(text = "Ordinary message 120", substring = true)
                .assertDoesNotExist()
            capture("content-search-anchor.png")
        } finally {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM chat_sessions WHERE id = ?",
                arrayOf(session)
            )
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM character WHERE id = ?",
                arrayOf(character)
            )
        }
    }

    /** 真实单聊列表覆盖最新消息直接定位及历史窗口逐页回到末尾，避免只测按钮条件。 */
    @Test
    fun chatHistoryNavigationEndsAtLatestAndCentersActions() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val character = createCharacter(database)
        val session = database.getChatSessionDao().insertOrReplace(
            data = ChatSession(
                characterId = character,
                title = "Navigation fixture",
                createTime = 1,
                latestTime = 1,
                lorebookEntrySet = "[]",
                userNote = "",
            ),
        )
        try {
            val ids = database.getChatMessageDao().insertOrReplaceAll(
                data = (0..120).map { index ->
                    ChatMessage(
                        sessionId = session,
                        createTime = index.toLong(),
                        source = ChatMessage.Source.User,
                        content = navigationMessage(index),
                    )
                },
            )
            // 最新消息较长时仍从消息开头阅读，不能为了退出定位直接跳到正文末尾。
            for (anchor in listOf(ids.last(), ids[110], ids[40])) {
                val intent = Intent(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    ChatActivity::class.java,
                )
                    .putExtra(ChatActivity.EXTRA_SESSION_ID, session.toString())
                    .putExtra(SearchNavigation.EXTRA_SOURCE_ID, anchor)
                ActivityScenario.launch<ChatActivity>(intent).use { scenario ->
                    lateinit var model: ChatViewModel
                    scenario.onActivity { chatActivity ->
                        model = ViewModelProvider(chatActivity)[ChatViewModel::class.java]
                    }
                    rule.waitUntil(timeoutMillis = 10_000) { model.uiStateFlow.value is ChatUiState.Normal }
                    val read = { (model.uiStateFlow.value as ChatUiState.Normal).conversationState }
                    rule.waitForIdle()
                    assertEquals(anchor.toString(), read().searchAnchorMessageId)
                    if (anchor == ids.last()) {
                        rule.onNodeWithText(latestLabel()).assertDoesNotExist()
                        rule.onNodeWithText(text = "Latest message start", substring = true)
                            .assertIsDisplayed()
                    }
                    // 窗口底部仍有未加载消息时必须保留定位，加载末页也不能抢走阅读位置。
                    while (read().canLoadNewerMessages) {
                        scrollToEnd(read().messages.size + 1)
                        assertEquals(anchor.toString(), read().searchAnchorMessageId)
                        assertCenteredLatestAction()
                        capture("content-search-navigation.png")
                        val previousTail = read().messages.last().id
                        rule.onNodeWithText(newerLabel()).performClick()
                        rule.waitUntil(timeoutMillis = 10_000) { !read().historyLoadState.isLoadingNewer && read().messages.last().id != previousTail }
                    }
                    val count = read().messages.size
                    assertEquals(anchor.toString(), read().searchAnchorMessageId)
                    scrollToEnd(count + 1)
                    rule.waitUntil(timeoutMillis = 10_000) { read().searchAnchorMessageId == null }
                    assertEquals(count, read().messages.size)
                    rule.onNodeWithText(latestLabel()).assertDoesNotExist()
                    rule.onNodeWithText(newerLabel()).assertDoesNotExist()
                }
            }
        } finally {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM chat_sessions WHERE id = ?",
                arrayOf(session)
            )
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM character WHERE id = ?",
                arrayOf(character)
            )
        }
    }

    /** 群聊页头与单聊不同，单独验证实际索引偏移、分页和手动到达最新消息。 */
    @Test
    fun groupHistoryNavigationEndsAtLatestAndCentersActions() = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        val character = createCharacter(database)
        val session = database.getGroupChatSessionDao().insertOrReplace(
            data = GroupChatSession(
                title = "Navigation group fixture",
                createTime = 1,
                latestTime = 1,
                userName = "User",
                userDescription = "",
            ),
        )
        try {
            database.getGroupChatMemberDao().insertOrReplace(
                data = GroupChatMember(
                    sessionId = session,
                    characterId = character,
                    sortOrder = 0,
                ),
            )
            val ids = database.getGroupChatMessageDao().insertOrReplaceAll(
                data = (0..120).map { index ->
                    GroupChatMessage(
                        sessionId = session,
                        createTime = index.toLong(),
                        source = GroupChatMessage.Source.User,
                        content = navigationMessage(index),
                        speakerNameSnapshot = "User",
                    )
                },
            )
            // 从长的最新消息或旧消息进入，首次定位都应保持原文的阅读起点。
            for (anchor in listOf(ids.last(), ids[110], ids[40])) {
                val intent = Intent(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    GroupChatActivity::class.java,
                )
                    .putExtra(GroupChatActivity.EXTRA_SESSION_ID, session.toString())
                    .putExtra(SearchNavigation.EXTRA_SOURCE_ID, anchor)
                ActivityScenario.launch<GroupChatActivity>(intent).use { scenario ->
                    lateinit var model: GroupChatViewModel
                    scenario.onActivity { groupChatActivity ->
                        model = ViewModelProvider(groupChatActivity)[GroupChatViewModel::class.java]
                    }
                    rule.waitUntil(timeoutMillis = 10_000) { model.uiStateFlow.value is GroupChatUiState.Normal }
                    val read =
                        { (model.uiStateFlow.value as GroupChatUiState.Normal).conversationState }
                    val endIndex =
                        { read().messages.size + if (read().canLoadOlderMessages || read().historyLoadState.isLoadingOlder) 1 else 0 }
                    rule.waitForIdle()
                    assertEquals(anchor, read().searchAnchorMessageId)
                    if (anchor == ids.last()) {
                        rule.onNodeWithText(latestLabel()).assertDoesNotExist()
                        rule.onNodeWithText(text = "Latest message start", substring = true)
                            .assertIsDisplayed()
                    }
                    // 群聊也只能在最新窗口真正滚到底后恢复跟随，不能在末页加载完成时触发。
                    while (read().canLoadNewerMessages) {
                        scrollToEnd(endIndex())
                        assertEquals(anchor, read().searchAnchorMessageId)
                        assertCenteredLatestAction()
                        capture("content-search-navigation.png")
                        val previousTail = read().messages.last().id
                        rule.onNodeWithText(newerLabel()).performClick()
                        rule.waitUntil(timeoutMillis = 10_000) { !read().historyLoadState.isLoadingNewer && read().messages.last().id != previousTail }
                    }
                    val count = read().messages.size
                    assertEquals(anchor, read().searchAnchorMessageId)
                    scrollToEnd(endIndex())
                    rule.waitUntil(timeoutMillis = 10_000) { read().searchAnchorMessageId == null }
                    assertEquals(count, read().messages.size)
                    rule.onNodeWithText(latestLabel()).assertDoesNotExist()
                    rule.onNodeWithText(newerLabel()).assertDoesNotExist()
                }
            }
        } finally {
            database.getGroupChatSessionDao().deleteById(session)
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM character WHERE id = ?",
                arrayOf(character)
            )
        }
    }

    /** 合成角色仅供当前测试使用，并由调用方在 finally 中删除。 */
    private suspend fun createCharacter(database: AppDatabase): Long =
        database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Navigation fixture",
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

    /** 超过一屏的最新消息保护阅读起点，普通消息则用于跨页加载。 */
    private fun navigationMessage(index: Int): String = if (index == 120) {
        "Latest message start\n\n" + (1..80).joinToString("\n\n") { "Latest message line $it" }
    } else "Navigation message $index"

    /** 滚动真实消息列表，触发与用户手动滑动相同的视口观察。 */
    private fun scrollToEnd(index: Int) {
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        rule.waitForIdle()
    }

    /** 相对于消息列表验证按钮水平居中，避免只检查父容器声明。 */
    private fun assertCenteredLatestAction() {
        val button = rule
            .onNodeWithText(latestLabel())
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val list = rule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Latest action must be horizontally centered",
            abs(button.center.x - list.center.x) < 2f
        )
    }

    /** 读取目标应用资源，独立 ActivityScenario 可能已经销毁首页宿主。 */
    private fun latestLabel(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.content_search_latest)

    /** 分页按钮的本地化标签。 */
    private fun newerLabel(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.content_search_newer)

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        instrumentation.targetContext.cacheDir.resolve(
            relative = InstrumentationRegistry.getArguments()
                .getString("capturePrefix")
                .orEmpty() + name,
        ).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
