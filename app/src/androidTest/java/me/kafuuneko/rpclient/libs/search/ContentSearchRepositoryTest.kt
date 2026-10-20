package me.kafuuneko.rpclient.libs.search

import android.view.WindowManager
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchQueryRepository
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真正执行 Android SQLite、Room 触发器和并发补建，不用 Mock 替代存储行为。 */
@RunWith(AndroidJUnit4::class)
class ContentSearchRepositoryTest {
    private lateinit var mScenario: ActivityScenario<MainActivity>
    private lateinit var mDatabase: AppDatabase
    private lateinit var mIndex: ContentSearchRepository
    private lateinit var mQueries: ContentSearchQueryRepository
    private var mCharacterId = 0L

    /** 每项测试独立创建内存库，并保持前台以排除设备后台冻结。 */
    @Before
    fun setUp() = runBlocking {
        // 部分实机会冻结无前台界面的测试进程，避免把后台调度误判为索引超时。
        mScenario = ActivityScenario.launch(MainActivity::class.java)
        mScenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SearchDatabaseCallback).build()
        mQueries = ContentSearchQueryRepository(mDatabase)
        mIndex = ContentSearchRepository(mDatabase, mQueries)
        mCharacterId = mDatabase.getCharacterDao().insertOrReplace(Character(
            name = "雪乃", avatar = "", characterTags = "[]", description = "", personality = "",
            scenario = "", firstMessages = "[]", examplesOfDialogue = "", postHistoryInstructions = ""
        ))
        mIndex.enable()
    }

    /** 等待工作器退出后关闭数据库和前台夹具，避免异步写入越过用例边界。 */
    @After
    fun tearDown() = runBlocking {
        mIndex.stop()
        mDatabase.close()
        mScenario.close()
    }

    @Test
    fun findsOldMessagesChineseSymbolsMarkdownAndEveryStoryChapter() = runBlocking {
        val chat = chat("Chat", 1)
        val old = message(chat, "x".repeat(SearchTextCodec.BLOCK_POINTS - 1) + "雪乃 100%_ hello **world** 👩🏽‍💻")
        message(chat, "newest unrelated")
        message(chat, "hidden-summary", ChatMessage.Source.Summary)
        val group = mDatabase.getGroupChatSessionDao().insertOrReplace(GroupChatSession(
            title = "Group", createTime = 1, latestTime = 2, userName = "User", userDescription = ""
        ))
        mDatabase.getGroupChatMessageDao().insertOrReplace(GroupChatMessage(sessionId = group, createTime = 1,
            source = GroupChatMessage.Source.Character, content = "雪乃", speakerNameSnapshot = "Historical name"))
        val story = mDatabase.getStoryDao().insertOrReplace(Story(title = "Story", createTime = 1, latestTime = 3))
        val chapter = mDatabase.getStoryChapterDao().insertOrReplace(StoryChapter(storyId = story,
            title = "Old chapter", content = "雪乃", sortOrder = 0, createTime = 1, latestTime = 1))
        mDatabase.getStoryChapterDao().insertOrReplace(StoryChapter(storyId = story,
            title = "Newest", content = "unrelated", sortOrder = 1, createTime = 2, latestTime = 2))
        ready()
        val found = mQueries.search(SearchRequest("雪乃", scope = SearchScope.Body))
        assertTrue(found.complete)
        assertEquals(setOf(1, 2, 3), found.hits.map { it.kind }.toSet())
        assertEquals(old, found.hits.single { it.kind == 1 }.sourceId)
        assertEquals(chapter, found.hits.single { it.kind == 3 }.sourceId)
        assertEquals("Historical name", found.hits.single { it.kind == 2 }.sourceLabel)
        for (query in listOf("雪", "100%_", "hello world", "👩🏽‍💻")) {
            assertEquals(chat, mQueries.search(SearchRequest(query, SearchKind.Chat, SearchScope.Body)).hits.single().parentId)
        }
        assertTrue(mQueries.search(SearchRequest("hidden-summary", scope = SearchScope.Body)).hits.isEmpty())
    }

    @Test
    fun dirtyEditsAndCascadeDeletesNeverReturnOldBody() = runBlocking {
        val chat = chat("Edit", 1)
        val id = message(chat, "oldneedle")
        ready()
        val previous = mQueries.search(SearchRequest("oldneedle", scope = SearchScope.Body)).hits.single()
        mIndex.stop()
        mDatabase.getChatMessageDao().updateMessageContent(id, "newneedle")
        assertFalse(mQueries.exists(previous, "oldneedle"))
        val old = mQueries.search(SearchRequest("oldneedle", scope = SearchScope.Body))
        assertFalse(old.complete)
        assertTrue(old.hits.isEmpty())
        assertEquals(id, mQueries.search(SearchRequest("newneedle", scope = SearchScope.Body, directScan = true)).hits.single().sourceId)
        ready()
        assertEquals(id, mQueries.search(SearchRequest("newneedle", scope = SearchScope.Body)).hits.single().sourceId)
        mDatabase.openHelper.writableDatabase.execSQL("DELETE FROM chat_sessions WHERE id = ?", arrayOf(chat))
        assertTrue(mQueries.search(SearchRequest("newneedle", scope = SearchScope.Body)).hits.isEmpty())
        ready()
        assertTrue(mQueries.search(SearchRequest("newneedle", scope = SearchScope.Body)).hits.isEmpty())
    }

    @Test
    fun objectPaginationAndSourcePaginationDoNotLoseDuplicateMatches() = runBlocking {
        val ids = (1..25).map { index -> chat("Object $index", index.toLong()).also { id ->
            repeat(if (index == 25) 30 else 1) { message(id, "needle") }
        } }
        ready()
        val request = SearchRequest("needle", scope = SearchScope.Body)
        val first = mQueries.search(request)
        val second = mQueries.search(request, first.next)
        assertEquals(20, first.hits.size)
        assertEquals(5, second.hits.size)
        assertEquals(ids.toSet(), (first.hits + second.hits).map { it.parentId }.toSet())
        val scoped = request.copy(kind = SearchKind.Chat, parentId = ids.last())
        val inner = mQueries.search(scoped)
        assertEquals(20, inner.hits.size)
        assertEquals(10, mQueries.search(scoped, inner.next).hits.size)
        // 数据变化后不能继续旧游标，必须显式刷新。
        message(ids.first(), "another")
        try { mQueries.search(request, first.next); throw AssertionError("Stale cursor was accepted") }
        catch (_: SearchChangedException) { }
    }

    @Test
    fun metadataPagesTransitionToBodyWithoutSkippingNewerObjects() = runBlocking {
        repeat(20) { chat("needle title $it", it.toLong()) }
        val bodyOnly = chat("body only", 1000)
        message(bodyOnly, "needle")
        ready()
        val request = SearchRequest("needle")
        val first = mQueries.search(request)
        assertEquals(20, first.hits.size)
        assertNotNull(first.next)
        assertEquals(bodyOnly, mQueries.search(request, first.next).hits.single().parentId)
    }

    @Test
    fun directScanCancelsAfterPartialResultsAndRemainsUsable() = runBlocking {
        mIndex.disable()
        val chat = chat("Cancel", 1)
        repeat(150) { message(chat, "x".repeat(20_000) + "needle") }
        val partial = CompletableDeferred<Unit>()
        var finished = false
        val job = launch {
            mQueries.search(SearchRequest("needle", scope = SearchScope.Body, directScan = true)) {
                partial.complete(Unit)
            }
            finished = true
        }
        // 已进入实际扫描并产出首批结果后取消，后续数据库查询必须仍能正常工作。
        withTimeout(10_000) { partial.await() }
        withTimeout(1_000) { job.cancelAndJoin() }
        assertFalse(finished)
        assertEquals(chat, mQueries.search(SearchRequest("Cancel", scope = SearchScope.Metadata)).hits.single().parentId)
    }

    @Test
    fun longSourceResumesAfterConcurrentEditWithoutPublishingOldRevision() = runBlocking {
        val chat = chat("Long", 1)
        val id = message(chat, "x".repeat(1_100_000) + "oldneedle")
        mIndex.restore()
        delay(650)
        mDatabase.getChatMessageDao().updateMessageContent(id, "x".repeat(1_100_000) + "newneedle")
        // 中途停止和重新启动使用持久待办恢复，不能把旧代次当作已经完成。
        mIndex.stop()
        ready()
        assertTrue(mQueries.search(SearchRequest("oldneedle", scope = SearchScope.Body)).hits.isEmpty())
        assertEquals(id, mQueries.search(SearchRequest("newneedle", scope = SearchScope.Body)).hits.single().sourceId)
    }

    /** 回收前后的同 ID 替换都不能与旧投影相混淆，墓碑和块最终都应清空。 */
    @Test
    fun deletionDuringIndexingAndReusedIdNeverResurrectOldChunks() = runBlocking {
        val chat = chat("Reuse", 1)
        val id = message(chat, "oldneedle " + "雪".repeat(300_000))
        mIndex.restore()
        withTimeout(30_000) {
            while (scalar("SELECT count(*) FROM search_chunks") == 0L) delay(10)
        }
        val db = mDatabase.openHelper.writableDatabase
        db.execSQL("DELETE FROM chat_messages WHERE id = ?", arrayOf(id))
        mDatabase.getChatMessageDao().insertOrReplace(ChatMessage(id, chat, 1, ChatMessage.Source.User, "replacement"))
        ready()
        assertTrue(mQueries.search(SearchRequest("oldneedle", scope = SearchScope.Body)).hits.isEmpty())
        assertEquals(id, mQueries.search(SearchRequest("replacement", scope = SearchScope.Body)).hits.single().sourceId)
        // 清理完成后旧代次已不可能提交，允许同 ID 从头登记。
        db.execSQL("DELETE FROM chat_messages WHERE id = ?", arrayOf(id))
        withTimeout(30_000) { while (mDatabase.getContentSearchDao().source(1, id) != null) delay(25) }
        assertEquals(0L, scalar("SELECT count(*) FROM search_chunks"))
        mDatabase.getChatMessageDao().insertOrReplace(ChatMessage(id, chat, 2, ChatMessage.Source.User, "afterprune"))
        ready()
        assertTrue(mQueries.search(SearchRequest("replacement", scope = SearchScope.Body)).hits.isEmpty())
        assertEquals(id, mQueries.search(SearchRequest("afterprune", scope = SearchScope.Body)).hits.single().sourceId)
    }

    /** 恢复必须从业务原文重新投影，而非保留已经损坏的外部内容 token。 */
    @Test
    fun retryRepairsCorruptTokensAndFormatChangeInvalidatesOldResults() = runBlocking {
        val chat = chat("Repair", 1)
        val id = message(chat, "needle 100%_ 👩🏽‍💻")
        ready()
        mIndex.stop()
        val db = mDatabase.openHelper.writableDatabase
        db.execSQL("UPDATE search_chunks SET tokens = 'n e e d l e xgg'")
        try {
            mQueries.search(SearchRequest("needle", scope = SearchScope.Body))
            throw AssertionError("Corrupt token was silently accepted")
        } catch (_: IllegalArgumentException) { }
        mIndex.retry()
        ready()
        val hit = mQueries.search(SearchRequest("100%_", scope = SearchScope.Body)).hits.single()
        assertEquals(id, hit.sourceId)
        assertEquals("100%_", hit.snippet.substring(hit.matchStart, hit.matchEnd))
        // 未来格式升级的入口必须先屏蔽旧结果，再逐源恢复。
        mIndex.stop()
        db.execSQL("UPDATE search_progress SET cursor = 0 WHERE kind = -1")
        try {
            mQueries.search(SearchRequest("needle", scope = SearchScope.Body))
            throw AssertionError("Incompatible format was accepted")
        } catch (_: IllegalStateException) { }
        mIndex.retry()
        ready()
        assertEquals(id, mQueries.search(SearchRequest("needle", scope = SearchScope.Body)).hits.single().sourceId)
    }

    /** 保留仍存在的 Summary 状态，循环删除的真实消息不会留下无限墓碑。 */
    @Test
    fun repeatedDeletionPrunesTombstonesButRetainsExcludedLiveSources() = runBlocking {
        val chat = chat("Cycles", 1)
        val summary = message(chat, "internal", ChatMessage.Source.Summary)
        repeat(20) {
            val id = message(chat, "cycle $it " + "雪乃".repeat(1500))
            ready()
            mDatabase.openHelper.writableDatabase.execSQL("DELETE FROM chat_messages WHERE id = ?", arrayOf(id))
            withTimeout(10_000) { while (mDatabase.getContentSearchDao().source(1, id) != null) delay(25) }
            assertNull(mDatabase.getContentSearchDao().source(1, id))
            assertEquals(0L, scalar("SELECT count(*) FROM search_chunks"))
        }
        assertNotNull(mDatabase.getContentSearchDao().source(1, summary))
        assertEquals(1L, scalar("SELECT count(*) FROM search_sources"))
    }

    /** 同时停止、重启、恢复后只保留一组当前块；生命周期控制不能放出两个工作器。 */
    @Test
    fun concurrentLifecycleChangesLeaveOneCurrentProjection() = runBlocking {
        val chat = chat("Lifecycle", 1)
        val text = "needle " + "雪".repeat(60_000)
        message(chat, text)
        mIndex.restore()
        val stopping = launch { mIndex.stop() }
        val retrying = launch { mIndex.retry() }
        repeat(5) { mIndex.restore() }
        stopping.join()
        retrying.join()
        ready()
        assertEquals(SearchTextCodec.chunks(text).count().toLong(), scalar("SELECT count(*) FROM search_chunks"))
        assertEquals(1, mQueries.search(SearchRequest("needle", scope = SearchScope.Body)).hits.size)
    }

    private fun scalar(sql: String): Long = mDatabase.openHelper.readableDatabase.query(sql).use {
        check(it.moveToFirst())
        it.getLong(0)
    }

    private suspend fun ready() {
        mIndex.enable()
        withTimeout(30_000) {
            while (true) {
                assertFalse("Index failed", mIndex.indexState.value == SearchIndexState.Unavailable)
                val progress = mDatabase.getContentSearchDao().progress()
                if (progress.size == 6 && progress.filter { it.kind > 0 }.all { it.complete } && !mDatabase.getContentSearchDao().hasPending() && mIndex.indexState.value == SearchIndexState.Ready) break
                delay(50)
            }
        }
    }

    private suspend fun chat(title: String, time: Long): Long = mDatabase.getChatSessionDao().insertOrReplace(
        ChatSession(characterId = mCharacterId, createTime = time, latestTime = time, lorebookEntrySet = "[]", title = title, userNote = "")
    )

    private suspend fun message(chat: Long, text: String, source: ChatMessage.Source = ChatMessage.Source.User): Long =
        mDatabase.getChatMessageDao().insertOrReplace(ChatMessage(sessionId = chat, createTime = 1, source = source, content = text))
}
