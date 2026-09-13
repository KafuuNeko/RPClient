package me.kafuuneko.rpclient.libs.llm.adapter

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.feature.common.media.MessageImageAction
import me.kafuuneko.rpclient.feature.common.media.MessageImageCoordinator
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.messageWithBlocks
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 Android 解码、Room、临时 HTTP 文件和 OkHttp 写出验证端到端协议；不访问外网。 */
@RunWith(AndroidJUnit4::class)
class MultimodalImageIntegrationTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var logDatabase: RequestLogDatabase
    private lateinit var files: FileRepository
    private lateinit var media: MessageImageRuntime
    private lateinit var chat: ChatRepository
    private lateinit var logs: LLMRequestLogRepository
    private var sessionId = 0L
    private var previousDebug = false

    @Before
    fun setUp() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "image-wire-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int) = File(directory, name).apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        // 隔离所有数据库和磁盘目录，不读取或修改用户聊天。
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        logDatabase = Room.inMemoryDatabaseBuilder(context, RequestLogDatabase::class.java).build()
        files = FileRepository(context, database)
        media = MessageImageRuntime(context, files)
        chat = ChatRepository(database, Gson(), MessageImageRepository(database, files))
        logs = LLMRequestLogRepository(logDatabase)
        val characterId = database.getCharacterDao().insertOrReplace(Character(name = "test", avatar = "", characterTags = "[]",
            description = "", personality = "", scenario = "", firstMessages = "", examplesOfDialogue = "", postHistoryInstructions = ""))
        sessionId = database.getChatSessionDao().insertOrReplace(ChatSession(characterId = characterId,
            createTime = 1, latestTime = 1, lorebookEntrySet = "[]", title = "test", userNote = "", userName = "test", userDescription = ""))
        previousDebug = AppModel.debugModeEnabled
        AppModel.debugModeEnabled = true
    }

    @After
    fun tearDown() {
        AppModel.debugModeEnabled = previousDebug
        database.close()
        logDatabase.close()
        directory.deleteRecursively()
    }

    /** 创建公开的合成色块图片，不使用用户素材。 */
    private fun fixture(jpeg: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (jpeg) Color.RED else Color.argb(128, 20, 120, 220))
        return File(directory, UUID.randomUUID().toString()).also { file ->
            file.outputStream().use { bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun inMemoryDraftSavesOriginalAndRejectsResultsAfterEditingEnds() = runBlocking {
        val coordinator = MessageImageCoordinator(media, files) {}
        val original = fixture()
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        val uuid = coordinator.state.draft.single()
        // 新 ViewModel 使用独立的内存草稿，不读取旧协调器拥有的暂存。
        val fresh = MessageImageCoordinator(media, files) {}
        assertTrue(fresh.state.draft.isEmpty())
        coordinator.handle(MessageImageAction.Preview(listOf(uuid), 0))
        assertTrue(coordinator.beginSave())
        val destination = File(directory, "saved-original.png")
        coordinator.handle(MessageImageAction.SaveResult(Uri.fromFile(destination)))
        assertTrue(original.readBytes().contentEquals(destination.readBytes()))
        // 同一页面取消编辑后，迟到的选择结果不应污染未发送的草稿。
        coordinator.startEditing(emptyList())
        coordinator.choose(true)
        coordinator.cancelEditing()
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        assertEquals(listOf(uuid), coordinator.state.draft)
        assertNotNull(coordinator.state.error)
        coordinator.releaseDrafts()
        assertTrue(File(context.getDir("repository", Context.MODE_PRIVATE), "staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun requestByteAndImageCountLimitsRejectBeforeNetworkAndReleaseTemporaryFiles() = runBlocking {
        val prepared = media.prepare("limits", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(prepared)))
        val reference = media.references(listOf(saved)).getValue(saved.key.messageId).single()
        val provider = LLMProviderConfig("test", LLMProviderType.Custom, LLMProviderProtocol.OpenAICompatible,
            "https://example.invalid", model = "test")
        // 直接检验最终 JSON 写出，超大扩展字段也不能绕过限制。
        for (count in listOf(1, 13)) {
            val codec = MultimodalWireCodec(provider)
            val message = messageWithBlocks(LLMMessageRole.User, List(count) { LLMContentBlock.Image(reference) })
            val payload = JSONObject().put("messages", JSONArray().put(JSONObject().put("content", codec.content(message))))
            if (count == 1) payload.put("extension", "x".repeat(16 * 1024 * 1024))
            val failure = runCatching { codec.prepare(payload, LLMGenerationRequest(listOf(message)), media) }.exceptionOrNull()
            assertTrue(failure is ImageRequestException)
            assertEquals(if (count == 1) ImageRequestFailure.TooLarge else
                ImageRequestFailure.TooMany,
                (failure as ImageRequestException).failure)
            assertTrue(File(context.cacheDir, "image-requests").listFiles().orEmpty().isEmpty())
        }
        // 请求文件完成编码后，非法地址仍必须释放文件。
        val invalidClient = OpenAICompatibleLLMClient(OkHttpClient(), logs,
            provider.copy(baseUrl = "invalid-address"), media)
        val request = LLMGenerationRequest(listOf(messageWithBlocks(LLMMessageRole.User, listOf(LLMContentBlock.Image(reference)))))
        assertTrue(runCatching { invalidClient.generate(request) }.isFailure)
        assertTrue(File(context.cacheDir, "image-requests").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun invalidAndAnimatedImagesReleaseTheirDrafts() = runBlocking {
        // 伪扩展名与 GIF 动画不能作为第一帧悄悄发送。
        for (bytes in listOf("not an image".toByteArray(), "GIF89a".toByteArray() + ByteArray(40))) {
            val original = File(directory, UUID.randomUUID().toString() + ".png").apply { writeBytes(bytes) }
            assertTrue(runCatching { media.prepare("invalid", Uri.fromFile(original)) }.isFailure)
            assertTrue(File(context.getDir("repository", Context.MODE_PRIVATE), "staging").listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun allProtocolsWriteTextPureImageAndMixedImagesForBothModesWithoutLoggingBytes() = runBlocking {
        val prepared = media.prepare("test", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(prepared)))
        val reference = media.references(listOf(saved)).getValue(saved.key.messageId).single()
        // 穷举三协议、两种传输和三类内容，检查实际 RequestBody 而非仅检查映射对象。
        for (protocol in LLMProviderProtocol.entries) for (stream in listOf(false, true)) for (count in 0..2) {
            var payload = ""
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                payload = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(response(protocol, stream).toResponseBody("application/json".toMediaType())).build()
            }.build()
            val provider = LLMProviderConfig("test", LLMProviderType.Custom, protocol, "https://example.invalid", model = "test")
            val client: LLMClient = when (protocol) {
                LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleLLMClient(http, logs, provider, media)
                LLMProviderProtocol.Gemini -> GeminiLLMClient(http, logs, provider, media)
                LLMProviderProtocol.AnthropicMessages -> AnthropicMessagesLLMClient(http, logs, provider, media)
            }
            val blocks = (0 until count).flatMap { index ->
                listOf(LLMContentBlock.Image(reference)) + if (count == 2) listOf(LLMContentBlock.Text("after-$index")) else emptyList()
            }
            val message = if (count == 0) LLMMessage(LLMMessageRole.User, "text") else messageWithBlocks(LLMMessageRole.User, blocks)
            val request = LLMGenerationRequest(listOf(message), isPromptFinalized = true)
            if (stream) client.streamGenerate(request).toList() else assertEquals("ok", client.generate(request).content)
            val json = JSONObject(payload)
            val messageJson = json.getJSONArray(if (protocol == LLMProviderProtocol.Gemini) "contents" else "messages").getJSONObject(0)
            val contentKey = if (protocol == LLMProviderProtocol.Gemini) "parts" else "content"
            if (count > 0) {
                val content = messageJson.getJSONArray(contentKey)
                assertEquals(if (count == 2) 4 else 1, content.length())
                val first = content.getJSONObject(0)
                val encoded = when (protocol) {
                    LLMProviderProtocol.OpenAICompatible -> first.getJSONObject("image_url").getString("url").substringAfter("base64,")
                    LLMProviderProtocol.Gemini -> first.getJSONObject("inlineData").getString("data")
                    LLMProviderProtocol.AnthropicMessages -> first.getJSONObject("source").getString("data")
                }
                assertEquals(reference.byteCount, Base64.getDecoder().decode(encoded).size.toLong())
                val logId = logs.getLogOverviews(10, 1, 0).single().id
                assertFalse(logs.getRequestJson(logId)!!.contains(encoded))
                assertTrue(logs.getRequestJson(logId)!!.contains("不能直接重放"))
            }
            assertTrue(File(context.cacheDir, "image-requests").listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun exifOrientationsAndTransparencyUseBoundedSendVersionsAndPreserveOriginal() = runBlocking {
        for (orientation in 1..8) {
            val original = fixture(jpeg = true)
            ExifInterface(original).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
            val bytes = original.readBytes()
            val prepared = media.prepare("test", Uri.fromFile(original))
            val saved = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(prepared)))
            val ref = media.references(listOf(saved)).getValue(saved.key.messageId).single()
            assertEquals(if (orientation in 5..8) 160 else 320, ref.width)
            assertEquals(if (orientation in 5..8) 320 else 160, ref.height)
            assertTrue(files.withFileLease(ref.uuid) { it.readBytes().contentEquals(bytes) })
        }
        // 透明 PNG 保持 Alpha；解码不是简单把扩展名当 MIME。
        val png = media.prepare("test", Uri.fromFile(fixture()))
        val preview = media.loadSendPreview(png.file.uuid, png)!!
        assertTrue(Color.alpha(preview.getPixel(0, 0)) in 120..135)
        preview.recycle()
        files.releasePrepared(png)
    }

    @Test
    fun missingFilesAndConcurrentImageEditsRejectRequestsAndSummaryCommit() = runBlocking {
        val prepared = media.prepare("test", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "old", listOf(MessageImageInput.Prepared(prepared)))
        val snapshot = chat.getSummaryInputSnapshot(sessionId, listOf(saved.key.messageId))
        val reference = media.references(listOf(saved)).getValue(saved.key.messageId).single()
        chat.editUserMessageWithImages(sessionId, saved.key.messageId, "edited", emptyList())
        assertNotNull(runCatching { chat.saveSummary(sessionId, "stale", saved.key.messageId, expectedSnapshot = snapshot) }.exceptionOrNull())
        assertNotNull(runCatching { media.withSendFile(reference) { it.length() } }.exceptionOrNull())
        assertEquals(null, chat.getLatestSummary(sessionId))
    }

    private fun response(protocol: LLMProviderProtocol, stream: Boolean): String = when (protocol) {
        LLMProviderProtocol.OpenAICompatible -> if (stream) "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
            else """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}"""
        LLMProviderProtocol.Gemini -> if (stream) "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}\n\n"
            else """{"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}]}"""
        LLMProviderProtocol.AnthropicMessages -> if (stream) "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
            else """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""
    }
}
