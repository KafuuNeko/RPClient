package me.kafuuneko.rpclient.libs.search

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/** 显式启用的混合负载观测，默认回归不运行耗时基准。 */
@RunWith(AndroidJUnit4::class)
class SearchMixedLoadBenchmarkTest {
    /** 相同原文分别测无索引、构建并行、日常增量，保留原始样本和存储采样峰值。 */
    @Test
    fun measureWritesDuringIndexWork() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runSearchMixedBenchmark") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        try {
            val report = JSONObject().put("device", Build.MODEL).put("api", Build.VERSION.SDK_INT)
                .put("clock", "elapsedRealtimeNanos").put("rounds", 40)
                .put("note", "Main dispatcher delay is a responsiveness proxy, not frame timing")
            for (mode in listOf("disabled", "building", "enabled")) report.put(mode, measure(mode))
            context.cacheDir.resolve("search-mixed-load.json").writeText(report.toString(2))
            Log.i("SearchMixedLoad", report.toString())
            Unit
        } finally { scenario.close() }
    }

    /** 每组重建相同合成库，关闭组不安装任何搜索业务触发器。 */
    private suspend fun measure(mode: String): JSONObject = withContext(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "search-mixed-$mode.sqlite"
        context.deleteDatabase(name)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).addCallback(SearchDatabaseCallback).build()
        val index = ContentSearchRepository(database)
        try {
            val (chat, chapter) = corpus(database)
            if (mode != "disabled") index.enable() else {
                index.restore()
                withTimeout(10_000.milliseconds) { while (index.indexState.value != SearchIndexState.Disabled) delay(10) }
            }
            if (mode == "enabled") awaitReady(index)
            if (mode == "building") withTimeout(30_000.milliseconds) {
                while (database.getContentSearchDao().indexedSourceCount() == 0L) delay(10)
            }
            // 工作器保持运行；峰值采样与主线程调度探针贯穿所有写入样本。
            val storage = SearchBenchmarkStorage(database, context.getDatabasePath(name))
            var peak = storage.fileBytes()
            var peakWal = 0L
            val mainDelay = CopyOnWriteArrayList<Double>()
            val monitor = launch {
                while (true) {
                    peak = maxOf(peak, storage.fileBytes())
                    peakWal = maxOf(peakWal, File(context.getDatabasePath(name).path + "-wal").length())
                    val start = SystemClock.elapsedRealtimeNanos()
                    withContext(Dispatchers.Main) { mainDelay += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0 }
                    delay(20)
                }
            }
            val report = JSONObject()
            val states = mutableMapOf<String, Int>()
            try {
                val saves = mutableListOf<Double>()
                val imports = mutableListOf<Double>()
                val autosaves = mutableListOf<Double>()
                repeat(40) { round ->
                    val state = index.indexState.value.javaClass.simpleName
                    states[state] = (states[state] ?: 0) + 1
                    saves += timed { database.getChatMessageDao().insertOrReplace(ChatMessage(sessionId = chat, createTime = 5000L + round,
                        source = ChatMessage.Source.User, content = "save $round needle")) }
                    imports += timed { database.withTransaction {
                        database.getChatMessageDao().insertOrReplaceAll((0 until 25).map { i -> ChatMessage(sessionId = chat,
                            createTime = 6000L + round * 25 + i, source = ChatMessage.Source.User, content = "import $round $i needle") })
                    } }
                    autosaves += timed { assertEquals(1, database.getStoryChapterDao().updateContent(chapter, 1, round.toLong(),
                        "autosave $round " + "word ".repeat(2000), round.toLong())) }
                    delay(30)
                }
                report.put("save", distribution(saves)).put("import25", distribution(imports)).put("autosave", distribution(autosaves))
            } finally { monitor.cancelAndJoin() }
            index.stop()
            report.put("mainDispatchDelay", distribution(mainDelay)).put("sampledPeakBytes", peak).put("sampledPeakWalBytes", peakWal)
                .put("statesAtWrite", JSONObject(states as Map<*, *>)).put("afterWrites", storage.snapshot())
            assertEquals(2640, database.getChatMessageDao().getMessagesBySessionId(chat).size)
            report
        } finally {
            index.stop()
            database.close()
            context.deleteDatabase(name)
        }
    }

    /** 固定正文规模超过一个播种批次，不使用真实聊天数据。 */
    private suspend fun corpus(database: AppDatabase): Pair<Long, Long> {
        val character = database.getCharacterDao().insertOrReplace(Character(name = "Fixture", avatar = "", characterTags = "[]",
            description = "", personality = "", scenario = "", firstMessages = "[]", examplesOfDialogue = "", postHistoryInstructions = ""))
        val chat = database.getChatSessionDao().insertOrReplace(ChatSession(characterId = character, createTime = 1, latestTime = 1,
            lorebookEntrySet = "[]", title = "Mixed", userNote = ""))
        val story = database.getStoryDao().insertOrReplace(Story(id = 1, title = "Mixed", createTime = 1, latestTime = 1))
        val chapter = database.getStoryChapterDao().insertOrReplace(StoryChapter(storyId = story, title = "Draft", content = "initial",
            sortOrder = 0, createTime = 1, latestTime = 1))
        // 三组使用同样的批事务与正文，关闭组连待办触发器都不存在。
        for (batch in (0 until 1600).chunked(100)) database.withTransaction {
            database.getChatMessageDao().insertOrReplaceAll(batch.map { ChatMessage(sessionId = chat, createTime = it.toLong(),
                source = ChatMessage.Source.User, content = "needle $it " + "雨夜咖啡 hello world ".repeat(150)) })
        }
        return chat to chapter
    }

    private suspend fun timed(block: suspend () -> Unit): Double {
        val start = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
    }

    private fun distribution(values: List<Double>): JSONObject {
        val sorted = values.sorted()
        return JSONObject().put("samplesMs", JSONArray(sorted)).put("p50Ms", sorted[sorted.size / 2])
            .put("p95Ms", sorted[((sorted.size * 95 + 99) / 100 - 1).coerceAtLeast(0)])
    }

    private suspend fun awaitReady(index: ContentSearchRepository) = withTimeout(120_000) {
        while (index.indexState.value != SearchIndexState.Ready) {
            check(index.indexState.value != SearchIndexState.CleanupFailed && index.indexState.value != SearchIndexState.Unavailable)
            delay(25)
        }
    }
}
