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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** 显式启用的合成大库基准，默认回归不生成百 MB 数据。 */
@RunWith(AndroidJUnit4::class)
class ContentSearchBenchmarkTest {
    /** 显式运行实机全流程基准，报告原始耗时及性能减半的两倍耗时估算。 */
    @Test
    fun measureLargeCorpus() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runSearchBenchmark") == "true")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { mainActivity -> mainActivity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "content-search-benchmark.sqlite"
        context.deleteDatabase(name)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addCallback(SearchDatabaseCallback)
            .build()
        val index = ContentSearchRepository(database)
        var monitor: Job? = null
        try {
            val sizeMiB =
                InstrumentationRegistry.getArguments().getString("benchmarkMiB")?.toInt() ?: 100
            val (count, bytes) = createCorpus(database, sizeMiB)
            val storage = SearchBenchmarkStorage(database, context.getDatabasePath(name))
            val baseline = storage.snapshot()
            var sampledPeak = storage.fileBytes()
            monitor = launch(Dispatchers.IO) {
                while (true) {
                    sampledPeak = maxOf(sampledPeak, storage.fileBytes())
                    delay(timeMillis = 20)
                }
            }
            val began = SystemClock.elapsedRealtime()
            index.enable()
            awaitReady(database, index)
            val buildMs = SystemClock.elapsedRealtime() - began
            index.stop()
            monitor?.cancelAndJoin()
            // 包含对象聚合、排序与片段读取，不能只计裸 MATCH 的时间。
            val report = JSONObject().put("sourceBytes", bytes)
                .put("messages", count)
                .put("objects", 1000)
                .put("buildMs", buildMs)
                .put("device", Build.MODEL)
                .put("api", Build.VERSION.SDK_INT)
                .put("label", InstrumentationRegistry.getArguments().getString("benchmarkLabel"))
                .put("beforeIndex", baseline)
                .put("indexed", storage.snapshot())
                .put("sampledBuildPeakBytes", sampledPeak)
                .put("performanceDivisor", 2)
                .put("adjustedBuildMs", buildMs * 2)
            val queryResults = measureQueries(index)
            queryResults.keys().forEach { key -> report.put(key, queryResults.get(key)) }
            val file = context.getDatabasePath(name)
            val disk = listOf(
                file,
                File(file.path + "-wal"),
                File(file.path + "-shm")
            ).sumOf { file -> file.length() }
            report.put("databaseAndWalBytes", disk).put(
                "sqlite",
                database.openHelper.readableDatabase.query("SELECT sqlite_version()")
                    .use { cursor ->
                        cursor.moveToFirst()
                        cursor.getString(0)
                    },
            )
            // 对比删除后的空闲页与墓碑，不能把文件没有缩小当作删除失败。
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM chat_messages WHERE id <= ?",
                arrayOf(count / 2)
            )
            index.enable()
            awaitReady(database, index)
            index.stop()
            report.put("afterDeleteHalf", storage.snapshot())
            context.cacheDir.resolve("content-search-benchmark.json").writeText(report.toString(2))
            Log.i("SearchBenchmark", report.toString())
            Unit
        } finally {
            monitor?.cancelAndJoin()
            index.stop()
            database.close()
            context.deleteDatabase(name)
            scenario.close()
        }
    }

    /** 使用固定语料与业务写入路径建立可重复的正文基线。 */
    private suspend fun createCorpus(database: AppDatabase, sizeMiB: Int): Pair<Int, Long> {
        val character = database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Benchmark",
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
        val seed = "雨夜的咖啡馆里，雪乃在读一本故事。Hello world `100%_` café 👩🏽‍💻。\n".repeat(220)
        require(sizeMiB in 10..500) { "Benchmark size out of range" }
        val count = sizeMiB * 1024 * 1024 / seed.toByteArray().size + 1
        val sessions = (0 until 1000).map { i ->
            database.getChatSessionDao().insertOrReplace(
                data = ChatSession(
                    characterId = character,
                    createTime = i.toLong(),
                    latestTime = i.toLong(),
                    lorebookEntrySet = "[]",
                    title = "Conversation $i",
                    userNote = "",
                ),
            )
        }
        // 合成数据只在专用测试库内生成，避免接触用户真实会话。
        var bytes = 0L
        for (batch in (0 until count).chunked(100)) {
            database.withTransaction {
                database.getChatMessageDao().insertOrReplaceAll(
                    data = batch.map { i ->
                        val text = "unique${i}boundary $seed"
                        bytes += text.toByteArray().size
                        ChatMessage(
                            sessionId = sessions[i % sessions.size],
                            createTime = i.toLong(),
                            source = ChatMessage.Source.User,
                            content = text,
                        )
                    },
                )
            }
        }
        return count to bytes
    }

    /** 计时包含 Repository 的聚合、分页与片段，不只统计裸 MATCH。 */
    private suspend fun measureQueries(index: ContentSearchRepository): JSONObject {
        val result = JSONObject()
        // 保留全部排序样本与两倍耗时，避免仅展示最快一次查询。
        for (word in listOf("unique203boundary", "雪乃", "的", "100%_")) {
            val samples = (0 until 20).map {
                val start = SystemClock.elapsedRealtimeNanos()
                val result = index.search(SearchRequest(query = word, scope = SearchScope.Body))
                assertTrue(result.complete)
                assertEquals(word, if (word.startsWith("unique")) 1 else 20, result.hits.size)
                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            }.sorted()
            result.put(
                word,
                JSONObject().put("p50Ms", samples[10])
                    .put("p95Ms", samples[18])
                    .put("adjustedP95Ms", samples[18] * 2)
                    .put("samplesMs", JSONArray(samples)),
            )
        }
        return result
    }

    /** 给 500 MiB 实机压力库留出建库时间，故障立即失败，不将未完成索引计入结果。 */
    private suspend fun awaitReady(database: AppDatabase, index: ContentSearchRepository) =
        withTimeout(
            timeout = 1_800_000.milliseconds,
        ) {
            while (true) {
                check(index.indexState.value != SearchIndexState.Unavailable) { "Index failed" }
                val progress = database.getContentSearchDao().progress()
                if (progress.filter { searchProgress -> searchProgress.kind > 0 }
                        .all { searchProgress -> searchProgress.complete } && !database.getContentSearchDao()
                        .hasPending()
                    && index.indexState.value == SearchIndexState.Ready) break
                delay(100.milliseconds)
            }
        }
}
