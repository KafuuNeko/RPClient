package me.kafuuneko.rpclient.libs.search

import android.view.WindowManager
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/** 两次 instrumentation 之间由外部强杀进程，区别于普通 close/reopen 回归。 */
@RunWith(AndroidJUnit4::class)
class SearchProcessRecoveryTest {
    /** prepare 写入实际半成品后等待强杀；verify 在新进程断言恢复与原文完整性。 */
    @Test
    fun recoverAfterExternalProcessKill() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("searchCrashStage")
        assumeTrue(stage == "prepare" || stage == "verify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "search-process-recovery.sqlite"
        val marker = context.cacheDir.resolve("search-crash-ready")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { mainActivity -> mainActivity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (stage == "prepare") {
            context.deleteDatabase(name)
            marker.delete()
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addCallback(SearchDatabaseCallback)
            .build()
        val index = ContentSearchRepository(database)
        try {
            if (stage == "prepare") {
                prepareCorpus(database)
                index.enable()
                withTimeout(timeout = 30_000.milliseconds) {
                    while (scalar(database, "SELECT count(*) FROM search_chunks") == 0L) delay(timeMillis = 5)
                }
                assertTrue(index.indexState.value is SearchIndexState.Building)
                marker.writeText("building")
                // 此等待只能由测试驱动器强杀，不能正常 stop 伪装成崩溃。
                withTimeout(timeout = 120_000.milliseconds) { while (true) delay(timeMillis = 1000) }
            } else {
                assertTrue(marker.exists())
                index.restore()
                withTimeout(timeout = 60_000.milliseconds) {
                    while (index.indexState.value != SearchIndexState.Disabled) {
                        check(index.indexState.value != SearchIndexState.CleanupFailed)
                        delay(25.milliseconds)
                    }
                }
                assertEquals(250L, scalar(database, "SELECT count(*) FROM chat_messages"))
                assertEquals(0L, scalar(database, "SELECT count(*) FROM search_chunks"))
                assertEquals(0L, scalar(database, "SELECT count(*) FROM search_sources"))
                assertEquals(0L, scalar(database, "SELECT count(*) FROM search_progress WHERE kind != -2"))
                assertEquals(
                    1,
                    index.search(SearchRequest(query = "crashneedle249", scope = SearchScope.Body)).hits.size,
                )
            }
        } finally {
            index.stop()
            database.close()
            scenario.close()
            if (stage == "verify") {
                context.deleteDatabase(name)
                marker.delete()
            }
        }
    }

    /** 合成语料为强杀驱动器提供足够构建窗口，不接触正式数据库。 */
    private suspend fun prepareCorpus(database: AppDatabase) {
        val character = database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Crash fixture",
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
                title = "Crash fixture",
                userNote = "",
            ),
        )
        // 分批导入后才开启，准备阶段没有搜索维护写入。
        for (batch in (0 until 250).chunked(25)) database.withTransaction {
            database.getChatMessageDao().insertOrReplaceAll(
                data = batch.map { value -> ChatMessage(
                    sessionId = chat,
                    createTime = value.toLong(),
                    source = ChatMessage.Source.User,
                    content = "crashneedle$value " + "雪".repeat(50_000),
                ) },
            )
        }
    }

    private fun scalar(database: AppDatabase, sql: String): Long = database.openHelper.readableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
