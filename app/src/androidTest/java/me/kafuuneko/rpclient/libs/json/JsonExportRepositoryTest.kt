package me.kafuuneko.rpclient.libs.json

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JsonExportRepositoryTest {
    /** 保存选择器返回后写入冻结文件，超过剪贴板长度的内容完整保留并清理缓存。 */
    @Test
    fun export_formatsLargeSavedPayloadAndReleasesSnapshot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = JsonExportRepository(context)
        val content = "中😀".repeat(30_000)
        val source = "{\"content\":\"$content\"}"
        val prepared = repository.prepare(source, "request-log-test-request")
        val target = File.createTempFile("json-export-test-", ".json", context.cacheDir)
        try {
            // 使用实际 ContentResolver 文件流，验证成功是在目标内容写完之后返回。
            assertEquals("application/json", prepared.mimeType)
            repository.write(prepared.token, Uri.fromFile(target))
            val text = target.readText(Charsets.UTF_8)
            assertTrue(text.contains("\n  \"content\""))
            assertEquals(content, JsonParser.parseString(text).asJsonObject.get("content").asString)
            assertFalse(File(context.cacheDir, "json-exports/${prepared.token}").exists())
        } finally {
            repository.discard(prepared.token)
            target.delete()
        }
    }

    /** 非法历史日志保留原文，并在打开选择器前确定文本 MIME 和扩展名。 */
    @Test
    fun export_preservesInvalidLegacyText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = JsonExportRepository(context)
        val source = "{\"content\":\"unfinished\n[log truncated]"
        val prepared = repository.prepare(source, "request-log-test-response")
        val target = File.createTempFile("json-export-test-", ".txt", context.cacheDir)
        try {
            assertEquals("text/plain", prepared.mimeType)
            assertTrue(prepared.fileName.endsWith(".raw.txt"))
            repository.write(prepared.token, Uri.fromFile(target))
            assertEquals(source, target.readText(Charsets.UTF_8))
        } finally {
            repository.discard(prepared.token)
            target.delete()
        }
    }
}
