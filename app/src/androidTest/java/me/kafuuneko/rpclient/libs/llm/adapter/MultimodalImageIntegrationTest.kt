package me.kafuuneko.rpclient.libs.llm.adapter

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Random
import java.util.Base64
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.media.CreateImageDocumentContract
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageCoordinator
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSummaryPromptBuilder
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.messageWithBlocks
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.media.ImageSendSettings
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.prompt.FormattedHistoryBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
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
import okio.buffer
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** 预览失败不能解除另一个仍在读取图片的任务对发送按钮的保护。 */
    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun previewFailureMustNotUnlockAnActiveImagePick() = runBlocking {
        val pipe = ParcelFileDescriptor.createPipe()
        val opened = CompletableDeferred<Unit>()
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "image/png"
            override fun query(
                uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?
            ): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(
                uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
            ) = 0
            override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
                opened.complete(Unit)
                return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }
        }
        val resolver = ContentResolver.wrap(provider)
        val providerContext = object : ContextWrapper(context) {
            override fun getContentResolver() = resolver
        }
        val files = FileRepository(providerContext, database)
        val coordinator = MessageImageCoordinator(MessageImageRuntime(providerContext, files), files) {}
        coordinator.choose(editing = false)
        val pick = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.handle(MessageImageAction.Picked(listOf(Uri.parse("content://audit/blocked-image"))))
        }
        try {
            // 管道保持打开但不提供字节，稳定模拟等待云端资源的选择任务。
            withTimeout(5_000) { opened.await() }
            assertTrue(coordinator.state.processing)
            assertTrue(pick.isActive)
            coordinator.handle(MessageImageAction.Preview(listOf("missing-history-image"), 0))
            assertTrue("Preview error incorrectly unlocked sending while pick still runs", coordinator.state.processing)
            assertTrue(pick.isActive)
            assertFalse(requireNotNull(coordinator.state.preview).loading)
            assertNull(coordinator.state.errorResId)
            assertTrue(runCatching { coordinator.draftInputs() }.exceptionOrNull() is IllegalStateException)
            // 保存元数据失败同样不能结束选图；实际选图结束后由自身收尾解锁。
            assertNull(coordinator.beginSave())
            assertTrue(coordinator.state.processing)
        } finally {
            pipe[1].close()
            withTimeout(5_000) { pick.await() }
            coordinator.releaseDrafts()
        }
        assertFalse(coordinator.state.processing)
    }

    @Test
    fun switchingEditorRejectsPickerResultFromPreviousMessage() = runBlocking {
        val coordinator = MessageImageCoordinator(media, files) {}
        coordinator.startEditing(listOf("message-a-image"))
        coordinator.choose(editing = true)
        coordinator.startEditing(listOf("message-b-image"))
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture()))))
        assertEquals(listOf("message-b-image"), coordinator.state.editing)
        assertEquals(R.string.image_edit_ended, coordinator.state.errorResId)
    }

    @Test
    fun switchingEditorCancelsOldPickWithoutClearingNewProcessingState() = runBlocking {
        val coordinator = MessageImageCoordinator(media, files) {}
        val lockDraft = media.prepare("lock", Uri.fromFile(fixture()))
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // 用真实文件仓库的锁暂停准备，稳定覆盖 A 未完成时切换到 B 的窗口。
        val holder = async {
            files.withPreparedFile(lockDraft) {
                locked.complete(Unit)
                release.await()
            }
        }
        try {
            withTimeout(5_000) {
                locked.await()
                coordinator.startEditing(emptyList())
                coordinator.choose(editing = true)
                val oldPick = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture()))))
                }
                assertTrue(coordinator.state.processing)
                coordinator.startEditing(listOf("message-b-image"))
                coordinator.choose(editing = true)
                val newPick = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture()))))
                }
                oldPick.join()
                assertTrue(oldPick.isCancelled)
                assertTrue(coordinator.state.processing)
                assertEquals(listOf("message-b-image"), coordinator.state.editing)
                release.complete(Unit)
                newPick.await()
                assertEquals(2, coordinator.state.editing.size)
                assertFalse(coordinator.state.processing)
            }
        } finally {
            release.complete(Unit)
            holder.await()
            coordinator.releaseDrafts()
            files.releasePrepared(lockDraft)
        }
    }

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
        media = MessageImageRuntime(context, files) { ImageSendSettings() }
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

    /** 新图片只在提交时处理；修改设置不能改变历史字节，删除最后引用后没有发送副本残留。 */
    @Test
    fun committedImagesKeepTheirBytesAcrossSettingsChangesAndDeleteCompletely() = runBlocking {
        val original = fixture(jpeg = true)
        ExifInterface(original).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        var settings = ImageSendSettings(ImageSendMode.Original)
        val runtime = MessageImageRuntime(context, files) { settings }
        val prepared = runtime.prepare("settings", Uri.fromFile(original))
        val saved = runtime.submit(listOf(MessageImageInput.Prepared(prepared))) {
            chat.createUserMessageWithImages(sessionId, "", it.filterIsInstance<MessageImageInput.Prepared>())
        }
        val reference = runtime.references(listOf(saved)).getValue(saved.key.messageId).single()
        assertEquals(160, reference.width)
        assertEquals(320, reference.height)
        runtime.withSendFile(reference) { assertTrue(original.readBytes().contentEquals(it.readBytes())) }
        // 新设置只影响下一次提交，不改变已保存图片的方向、尺寸和字节。
        settings = ImageSendSettings(ImageSendMode.Custom, 64, 128, 256)
        assertEquals(reference, runtime.references(listOf(saved)).getValue(saved.key.messageId).single())
        val next = runtime.prepare("settings", Uri.fromFile(original))
        val compressed = runtime.submit(listOf(MessageImageInput.Prepared(next))) {
            chat.createUserMessageWithImages(sessionId, "", it.filterIsInstance<MessageImageInput.Prepared>())
        }
        val custom = runtime.references(listOf(compressed)).getValue(compressed.key.messageId).single()
        assertEquals(128, custom.width)
        assertEquals(256, custom.height)
        val stored = requireNotNull(files.getFile(custom.uuid))
        assertFalse(original.readBytes().contentEquals(stored.readBytes()))
        val destination = File(directory, "saved.jpg")
        runtime.save(custom.uuid, Uri.fromFile(destination))
        assertTrue(stored.readBytes().contentEquals(destination.readBytes()))
        assertTrue(File(directory, "repository/staging").listFiles().orEmpty().isEmpty())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
        chat.deleteMessage(compressed.key.messageId)
        assertFalse(stored.exists())
        chat.deleteMessage(saved.key.messageId)
        assertTrue(File(directory, "repository").listFiles().orEmpty().none { it.isFile })
    }

    /** 原图发送限制在提交时检查；失败保留草稿，切换压缩后可重试且不保留源图。 */
    @Test
    fun compressionFailureKeepsDraftAndSuccessfulRetryStoresOnlyFinalBytes() = runBlocking {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val random = Random(42)
        bitmap.setPixels(IntArray(1024 * 1024) { random.nextInt() }, 0, 1024, 0, 0, 1024, 1024)
        val original = File(directory, "noise.png")
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertTrue(original.length() > 2L * 1024 * 1024)
        var settings = ImageSendSettings(ImageSendMode.Original)
        val runtime = MessageImageRuntime(context, files) { settings }
        val coordinator = MessageImageCoordinator(runtime, files) {}
        coordinator.choose(editing = false)
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        val source = coordinator.draftInputs().single().value
        assertEquals(1, coordinator.state.draft.size)
        // 选图不预生成发送文件；失败不能创建空消息或丢失用户草稿。
        val failure = runCatching {
            coordinator.submit { chat.createUserMessageWithImages(sessionId, "", it.filterIsInstance<MessageImageInput.Prepared>()) }
        }.exceptionOrNull() as ImageRequestException
        assertEquals(ImageRequestFailure.OriginalTooLarge, failure.failure)
        assertEquals(listOf(source.file.uuid), coordinator.state.draft)
        assertFalse(coordinator.state.processing)
        assertTrue(chat.getMessagesBySessionId(sessionId).isEmpty())
        settings = ImageSendSettings(ImageSendMode.Custom, 64, 1024, 1024)
        val saved = coordinator.submit {
            chat.createUserMessageWithImages(sessionId, "", it.filterIsInstance<MessageImageInput.Prepared>())
        }
        val reference = runtime.references(listOf(saved)).getValue(saved.key.messageId).single()
        assertTrue(reference.byteCount <= 64 * 1024L)
        assertTrue(reference.width < 1024 && reference.height < 1024)
        runtime.withSendFile(reference) {
            val decoded = BitmapFactory.decodeFile(it.absolutePath)
            assertTrue(decoded.hasAlpha())
            decoded.recycle()
        }
        assertTrue(coordinator.state.draft.isEmpty())
        assertNull(files.getFileEntity(source.file.uuid))
        assertFalse(File(directory, "repository/${source.file.hash}").exists())
        assertTrue(File(directory, "repository/staging").listFiles().orEmpty().isEmpty())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }

    /** 单次提交冻结设置；历史请求不读取设置，批量处理失败释放已经完成的处理结果。 */
    @Test
    fun submissionFreezesSettingsAndFailedBatchLeavesOnlySourceDrafts() = runBlocking {
        val first = media.prepare("batch", Uri.fromFile(fixture(jpeg = true)))
        val second = media.prepare("batch", Uri.fromFile(fixture()))
        var reads = 0
        val runtime = MessageImageRuntime(context, files) {
            reads++
            ImageSendSettings(ImageSendMode.Custom, 64, if (reads == 1) 128 else 256, 128)
        }
        val inputs = listOf(first, second).map(MessageImageInput::Prepared)
        val saved = runtime.submit(inputs) {
            chat.createUserMessageWithImages(sessionId, "", it.filterIsInstance<MessageImageInput.Prepared>())
        }
        val refs = runtime.references(listOf(saved)).getValue(saved.key.messageId)
        assertEquals(1, reads)
        assertTrue(refs.all { it.width <= 128 && it.height <= 128 })
        // 第二张在处理时失败，第一张的新结果也必须清理，源草稿仍可重试。
        val good = media.prepare("failure", Uri.fromFile(fixture()))
        val bad = files.prepareStream("failure", ByteArrayInputStream("invalid".toByteArray()), "image/png")
        var committed = false
        assertTrue(runCatching {
            runtime.submit(listOf(good, bad).map(MessageImageInput::Prepared)) { committed = true }
        }.isFailure)
        assertFalse(committed)
        val staging = File(directory, "repository/staging").listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(setOf(good.handle, "${good.handle}.meta", bad.handle, "${bad.handle}.meta"), staging)
        files.releasePrepared(good)
        files.releasePrepared(bad)
    }

    /** 在写入最终文件时取消，半成品和索引不能残留，也不能影响仍可重试的源草稿。 */
    @Test
    fun cancelledFileGenerationRemovesPartialOutputAndKeepsSourceDraft() = runBlocking {
        val source = media.prepare("cancel", Uri.fromFile(fixture()))
        val started = CompletableDeferred<Unit>()
        val job = async {
            files.prepareGenerated("cancel") { target ->
                target.writeText("partial image")
                started.complete(Unit)
                awaitCancellation()
            }
        }
        withTimeout(5_000) { started.await() }
        job.cancelAndJoin()
        assertEquals(setOf(source.handle, "${source.handle}.meta"),
            File(directory, "repository/staging").listFiles().orEmpty().map { it.name }.toSet())
        assertNull(files.getFileEntity(source.file.uuid))
        files.releasePrepared(source)
        assertTrue(File(directory, "repository/staging").listFiles().orEmpty().isEmpty())
    }

    /** 编辑只处理新图；事务失败或取消编辑保留原消息，成功才回收移除项。 */
    @Test
    fun editingKeepsExistingBytesAndRollsBackBeforeReleasingRemovedImages() = runBlocking {
        var settings = ImageSendSettings(ImageSendMode.Original)
        val runtime = MessageImageRuntime(context, files) { settings }
        val coordinator = MessageImageCoordinator(runtime, files) {}
        coordinator.choose(editing = false)
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture(true)), Uri.fromFile(fixture()))))
        val saved = coordinator.submit {
            chat.createUserMessageWithImages(sessionId, "old", it.filterIsInstance<MessageImageInput.Prepared>())
        }
        val ids = saved.images.map { it.image.imageUuid }
        val removed = requireNotNull(files.getFile(ids[0]))
        val retained = requireNotNull(files.getFile(ids[1])).readBytes()
        settings = ImageSendSettings(ImageSendMode.Custom, 64, 128, 128)
        coordinator.startEditing(ids)
        coordinator.handle(MessageImageAction.Remove(ids[0], editing = true))
        coordinator.choose(editing = true)
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture(true)))))
        val added = coordinator.editingInputs().filterIsInstance<MessageImageInput.Prepared>().single().value
        // 让事务在附件替换之后失败，验证正文、关系和旧文件一起回滚。
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_edit BEFORE UPDATE ON chat_messages BEGIN SELECT RAISE(ABORT, 'test'); END")
        assertTrue(runCatching {
            coordinator.submit(editing = true) { chat.editMessageWithImages(sessionId, saved.key.messageId, "new", it) }
        }.isFailure)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_edit")
        assertEquals("old", chat.getMessageById(saved.key.messageId)!!.content)
        assertEquals(ids, chat.getMessagesWithImages(listOf(saved.key.messageId)).single().images.map { it.image.imageUuid })
        assertTrue(removed.exists())
        assertEquals(setOf(added.handle, "${added.handle}.meta"),
            File(directory, "repository/staging").listFiles().orEmpty().map { it.name }.toSet())
        val edited = coordinator.submit(editing = true) {
            chat.editMessageWithImages(sessionId, saved.key.messageId, "new", it)
        }
        assertFalse(removed.exists())
        assertEquals(ids[1], edited.images.first().image.imageUuid)
        assertTrue(retained.contentEquals(requireNotNull(files.getFile(ids[1])).readBytes()))
        assertTrue(runtime.references(listOf(edited)).getValue(saved.key.messageId).last().width <= 128)
        assertTrue(File(directory, "repository/staging").listFiles().orEmpty().isEmpty())
        coordinator.startEditing(edited.images.map { it.image.imageUuid })
        coordinator.handle(MessageImageAction.Remove(ids[1], editing = true))
        coordinator.choose(editing = true)
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture()))))
        coordinator.cancelEditing()
        assertTrue(retained.contentEquals(requireNotNull(files.getFile(ids[1])).readBytes()))
        assertEquals(edited, chat.getMessagesWithImages(listOf(saved.key.messageId)).single())
        assertTrue(File(directory, "repository/staging").listFiles().orEmpty().isEmpty())
        chat.deleteSession(sessionId)
        assertTrue(File(directory, "repository").listFiles().orEmpty().none { it.isFile })
    }

    /** 保存文档类型必须来自真实字节，且草稿和历史图片导出均保留原图。 */
    @Test
    fun originalExportUsesActualFormatForDraftAndHistory() = runBlocking {
        for (jpeg in listOf(false, true)) {
            val original = fixture(jpeg)
            // 故意声明错误类型，确保导出不信任选择器或文件索引的 MIME。
            val prepared = files.prepareStream("export", ByteArrayInputStream(original.readBytes()), "image/webp")
            val metadata = media.exportMetadata(prepared.file.uuid, prepared)
            val expectedMime = if (jpeg) "image/jpeg" else "image/png"
            val expectedName = if (jpeg) "image.jpg" else "image.png"
            assertEquals(expectedMime, metadata.mimeType)
            assertEquals(expectedName, metadata.fileName)
            val intent = CreateImageDocumentContract().createIntent(context, metadata)
            assertEquals(expectedMime, intent.type)
            assertEquals(expectedName, intent.getStringExtra(Intent.EXTRA_TITLE))
            val draftDestination = File(directory, "draft-$expectedName")
            media.save(prepared.file.uuid, Uri.fromFile(draftDestination), prepared)
            assertTrue(original.readBytes().contentEquals(draftDestination.readBytes()))
            // 持久化后使用文件租约重新识别并保存，不能误导出 JPEG/PNG 发送缓存。
            chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(prepared)))
            assertEquals(metadata, media.exportMetadata(prepared.file.uuid))
            val historyDestination = File(directory, "history-$expectedName")
            media.save(prepared.file.uuid, Uri.fromFile(historyDestination))
            assertTrue(original.readBytes().contentEquals(historyDestination.readBytes()))
        }
    }

    @Test
    fun inMemoryDraftSavesOriginalAndRejectsResultsAfterEditingEnds() = runBlocking {
        val coordinator = MessageImageCoordinator(media, files) {}
        val original = fixture()
        coordinator.choose(editing = false)
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        val uuid = coordinator.state.draft.single()
        // 新 ViewModel 不接纳原页面尚未交付的选择结果，也不恢复旧暂存。
        val fresh = MessageImageCoordinator(media, files) {}
        fresh.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        assertTrue(fresh.state.draft.isEmpty())
        assertEquals(R.string.image_prepare_failed, fresh.state.errorResId)
        assertFalse(fresh.state.processing)
        // 成功或取消的回调都会消费归属，重复结果不能继续往草稿追加附件。
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        assertEquals(listOf(uuid), coordinator.state.draft)
        coordinator.choose(editing = false)
        coordinator.handle(MessageImageAction.Picked(emptyList()))
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        assertEquals(listOf(uuid), coordinator.state.draft)
        coordinator.handle(MessageImageAction.Preview(listOf(uuid), 0))
        val metadata = requireNotNull(coordinator.beginSave())
        assertEquals("image/png", metadata.mimeType)
        assertEquals("image.png", metadata.fileName)
        // 系统保存期间切换预览，返回的 URI 仍必须接收原先选中的原图。
        coordinator.handle(MessageImageAction.Preview(listOf("another-missing-image"), 0))
        val destination = File(directory, "saved-original.png")
        coordinator.handle(MessageImageAction.SaveResult(Uri.fromFile(destination)))
        assertTrue(original.readBytes().contentEquals(destination.readBytes()))
        // 同一页面取消编辑后，迟到的选择结果不应污染未发送的草稿。
        coordinator.startEditing(emptyList())
        coordinator.choose(true)
        coordinator.cancelEditing()
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(original))))
        assertEquals(listOf(uuid), coordinator.state.draft)
        assertEquals(R.string.image_edit_ended, coordinator.state.errorResId)
        coordinator.releaseDrafts()
        assertTrue(File(context.getDir("repository", Context.MODE_PRIVATE), "staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun visibleDraftAndEditingThumbnailsSurviveHistoryEvictionAndReferenceRelease() = runBlocking {
        val coordinator = MessageImageCoordinator(media, files) {}
        coordinator.choose(editing = false)
        coordinator.handle(MessageImageAction.Picked(listOf(Uri.fromFile(fixture()))))
        val draft = coordinator.state.draft.single()
        val prepared = media.prepare("editing", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "edit", listOf(MessageImageInput.Prepared(prepared)))
        val editing = saved.images.single().image.imageUuid
        coordinator.startEditing(listOf(editing))
        coordinator.handle(MessageImageAction.Load(editing))
        // 缺图也是已完成的缓存项，滚动超过历史目标时不应淘汰任何受保护图片。
        repeat(80) { coordinator.handle(MessageImageAction.Load("missing-history-$it")) }
        assertNotNull(coordinator.state.thumbnails[draft])
        assertNotNull(coordinator.state.thumbnails[editing])
        coordinator.handle(MessageImageAction.RegisterDisplay(editing))
        coordinator.handle(MessageImageAction.RegisterDisplay(editing))
        coordinator.cancelEditing()
        coordinator.handle(MessageImageAction.ReleaseDisplay(editing))
        repeat(80) { coordinator.handle(MessageImageAction.Load("more-history-$it")) }
        assertNotNull(coordinator.state.thumbnails[editing])
        // 最后一个显示引用释放后恢复回收，原图所有权始终属于历史消息。
        coordinator.handle(MessageImageAction.ReleaseDisplay(editing))
        repeat(80) { coordinator.handle(MessageImageAction.Load("last-history-$it")) }
        assertFalse(coordinator.state.thumbnails.containsKey(editing))
        assertTrue(coordinator.state.thumbnails.size <= 64)
        assertNotNull(files.getFileEntity(editing))
        coordinator.handle(MessageImageAction.RegisterDisplay(editing))
        assertNotNull(coordinator.state.thumbnails[editing])
        coordinator.handle(MessageImageAction.ReleaseDisplay(editing))
        coordinator.releaseDrafts()
    }

    @Test
    fun candidatePreparationKeepsReadyImagesAndClassifiesMissingAndInvalidAttachments() = runBlocking {
        val ready = media.prepare("ready", Uri.fromFile(fixture()))
        val invalidFile = File(directory, "damaged").apply { writeText("not an image") }
        val invalid = files.prepareFile("invalid", Uri.fromFile(invalidFile))
        val saved = chat.createUserMessageWithImages(sessionId, "", listOf(
            MessageImageInput.Prepared(ready), MessageImageInput.Prepared(invalid)))
        val snapshot = saved.copy(images = saved.images + saved.images.first().copy(
            image = saved.images.first().image.copy(position = 2), file = null))
        // 每个附件的失败独立收集，未知程序错误不会在 Runtime 被 blanket catch 吞掉。
        val candidates = media.prepareCandidates(listOf(snapshot))
        assertEquals(listOf(ready.file.uuid), candidates.references.getValue(saved.key.messageId).map { it.uuid })
        val failures = candidates.unavailable.getValue(saved.key.messageId)
        assertEquals(listOf(1, 2), failures.map { it.position })
        assertEquals(listOf(ImageRequestFailure.InvalidImage, ImageRequestFailure.Missing), failures.map { it.failure })
        assertTrue(runCatching { media.references(listOf(snapshot)) }.exceptionOrNull() is ImageRequestException)
    }

    @Test
    fun cancelledCandidatePreparationDoesNotTurnCancellationIntoMissingImage() = runBlocking {
        val prepared = media.prepare("cancel", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(prepared)))
        val attempt = async(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            media.prepareCandidates(listOf(saved))
        }
        assertTrue(runCatching { attempt.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, media.references(listOf(saved)).getValue(saved.key.messageId).size)
    }

    @Test
    fun bothSummaryBuildersRejectOnlyMissingImagesWithinTheSelectedPrefix() = runBlocking {
        val session = chat.getSessionById(sessionId)!!
        val character = database.getCharacterDao().getCharacterById(session.characterId)!!
        val provider = LLMProvider(name = "test", providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible, baseUrl = "https://example.invalid",
            model = "unknown", contextTokens = 100_000)
        val messages = (1L..3L).map { ChatMessage(it, sessionId, it, ChatMessage.Source.User, "text") }
        val unavailable = UnavailablePromptImage(3, "missing", 0, ImageRequestFailure.Missing)
        val history = FormattedHistoryBuilder()
        val single = SummaryPromptBuilder(PromptMacroResolver(history), history, PromptRequestFinalizer())
        val baseline = single.buildWithSelection("user", "", character, session, "", messages, provider)
        val outside = single.buildWithSelection("user", "", character, session, "", messages, provider,
            unavailableImages = mapOf(3L to listOf(unavailable)))
        assertEquals(baseline, outside)
        assertTrue(runCatching { single.buildWithSelection("user", "", character, session, "", messages, provider,
            unavailableImages = mapOf(1L to listOf(unavailable.copy(messageId = 1)))) }.exceptionOrNull() is ImageRequestException)
        // 群聊采用自己的 Builder，选择边界和资源错误仍应遵循相同规则。
        val group = GroupChatSummaryPromptBuilder()
        val groupSession = GroupChatSession(title = "group", createTime = 1, latestTime = 1, userName = "user", userDescription = "")
        val groupMessages = messages.map { GroupChatMessage(it.id, 1, it.createTime,
            GroupChatMessage.Source.User, it.content, speakerNameSnapshot = "user") }
        val groupBaseline = group.buildWithSelection(groupSession, listOf("character"), "", groupMessages, provider)
        val groupOutside = group.buildWithSelection(groupSession, listOf("character"), "", groupMessages, provider,
            unavailableImages = mapOf(3L to listOf(unavailable)))
        assertEquals(groupBaseline, groupOutside)
        assertTrue(runCatching { group.buildWithSelection(groupSession, listOf("character"), "", groupMessages, provider,
            unavailableImages = mapOf(1L to listOf(unavailable.copy(messageId = 1)))) }.exceptionOrNull() is ImageRequestException)
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
        // 各协议无论在编码前还是编码后拒绝非法地址，都不能遗留请求文件。
        val request = LLMGenerationRequest(listOf(messageWithBlocks(LLMMessageRole.User, listOf(LLMContentBlock.Image(reference)))))
        for (protocol in LLMProviderProtocol.entries) {
            val invalidProvider = provider.copy(protocol = protocol, baseUrl = "invalid-address")
            val invalidClient: LLMClient = when (protocol) {
                LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleLLMClient(OkHttpClient(), logs, invalidProvider, media)
                LLMProviderProtocol.OpenAIResponses -> OpenAIResponsesLLMClient(OkHttpClient(), logs, invalidProvider, media)
                LLMProviderProtocol.Gemini -> GeminiLLMClient(OkHttpClient(), logs, invalidProvider, media)
                LLMProviderProtocol.AnthropicMessages -> AnthropicMessagesLLMClient(OkHttpClient(), logs, invalidProvider, media)
            }
            assertTrue(runCatching { invalidClient.generate(request) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(File(context.cacheDir, "image-requests").listFiles().orEmpty().isEmpty())
            assertTrue(runCatching { invalidClient.streamGenerate(request).toList() }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(File(context.cacheDir, "image-requests").listFiles().orEmpty().isEmpty())
        }
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
        // 穷举用户与角色、各协议、两种传输和三类内容，检查实际请求字节及角色。
        for (protocol in LLMProviderProtocol.entries) for (stream in listOf(false, true))
        for (role in listOf(LLMMessageRole.User, LLMMessageRole.Assistant)) for (count in 0..2) {
            var payload = ""
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                payload = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(response(protocol, stream).toResponseBody("application/json".toMediaType())).build()
            }.build()
            val provider = LLMProviderConfig("test", LLMProviderType.Custom, protocol, "https://example.invalid", model = "test")
            val client: LLMClient = when (protocol) {
                LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleLLMClient(http, logs, provider, media)
                LLMProviderProtocol.OpenAIResponses -> OpenAIResponsesLLMClient(http, logs, provider, media)
                LLMProviderProtocol.Gemini -> GeminiLLMClient(http, logs, provider, media)
                LLMProviderProtocol.AnthropicMessages -> AnthropicMessagesLLMClient(http, logs, provider, media)
            }
            val blocks = (0 until count).flatMap { index ->
                listOf(LLMContentBlock.Image(reference)) + if (count == 2) listOf(LLMContentBlock.Text("after-$index")) else emptyList()
            }
            val message = if (count == 0) LLMMessage(role, "text") else messageWithBlocks(role, blocks)
            val request = LLMGenerationRequest(listOf(message), isPromptFinalized = true)
            if (stream) client.streamGenerate(request).toList() else assertEquals("ok", client.generate(request).content)
            val json = JSONObject(payload)
            val messagesKey = when (protocol) {
                LLMProviderProtocol.Gemini -> "contents"
                LLMProviderProtocol.OpenAIResponses -> "input"
                else -> "messages"
            }
            val messageJson = json.getJSONArray(messagesKey).getJSONObject(0)
            val expectedRole = if (role == LLMMessageRole.User) "user"
                else if (protocol == LLMProviderProtocol.Gemini) "model" else "assistant"
            assertEquals(expectedRole, messageJson.getString("role"))
            val contentKey = if (protocol == LLMProviderProtocol.Gemini) "parts" else "content"
            if (count > 0) {
                val content = messageJson.getJSONArray(contentKey)
                assertEquals(if (count == 2) 4 else 1, content.length())
                val first = content.getJSONObject(0)
                val encoded = when (protocol) {
                    LLMProviderProtocol.OpenAICompatible -> first.getJSONObject("image_url").getString("url").substringAfter("base64,")
                    LLMProviderProtocol.OpenAIResponses -> first.getString("image_url").substringAfter("base64,")
                    LLMProviderProtocol.Gemini -> first.getJSONObject("inlineData").getString("data")
                    LLMProviderProtocol.AnthropicMessages -> first.getJSONObject("source").getString("data")
                }
                assertEquals(reference.byteCount, Base64.getDecoder().decode(encoded).size.toLong())
                val logId = logs.getLogOverviews(10, 1, 0).single().id
                assertFalse(logs.getRequestJson(logId)!!.contains(encoded))
                assertTrue(logs.getRequestJson(logId)!!.contains("cannot be replayed directly"))
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
        val preview = media.load(png.file.uuid, png)!!
        assertTrue(Color.alpha(preview.getPixel(0, 0)) in 120..135)
        preview.recycle()
        files.releasePrepared(png)
    }

    @Test
    fun nativeResponsesCloseIdleSocketsAndReleaseImageFilesOnCancelAndCompletion() = runBlocking {
        val prepared = media.prepare("responses", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(prepared)))
        val reference = media.references(listOf(saved)).getValue(saved.key.messageId).single()
        val request = LLMGenerationRequest(listOf(messageWithBlocks(LLMMessageRole.User, listOf(LLMContentBlock.Image(reference)))))
        // 真正保持 HTTP 连接开启，分别检查用户停止和收到终态后的主动关闭。
        for (cancel in listOf(true, false)) {
            val body = if (cancel) "" else response(LLMProviderProtocol.OpenAIResponses, true)
            IdleResponsesServer(body).use { server ->
                val provider = LLMProviderConfig("test", LLMProviderType.ChatGPT, LLMProviderProtocol.OpenAIResponses,
                    "http://127.0.0.1:${server.port}/v1", model = "test")
                val client = LLMClientFactory(OkHttpClient(), logs, mImageRuntime = media).create(provider)
                val connected = CompletableDeferred<Unit>()
                val events = mutableListOf<LLMStreamEvent>()
                val job = async {
                    client.streamGenerate(request).collect { event ->
                        events += event
                        if (event == LLMStreamEvent.Connected) connected.complete(Unit)
                    }
                }
                try {
                    withTimeout(5_000) {
                        connected.await()
                        if (cancel) job.cancelAndJoin() else job.await()
                        server.closed.await()
                    }
                    assertEquals(!cancel, events.any { it is LLMStreamEvent.Finished })
                    assertTrue(File(context.cacheDir, "image-requests").listFiles().orEmpty().isEmpty())
                    assertEquals(1, media.references(listOf(saved)).getValue(saved.key.messageId).size)
                } finally {
                    job.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun missingFilesAndConcurrentImageEditsRejectRequestsAndSummaryCommit() = runBlocking {
        val prepared = media.prepare("test", Uri.fromFile(fixture()))
        val saved = chat.createUserMessageWithImages(sessionId, "old", listOf(MessageImageInput.Prepared(prepared)))
        val snapshot = chat.getSummaryInputSnapshot(sessionId, listOf(saved.key.messageId))
        val reference = media.references(listOf(saved)).getValue(saved.key.messageId).single()
        chat.editMessageWithImages(sessionId, saved.key.messageId, "edited", emptyList())
        assertNotNull(runCatching { chat.saveSummary(sessionId, "stale", saved.key.messageId, expectedSnapshot = snapshot) }.exceptionOrNull())
        assertNotNull(runCatching { media.withSendFile(reference) { it.length() } }.exceptionOrNull())
        assertEquals(null, chat.getLatestSummary(sessionId))
    }

    private fun response(protocol: LLMProviderProtocol, stream: Boolean): String = when (protocol) {
        LLMProviderProtocol.OpenAIResponses -> {
            val response = """{"id":"resp_test","status":"completed","model":"test","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"ok"}]}]}"""
            if (stream) "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":$response}\n\n" else response
        }
        LLMProviderProtocol.OpenAICompatible -> if (stream) "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
            else """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}"""
        LLMProviderProtocol.Gemini -> if (stream) "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}\n\n"
            else """{"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}]}"""
        LLMProviderProtocol.AnthropicMessages -> if (stream) "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
            else """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""
    }

    /** 仅监听设备回环地址，响应发送后等待客户端关闭，验证传输资源的真实释放。 */
    private class IdleResponsesServer(body: String) : AutoCloseable {
        private val mSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = mSocket.localPort
        val closed = CompletableDeferred<Unit>()
        private val mWorker = thread(isDaemon = true, name = "responses-http-fixture") {
            try {
                mSocket.accept().use { client ->
                    client.soTimeout = 8_000
                    val input = client.getInputStream().source().buffer()
                    check(input.readUtf8LineStrict() == "POST /v1/responses HTTP/1.1")
                    var length = 0L
                    while (true) {
                        val header = input.readUtf8LineStrict()
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toLong()
                    }
                    // 消费包含真实 Base64 的请求体，才发出连接成功与最终事件。
                    val payload = JSONObject(input.readUtf8(length))
                    check(!payload.getBoolean("store") && payload.getBoolean("stream"))
                    client.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n$body".toByteArray())
                        flush()
                    }
                    check(input.exhausted())
                    closed.complete(Unit)
                }
            } catch (error: Throwable) {
                closed.completeExceptionally(error)
            }
        }

        /** 测试失败时同样关闭监听端口，后台读操作另有有界超时。 */
        override fun close() {
            mSocket.close()
            mWorker.join(1_000)
        }
    }
}
