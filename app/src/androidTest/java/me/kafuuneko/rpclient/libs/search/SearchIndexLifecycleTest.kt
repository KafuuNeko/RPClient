package me.kafuuneko.rpclient.libs.search

import android.view.WindowManager
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
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
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/** 文件库验证真实开关、触发器升级、恢复与空间回收，不以 Mock 代替持久边界。 */
@RunWith(AndroidJUnit4::class)
class SearchIndexLifecycleTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mName = "search-lifecycle-test.sqlite"
    private lateinit var mScenario: ActivityScenario<MainActivity>
    private lateinit var mDatabase: AppDatabase
    private lateinit var mRepository: ContentSearchRepository

    @get:Rule val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
    )

    /** 每项使用独立合成文件库，保持前台以避免系统冻结后台测试。 */
    @Before
    fun setUp() {
        mScenario = ActivityScenario.launch(MainActivity::class.java)
        mScenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        mContext.deleteDatabase(mName)
        open()
    }

    /** 等待管理器退出后才删除本项合成库。 */
    @After
    fun tearDown() = runBlocking {
        mRepository.stop()
        mDatabase.close()
        mContext.deleteDatabase(mName)
        mScenario.close()
    }

    /** 默认关闭、重新打开与三类原文搜索均不生成 FTS 待办。 */
    @Test
    fun disabledScanWorksWithoutIndexAcrossReopenAndEdits() = runBlocking {
        val ids = corpus()
        mRepository.restore()
        awaitState { searchIndexState -> searchIndexState == SearchIndexState.Disabled }
        val request = SearchRequest(query = "hello world", scope = SearchScope.Body)
        assertEquals(setOf(1, 2, 3), mRepository.search(request).hits.map { hit -> hit.kind }.toSet())
        assertTrue(mRepository.search(SearchRequest(query = "hiddenneedle", scope = SearchScope.Body)).hits.isEmpty())
        assertEquals(3, mRepository.search(SearchRequest(query = "100%_", scope = SearchScope.Body)).hits.size)
        assertEmptyIndex()
        // 关闭后保存和重启只影响原文，不能重新安装专用维护入口。
        mDatabase.getChatMessageDao().updateMessageContent(ids.first, "changedneedle")
        reopen()
        mRepository.restore()
        awaitState { searchIndexState -> searchIndexState == SearchIndexState.Disabled }
        assertEquals(
            ids.first,
            mRepository.search(SearchRequest(query = "changedneedle", scope = SearchScope.Body)).hits.single().sourceId,
        )
        assertEmptyIndex()
    }

    /** 两种搜索保持命中语义；关闭释放文件，所有跨模式游标均失效。 */
    @Test
    fun enableDisableReclaimsSpaceAndRejectsOldCursors() = runBlocking {
        val character = character()
        repeat(times = 25) { i ->
            val chat = chat(character, i.toLong())
            message(chat, "needle hello **world** 100%_ " + "雪乃".repeat(2500))
        }
        val request = SearchRequest(query = "needle", scope = SearchScope.Body)
        val scanned = mRepository.search(request)
        assertNotNull(scanned.next)
        mRepository.enable()
        awaitReady()
        assertStale(request, scanned.next!!)
        val indexed = mRepository.search(request)
        assertEquals(scanned.hits.map { hit -> hit.key }, indexed.hits.map { hit -> hit.key })
        mRepository.stop()
        val storage = SearchBenchmarkStorage(mDatabase, mContext.getDatabasePath(mName))
        val before = storage.snapshot().getLong("databaseBytes")
        // 逻辑记录与实际文件必须一起验证，不能仅以空表证明空间已归还。
        mRepository.disable()
        assertEquals(SearchIndexState.Disabled, mRepository.indexState.value)
        assertEmptyIndex()
        val after = storage.snapshot().getLong("databaseBytes")
        assertTrue("Index files did not shrink: $before -> $after", after < before)
        assertEquals(25L, scalar("SELECT count(*) FROM chat_messages"))
        assertStale(request, indexed.next!!)
        mRepository.enable()
        awaitReady()
        assertStale(request, indexed.next)
        assertEquals(20, mRepository.search(request).hits.size)
    }

    /** 请求入队后立即取消仍以最后一次关闭为准，旧任务不能在清理之后写回。 */
    @Test
    fun immediateCancellationWinsOverQueuedEnableAndRebuild() = runBlocking {
        corpus()
        repeat(times = 8) {
            val enabling = launch(start = CoroutineStart.UNDISPATCHED) { mRepository.enable() }
            val disabling = launch(start = CoroutineStart.UNDISPATCHED) { mRepository.disable() }
            enabling.join()
            disabling.join()
            assertEquals(SearchIndexState.Disabled, mRepository.indexState.value)
            assertEmptyIndex()
        }
        // 重建本身的清理与启动也是一个有序操作，随后取消不能被尾部开启覆盖。
        val rebuilding = launch(start = CoroutineStart.UNDISPATCHED) { mRepository.retry() }
        val cancelling = launch(start = CoroutineStart.UNDISPATCHED) { mRepository.disable() }
        rebuilding.join()
        cancelling.join()
        assertEquals(SearchIndexState.Disabled, mRepository.indexState.value)
        assertEmptyIndex()
    }

    /** 外层 REPLACE 不能重置已存在的源版本，否则旧分块可能冒充新内容。 */
    @Test
    fun replacingSameIdPreservesMonotonicSourceRevision() = runBlocking {
        val (id, _) = corpus()
        mRepository.enable()
        awaitReady()
        mRepository.stop()
        val old = mDatabase
            .getChatMessageDao()
            .getByIds(listOf(id))
            .single()
        val revision = mDatabase.getContentSearchDao().source(kind = 1, id = id)!!.revision
        mDatabase.getChatMessageDao().insertOrReplace(old.copy(content = "replacementneedle"))
        assertTrue(mDatabase.getContentSearchDao().source(kind = 1, id = id)!!.revision > revision)
        assertFalse(mRepository.search(SearchRequest(query = "hello world", scope = SearchScope.Body)).hits.any { hit -> hit.kind == 1 })
        mRepository.restore()
        awaitReady()
        assertEquals(
            id,
            mRepository.search(SearchRequest(query = "replacementneedle", scope = SearchScope.Body)).hits.single().sourceId,
        )
    }

    /** 普通搜索分页只跟踪业务变更；不靠关闭后残留的搜索版本号。 */
    @Test
    fun scanPaginationRejectsBusinessChangesWithoutFtsProgress() = runBlocking {
        val character = character()
        repeat(times = 25) { value -> message(chat(character, value.toLong()), "needle") }
        val request = SearchRequest(query = "needle", scope = SearchScope.Body)
        val first = mRepository.search(request)
        val second = mRepository.search(request, first.next)
        assertEquals(25, (first.hits + second.hits).map { hit -> hit.key }.distinct().size)
        mDatabase.openHelper.writableDatabase.execSQL("UPDATE chat_messages SET content = 'replacement' WHERE id = 1")
        assertStale(request, first.next!!)
        assertEmptyIndex()
    }

    /** 取消及构建中断都清理半成品；本项关闭重开覆盖文件持久边界，不冒充强杀进程。 */
    @Test
    fun cancelAndInterruptedBuildRemovePartialChunks() = runBlocking {
        val chat = chat(character = character(), time = 1)
        message(chat, "needle " + "雪".repeat(1_100_000))
        mRepository.enable()
        awaitChunks()
        mRepository.disable()
        assertEmptyIndex()
        assertEquals(SearchIndexState.Disabled, mRepository.indexState.value)
        // 再次构建到实际分块写入后关闭文件，重开不得静默完成昂贵补建。
        mRepository.enable()
        awaitChunks()
        reopen()
        mRepository.restore()
        awaitState { searchIndexState -> searchIndexState == SearchIndexState.Disabled }
        assertEmptyIndex()
        assertEquals(
            chat,
            mRepository.search(SearchRequest(query = "needle", scope = SearchScope.Body)).hits.single().parentId,
        )
    }

    /** 已启用重启保留索引，并处理关闭进程前提交的编辑、删除与同 ID 替换。 */
    @Test
    fun enabledReopenResumesPendingWithoutPublishingStaleContent() = runBlocking {
        val (edited, deleted) = corpus()
        mRepository.enable()
        awaitReady()
        mRepository.stop()
        mDatabase.getChatMessageDao().updateMessageContent(edited, "replacementneedle")
        mDatabase.openHelper.writableDatabase.execSQL("DELETE FROM story_chapters WHERE id = ?", arrayOf(deleted))
        reopen()
        assertEquals(SearchIndexPhase.Enabled, SearchDatabaseCallback.phase(mDatabase.openHelper.readableDatabase))
        assertFalse(mRepository.search(SearchRequest(query = "hello world", scope = SearchScope.Body)).hits.any { hit -> hit.kind == 1 || hit.kind == 3 })
        mRepository.restore()
        awaitReady()
        assertEquals(
            edited,
            mRepository.search(SearchRequest(query = "replacementneedle", scope = SearchScope.Body)).hits.single().sourceId,
        )
        assertEquals(0L, scalar("SELECT count(*) FROM search_sources WHERE dirty = 1"))
    }

    /** 旧同名定义会更新；全字段同值 UPDATE 与不相关字段不作废搜索分页。 */
    @Test
    fun upgradesInstalledTriggersAndNarrowsMetadataInvalidation() = runBlocking {
        corpus()
        mRepository.enable()
        awaitReady()
        mRepository.stop()
        val db = mDatabase.openHelper.writableDatabase
        db.execSQL("DROP TRIGGER search_meta_character_UPDATE")
        db.execSQL(sql = "CREATE TRIGGER search_meta_character_UPDATE AFTER UPDATE ON character BEGIN UPDATE search_progress SET cursor = cursor + 1 WHERE kind = 0; END")
        reopen()
        val before = scalar("SELECT cursor FROM search_progress WHERE kind = 0")
        mDatabase.openHelper.writableDatabase.execSQL("UPDATE character SET name = name, description = 'unrelated'")
        assertEquals(before, scalar("SELECT cursor FROM search_progress WHERE kind = 0"))
        mDatabase.openHelper.writableDatabase.execSQL("UPDATE character SET name = 'renamed'")
        assertEquals(before + 1, scalar("SELECT cursor FROM search_progress WHERE kind = 0"))
        val schemaVersion = scalar("PRAGMA schema_version")
        reopen()
        assertEquals(schemaVersion, scalar("PRAGMA schema_version"))
    }

    /** 清理故障不能发布已关闭完成；停用维护后业务保存仍然可用，重开继续清理。 */
    @Test
    fun cleanupFailurePersistsUntilRetryAndDoesNotRegeneratePending() = runBlocking {
        val (message, _) = corpus()
        mRepository.enable()
        awaitReady()
        mDatabase.openHelper.writableDatabase.execSQL(sql = "CREATE TRIGGER test_block_cleanup BEFORE DELETE ON search_chunks BEGIN SELECT RAISE(ABORT, 'Injected cleanup failure'); END")
        mRepository.disable()
        assertEquals(SearchIndexState.CleanupFailed, mRepository.indexState.value)
        assertEquals(SearchIndexPhase.Cleaning, SearchDatabaseCallback.phase(mDatabase.openHelper.readableDatabase))
        val revision = scalar("SELECT revision FROM search_sources WHERE kind = 1 AND sourceId = $message")
        mDatabase.getChatMessageDao().updateMessageContent(message, "afterfailure")
        assertEquals(revision, scalar("SELECT revision FROM search_sources WHERE kind = 1 AND sourceId = $message"))
        // 移除注入故障后模拟重启，保留的 Cleaning 不能被错当成 Enabled。
        mDatabase.openHelper.writableDatabase.execSQL("DROP TRIGGER test_block_cleanup")
        reopen()
        mRepository.restore()
        awaitState { searchIndexState -> searchIndexState == SearchIndexState.Disabled }
        assertEmptyIndex()
        assertEquals(
            message,
            mRepository.search(SearchRequest(query = "afterfailure", scope = SearchScope.Body)).hits.single().sourceId,
        )
    }

    /** 旧自动索引不代表用户同意；投影格式变化同样回退清理，不静默重建。 */
    @Test
    fun legacyAutomaticIndexAndFormatChangesRequireNewConsent() = runBlocking {
        corpus()
        mRepository.enable()
        awaitReady()
        mRepository.stop()
        mDatabase.openHelper.writableDatabase.execSQL("DELETE FROM search_progress WHERE kind = -2")
        reopen()
        mRepository.restore()
        awaitState { searchIndexState -> searchIndexState == SearchIndexState.Disabled }
        assertEmptyIndex()
        mRepository.enable()
        awaitReady()
        mRepository.stop()
        mDatabase.openHelper.writableDatabase.execSQL("UPDATE search_progress SET cursor = 0 WHERE kind = -1")
        reopen()
        mRepository.restore()
        awaitState { searchIndexState -> searchIndexState == SearchIndexState.Disabled }
        assertEmptyIndex()
    }

    /** 从真实 v4 文件升级，三类历史正文默认可扫描，确认后完整建索引并可再次打开。 */
    @Test
    fun v4MigrationPreservesHistoricalContentAndDefaultsToScan() = runBlocking {
        mRepository.stop()
        mDatabase.close()
        mContext.deleteDatabase(mName)
        migrationHelper.createDatabase(name = mName, version = 4).use { db ->
            db.execSQL(sql = "INSERT INTO character(id,name,avatar,characterTags,description,personality,scenario,firstMessages,examplesOfDialogue,postHistoryInstructions) VALUES(1,'fixture','','[]','','','','[]','','')")
            db.execSQL(sql = "INSERT INTO chat_sessions(id,characterId,createTime,latestTime,lorebookEntrySet,title,userNote,userName,userDescription,worldInfoStateJson,autoSummaryPaused) VALUES(1,1,1,1,'[]','chat','','user','','{}',0)")
            db.execSQL(sql = "INSERT INTO group_chat_sessions(id,title,createTime,latestTime,userName,userDescription,scenario,userNote,lorebookEntrySet,worldInfoStateJson,systemPromptOverride,groupNudgePromptOverride,newGroupChatPromptOverride,activationStrategy,allowSelfResponses,characterCardMode,includeMutedCards,autoModeEnabled,trimOtherSpeakers,autoSummaryPaused) VALUES(1,'group',1,1,'user','','','','[]','{}','','','','Natural',0,'Swap',0,0,1,0)")
            db.execSQL(sql = "INSERT INTO stories(id,title,memory,summary,authorNote,worldInfoGenerationStep,revision,createTime,latestTime) VALUES(1,'story','','','',0,7,1,1)")
            // 超过播种批次，验证检查点不是只覆盖首批旧数据。
            repeat(times = 230) { i ->
                db.execSQL(
                    sql = "INSERT INTO chat_messages(sessionId,createTime,source,content) VALUES(1,?,'User',?)",
                    bindArgs = arrayOf<Any>(
                        i,
                        "legacyneedle $i",
                    ),
                )
                db.execSQL(
                    sql = "INSERT INTO group_chat_messages(sessionId,createTime,source,content,speakerNameSnapshot) VALUES(1,?,'User',?,'user')",
                    bindArgs = arrayOf<Any>(
                        i,
                        "legacyneedle $i",
                    ),
                )
                db.execSQL(
                    sql = "INSERT INTO story_chapters(storyId,title,content,sortOrder,contentRevision,createTime,latestTime) VALUES(1,'chapter',?,?,0,1,1)",
                    bindArgs = arrayOf<Any>(
                        "legacyneedle $i",
                        i,
                    ),
                )
            }
        }
        open()
        val request = SearchRequest(query = "legacyneedle 229", scope = SearchScope.Body)
        assertEquals(setOf(1, 2, 3), mRepository.search(request).hits.map { hit -> hit.kind }.toSet())
        assertEmptyIndex()
        mRepository.enable()
        awaitReady()
        reopen()
        mRepository.restore()
        awaitReady()
        assertEquals(setOf(1, 2, 3), mRepository.search(request).hits.map { hit -> hit.kind }.toSet())
        assertEquals(7L, scalar("SELECT revision FROM stories WHERE id = 1"))
        assertEquals(5L, scalar("PRAGMA user_version"))
    }

    private fun open() {
        mDatabase = Room.databaseBuilder(mContext, AppDatabase::class.java, mName)
            .addCallback(SearchDatabaseCallback)
            .build()
        mRepository = ContentSearchRepository(mDatabase)
    }

    private suspend fun reopen() {
        mRepository.stop()
        mDatabase.close()
        open()
    }

    /** 三类正文共用可见语义，think 与摘要均不进入结果。 */
    private suspend fun corpus(): Pair<Long, Long> {
        val chat = chat(character = character(), time = 1)
        val text = "Hello **world** 100%_ 👩🏽‍💻 <think>hiddenneedle</think>"
        val id = message(chat, text)
        message(chat, "hiddenneedle", ChatMessage.Source.Summary)
        val group = mDatabase.getGroupChatSessionDao().insertOrReplace(
            data = GroupChatSession(
                title = "Group",
                createTime = 1,
                latestTime = 2,
                userName = "User",
                userDescription = "",
            ),
        )
        mDatabase.getGroupChatMessageDao().insertOrReplace(
            data = GroupChatMessage(
                sessionId = group,
                createTime = 1,
                source = GroupChatMessage.Source.User,
                content = text,
                speakerNameSnapshot = "User",
            ),
        )
        val story = mDatabase.getStoryDao().insertOrReplace(Story(title = "Story", createTime = 1, latestTime = 3))
        val chapter = mDatabase.getStoryChapterDao().insertOrReplace(
            data = StoryChapter(
                storyId = story,
                title = "Chapter",
                content = text,
                sortOrder = 0,
                createTime = 1,
                latestTime = 1,
            ),
        )
        return id to chapter
    }

    private suspend fun character(): Long = mDatabase.getCharacterDao().insertOrReplace(
        data = Character(
            name = "Fixture",
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

    private suspend fun chat(character: Long, time: Long): Long = mDatabase.getChatSessionDao().insertOrReplace(
        data = ChatSession(
            characterId = character,
            createTime = time,
            latestTime = time,
            lorebookEntrySet = "[]",
            title = "Chat $time",
            userNote = "",
        ),
    )

    private suspend fun message(chat: Long, text: String, source: ChatMessage.Source = ChatMessage.Source.User): Long =
        mDatabase.getChatMessageDao().insertOrReplace(
            data = ChatMessage(
                sessionId = chat,
                createTime = 1,
                source = source,
                content = text,
            ),
        )

    private suspend fun awaitReady() = withTimeout(timeout = 60_000.milliseconds) {
        while (true) {
            val isIndexStillPending = mRepository.indexState.value != SearchIndexState.Ready
                || mDatabase.getContentSearchDao().hasPending()
                || mDatabase.getContentSearchDao().progress().count { searchProgress ->
                    searchProgress.kind > 0 && searchProgress.complete
                } != 3
            if (!isIndexStillPending) break
            assertFalse(
                "Index failed",
                mRepository.indexState.value == SearchIndexState.CleanupFailed || mRepository.indexState.value == SearchIndexState.Unavailable,
            )
            delay(20.milliseconds)
        }
    }

    private suspend fun awaitState(predicate: (SearchIndexState) -> Boolean) = withTimeout(timeout = 60_000.milliseconds) {
        while (!predicate(mRepository.indexState.value)) {
            assertFalse(
                "Index failed: ${mRepository.indexState.value}",
                mRepository.indexState.value == SearchIndexState.CleanupFailed || mRepository.indexState.value == SearchIndexState.Unavailable,
            )
            delay(20.milliseconds)
        }
    }

    private suspend fun awaitChunks() = withTimeout(timeout = 30_000.milliseconds) {
        while (scalar("SELECT count(*) FROM search_chunks") == 0L) delay(5.milliseconds)
        assertTrue(mRepository.indexState.value is SearchIndexState.Building)
    }

    private suspend fun assertStale(request: SearchRequest, cursor: SearchCursor) {
        try {
            mRepository.search(request, cursor)
            throw AssertionError("Stale cursor accepted")
        } catch (_: SearchChangedException) { }
    }

    private fun assertEmptyIndex() {
        assertEquals(0L, scalar("SELECT count(*) FROM search_sources"))
        assertEquals(0L, scalar("SELECT count(*) FROM search_chunks"))
        assertEquals(0L, scalar("SELECT count(*) FROM search_chunks_fts_segdir"))
        assertEquals(0L, scalar("SELECT count(*) FROM search_progress WHERE kind != -2"))
        assertEquals(
            0L,
            scalar(sql = "SELECT count(*) FROM sqlite_master WHERE type = 'trigger' AND (name GLOB 'search_source_*' OR name GLOB 'search_meta_*')"),
        )
    }

    private fun scalar(sql: String): Long = mDatabase.openHelper.readableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
