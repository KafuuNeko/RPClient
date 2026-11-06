package me.kafuuneko.rpclient.libs.backup

import android.util.Xml
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** 完整恢复只读取归档自己的偏好，同时验证 SP 选择和稀疏覆盖兼容。 */
@RunWith(AndroidJUnit4::class)
class PromptPresetRestoreTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    /** 旧备份从暂存偏好迁入，显式空串、换行、宏和其他偏好原文保持不变。 */
    @Test
    fun oldArchiveUsesItsOwnPreferenceXmlAndPreservesOtherSettings() = runBlocking {
        withStaging { database, directory, file ->
            file.writeText(
                """
                <map>
                  <string name="mainPrompt"></string>
                  <string name="groupSummarizePrompt"> &#13;
                {{words}} &amp; memory</string>
                  <int name="lastMigratedVersionCode" value="20260103" />
                  <string name="unrelated"> &#13; untouched &amp; value </string>
                  <set name="sampleSet"><string>one</string><string>two</string></set>
                </map>
            """.trimIndent()
            )
            val onlineSelection = AppModel.activePromptPresetId
            PromptPresetRestore.prepare(
                mContext,
                database.openHelper.writableDatabase,
                5,
                directory
            )
            val snapshot =
                PromptPresetRepository(database).getDetail(PromptTemplateSnapshot.LEGACY_ID).snapshot
            // 校验实际读取的是归档值，并且新选择只写到暂存文件。
            assertEquals("", snapshot.mainPrompt)
            assertTrue(snapshot.groupSummarizePrompt.startsWith(" \r\n"))
            assertTrue(snapshot.groupSummarizePrompt.endsWith("{{words}} & memory"))
            assertEquals(2, database.getPromptPresetDao().getEntries(snapshot.presetId).size)
            assertEquals(onlineSelection, AppModel.activePromptPresetId)
            assertEquals("1", readValue(file, "activePromptPresetId"))
            assertEquals(" \r untouched & value ", readValue(file, "unrelated"))
            assertTrue(file.readText().contains("<string>two</string>"))
        }
    }

    /** 错误类型、根元素和重复键不能被误当作默认配置。 */
    @Test
    fun malformedLegacyPreferencesDoNotCreatePresets() {
        val cases = listOf(
            "<unexpected><string name=\"mainPrompt\">saved</string></unexpected>",
            "<map><string name=\"lastMigratedVersionCode\">20260404</string></map>",
            "<map><boolean name=\"mainPrompt\" value=\"true\" /></map>",
            "<map><string name=\"mainPrompt\">a</string><string name=\"mainPrompt\">b</string></map>",
        )
        // 每种异常使用独立库，失败不得产生半套自定义预设。
        cases.forEach { value ->
            withStaging { database, directory, file ->
                file.writeText(value)
                val db = database.openHelper.writableDatabase
                assertTrue(runCatching {
                    PromptPresetRestore.prepare(
                        mContext,
                        db,
                        5,
                        directory
                    )
                }.isFailure)
                db.query("SELECT COUNT(*) FROM prompt_presets")
                    .use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
        }
    }

    /** 删除当前预设的可编辑备份回到 -1，空的新库也无需默认记录或状态表。 */
    @Test
    fun missingSelectionFallsBackInStagedSpWithoutCreatingDefaultRows() {
        withStaging { database, directory, file ->
            file.writeText("<map><long name=\"activePromptPresetId\" value=\"42\" /><boolean name=\"kept\" value=\"true\" /></map>")
            val db = database.openHelper.writableDatabase
            PromptPresetRestore.prepare(mContext, db, 6, directory)
            assertEquals("-1", readValue(file, "activePromptPresetId"))
            assertEquals("true", readValue(file, "kept"))
            db.query("SELECT COUNT(*) FROM prompt_presets")
                .use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    /** 通过 XML 解析断言文本值，避免序列化引号或字符引用格式影响检查。 */
    private fun readValue(file: File, key: String): String? = file.inputStream().use { input ->
        val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.depth == 2 && parser.getAttributeValue(
                    null,
                    "name"
                ) == key
            ) {
                return@use if (parser.name == "string") parser.nextText() else parser.getAttributeValue(
                    null,
                    "value"
                )
            }
            parser.next()
        }
        null
    }

    /** 所有读写均限定在本测试创建的暂存目录与内存数据库。 */
    private inline fun <T> withStaging(block: (AppDatabase, File, File) -> T): T {
        val directory =
            File(mContext.cacheDir, "prompt-restore-${UUID.randomUUID()}").apply { mkdirs() }
        val database = Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java).build()
        try {
            return block(database, directory, File(directory, AppModel.kotprefName + ".xml"))
        } finally {
            database.close(); directory.deleteRecursively()
        }
    }
}
