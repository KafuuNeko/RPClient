package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LLMRequestLogRepositoryTest {
    private lateinit var database: RequestLogDatabase
    private lateinit var repository: LLMRequestLogRepository
    private var previousDebugMode = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Kotpref.init(context)
        previousDebugMode = AppModel.debugModeEnabled
        AppModel.debugModeEnabled = true
        database = Room.inMemoryDatabaseBuilder(context, RequestLogDatabase::class.java).build()
        repository = LLMRequestLogRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        AppModel.debugModeEnabled = previousDebugMode
    }

    @Test
    fun saveReadAndDelete_persistsRawRequestAndResponse() = runBlocking {
        val sentinel = "PRIVATE_SENTINEL_92f1"
        val requestJson = """{"messages":[{"role":"user","content":"$sentinel"}]}"""
        val responseJson = """{"choices":[{"message":{"content":"$sentinel"}}]}"""
        val id = repository.saveLog(
            provider = LLMProviderConfig(
                name = "Local",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://example.invalid",
                model = "model",
            ),
            model = "model",
            isStreaming = false,
            requestJson = requestJson,
            responseJson = responseJson,
        )

        val logs = repository.getLogOverviews(previewLength = 24, limit = 50, offset = 0)
        assertTrue(id > 0)
        assertEquals(1, logs.size)
        assertEquals(requestJson.take(24), logs.single().requestPreview)
        assertEquals(responseJson.take(24), logs.single().responsePreview)
        assertEquals(requestJson, repository.getRequestJson(id))
        assertEquals(responseJson, repository.getResponseJson(id))

        repository.deleteAll()
        assertTrue(repository.getLogOverviews(previewLength = 24, limit = 50, offset = 0).isEmpty())
        assertEquals(null, repository.getRequestJson(id))
        assertEquals(null, repository.getResponseJson(id))
    }

    @Test
    fun saveLog_doesNotPersistWhenDebugModeIsDisabled() = runBlocking {
        AppModel.debugModeEnabled = false

        val id = repository.saveLog(
            provider = LLMProviderConfig(
                name = "Local",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://example.invalid",
                model = "model",
            ),
            model = "model",
            isStreaming = false,
            requestJson = "raw request",
            responseJson = "raw response",
        )

        assertEquals(0L, id)
        assertTrue(repository.getLogOverviews(previewLength = 24, limit = 50, offset = 0).isEmpty())
    }

    /** 大于数据库窗口的正文必须通过分块读取，并保持 UTF-8 及备份字段完整。 */
    @Test
    fun largeLog_roundTripsAcrossCursorChunksAndBackup() = runBlocking {
        val content = "中😀\n".repeat(350_000)
        val request = "{\"content\":\"" + content.replace("\n", "\\n") + "\"}"
        val provider = LLMProviderConfig(
            name = "Local",
            providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://example.invalid",
            model = "model",
        )
        // 该合成数据超过常见 CursorWindow 容量，但仍处于新录制预算内。
        val id = repository.saveLog(provider, "model", false, request, request)
        assertEquals(request, repository.getRequestJson(id))
        assertEquals(request, repository.getResponseJson(id))
        val rows = BackupDatabaseCodec.readRows(
            database.openHelper.readableDatabase,
            "llm_request_logs",
            "id",
            id
        )
        assertEquals(request, rows.getJSONObject(0).getString("requestJson"))
        assertEquals(request, rows.getJSONObject(0).getString("responseJson"))
    }

    /** 非法历史原文中的 NUL 不能让 SQLite 文本 SUBSTR 静默丢失后半段。 */
    @Test
    fun rawLog_keepsEmbeddedNulAndUnicode() = runBlocking {
        val raw = "prefix\u0000" + "中😀".repeat(20_000) + "suffix"
        val id = repository.saveLog(
            provider = LLMProviderConfig(
                name = "Local",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://example.invalid",
                model = "model",
            ),
            model = "model",
            isStreaming = false,
            requestJson = raw,
            responseJson = raw,
        )
        assertEquals(raw, repository.getRequestJson(id))
        assertEquals(raw, repository.getResponseJson(id))
    }
}
