package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.chat.ChatArchive
import me.kafuuneko.rpclient.libs.chat.ChatArchiveCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchiveImage
import me.kafuuneko.rpclient.libs.chat.ChatArchiveMessage
import me.kafuuneko.rpclient.libs.chat.ChatArchiveMessageRole
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.MessageImageEntity
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 使用完整 ZIP 与真实数据库替换验证可编辑备份的数据恢复边界。
 * - 所有数据库、图片与偏好均属于独立临时目录。
 * - 覆盖无编辑往返、来源歧义、图片权威来源及后台检索重建。
 */
@RunWith(AndroidJUnit4::class)
class PortableBackupRecoveryTest {
    private lateinit var mRoot: File
    private lateinit var mContext: Context
    private lateinit var mPrimary: AppDatabase
    private lateinit var mLogs: RequestLogDatabase
    private val mIndexes = mutableListOf<ContentSearchRepository>()

    /** 用包装 Context 隔离文件与数据库，不接触应用的实际业务目录。 */
    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        mRoot = File(base.cacheDir, "backup-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        mContext = object : ContextWrapper(base) {
            override fun getDataDir(): File = mRoot
            override fun getFilesDir(): File = File(mRoot, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(mRoot, "cache").apply { mkdirs() }
            override fun getDir(name: String, mode: Int): File =
                File(mRoot, "app_$name").apply { mkdirs() }

            override fun getDatabasePath(name: String): File =
                (if (File(name).isAbsolute) File(name) else File(mRoot, "databases/$name"))
                    .also { file -> file.parentFile?.mkdirs() }
        }
        // 空偏好目录也参与完整替换，测试不会借用宿主的真实设置。
        File(mRoot, "shared_prefs").mkdirs()
        openDatabases()
    }

    /** 先停止派生任务，再关闭数据库并删除本项创建的临时资源。 */
    @After
    fun tearDown() = runBlocking {
        mIndexes.forEach { index -> index.stop() }
        mPrimary.close()
        mLogs.close()
        mRoot.deleteRecursively()
        Unit
    }

    /** 完整往返保留分类及空值，角色内嵌与独立世界书的历史字段都不能复活。 */
    @Test
    fun worldbookCategoriesAndClearedSettingsSurviveFullRestore() = runBlocking {
        mPrimary.getLorebookDao().insertOrReplace(Lorebook(id = 7L, name = "Book"))
        mPrimary.getLorebookEntryDao().insertOrReplace(
            entry().copy(
                category = "[\"location\",\"setting\"]",
                rawJson = """{"sticky":9,"scanDepth":7,"caseSensitive":true,"custom":"kept"}""",
                extensionsJson = """{"sticky":9,"scan_depth":7,"case_sensitive":true}"""
            )
        )
        mPrimary.getCharacterDao().insertOrReplace(character().copy(characterLorebookId = 7L))
        val (archive, unpacked) = editableArchive()
        restore(archive, unpacked)

        // 断言重新打开后的实体，覆盖编解码、暂存重建与实际替换。
        val restored = mPrimary.getLorebookEntryDao().getAllEntries().single()
        assertEquals("[\"location\",\"setting\"]", restored.category)
        assertEquals(null, restored.sticky)
        assertEquals(null, restored.scanDepth)
        assertEquals(null, restored.caseSensitive)
        assertEquals("kept", JSONObject(restored.rawJson).getString("custom"))
        assertEquals(7L, mPrimary.getCharacterDao().getCharacterById(5L)!!.characterLorebookId)
    }

    /** 删除明确绑定的角色后，聊天必须随之移除，不能回退到唯一同名角色。 */
    @Test
    fun deletedCharacterDoesNotRebindItsChatToNamesake() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        mPrimary.getCharacterDao()
            .insertOrReplace(character(id = 6L).copy(description = "Other character"))
        seedChat()
        val (archive, unpacked) = editableArchive()
        File(unpacked, "characters").listFiles()!!.single { file ->
            JSONObject(file.readText()).getJSONObject(BackupV3.SIDECAR).getLong("id") == 5L
        }.delete()
        restore(archive, unpacked)

        assertEquals(1L, scalar("SELECT COUNT(*) FROM character"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM chat_sessions"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM chat_messages"))
    }

    /** 重复来源 ID 同样不能按名称挑选一个角色，但无来源 ID 的新增聊天仍可正常导入。 */
    @Test
    fun ambiguousSourceIsSkippedWhileNewChatUsesUniqueMatch() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        mPrimary.getCharacterDao().insertOrReplace(character(id = 6L).copy(name = "Bob"))
        seedChat()
        val (archive, unpacked) = editableArchive()
        val bob = File(unpacked, "characters").listFiles()!!.single { file ->
            JSONObject(file.readText()).getJSONObject("data").getString("name") == "Bob"
        }
        val document = JSONObject(bob.readText())
        document.getJSONObject(BackupV3.SIDECAR).put("id", 5L)
        bob.writeText(document.toString())
        writeChat(unpacked, "new.jsonl", "New chat")
        restore(archive, unpacked)

        // 原聊天的来源有两个候选；新增聊天不带本地主键，可按唯一名称匹配 Alice。
        assertEquals(1L, scalar("SELECT COUNT(*) FROM chat_sessions"))
        val title = mPrimary.openHelper.readableDatabase.query("SELECT title FROM chat_sessions")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        assertEquals("New chat", title)
    }

    /** 编辑、删除与复制 JSONL 图片后，关系、原始字节和独立 UUID 都以文档为准。 */
    @Test
    fun editedAndCopiedChatImagesReplaceOldRelationsWithoutDuplicatingStableIndexes() =
        runBlocking {
            mPrimary.getCharacterDao().insertOrReplace(character())
            seedChat()
            val original = png(0xff0000ff.toInt())
            val firstUuid = attach(MessageType.Single, 20L, original)
            mPrimary.getChatMessageDao().insertOrReplace(message(id = 21L, content = "Second"))
            attach(MessageType.Single, 21L, original)
            val (archive, unpacked) = editableArchive()
            val file = File(unpacked, "chats").listFiles()!!.single()
            val lines = file.readLines().map(::JSONObject)
            val replacement = png(0xffff0000.toInt())
            imageArray(lines[1]).put(0, embeddedImage(replacement).put("mime_type", "image/jpeg"))
            lines[2].getJSONObject("extra").getJSONObject("rpclient").put("images", JSONArray())
            writeLines(file, lines)
            val copy =
                lines.map { line -> JSONObject(line.toString()).apply { remove(BackupV3.SIDECAR) } }
            writeLines(File(unpacked, "chats/copied.jsonl"), copy)
            restore(archive, unpacked)

            // 两个聊天各保留一张修改后的图片，旧消息中的图片删除和复制都实际生效。
            assertEquals(2L, scalar("SELECT COUNT(*) FROM chat_sessions"))
            assertEquals(2L, scalar("SELECT COUNT(*) FROM message_images"))
            assertEquals(2L, scalar("SELECT COUNT(DISTINCT imageUuid) FROM message_images"))
            assertTrue(
                mPrimary.getMessageImageDao().getByMessage(MessageType.Single, 21L).isEmpty()
            )
            val restored =
                mPrimary.getMessageImageDao().getByMessage(MessageType.Single, 20L).single()
            assertFalse(firstUuid == restored.imageUuid)
            val files = FileRepository(mContext, mPrimary)
            assertEquals("image/png", files.getFileEntity(restored.imageUuid)!!.mimeType)
            assertArrayEquals(replacement, files.getFile(restored.imageUuid)!!.readBytes())
            val imageUuids = imageUuids()

            // 再次无编辑往返时复用稳定文件索引，不因恢复次数增加而重复签发 UUID。
            val (secondArchive, secondUnpacked) = editableArchive()
            restore(secondArchive, secondUnpacked)
            assertEquals(imageUuids, imageUuids())
        }

    /** 新增内嵌图片与后续哈希引用共享原始字节，每个关系仍拥有独立文件索引。 */
    @Test
    fun addedChatResolvesRepeatedEmbeddedImageAndHashReferences() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        val (archive, unpacked) = editableArchive()
        val bytes = png(0xff00ff00.toInt())
        val image = ChatArchiveImage(
            mimeType = "image/png",
            data = Base64.getEncoder().encodeToString(bytes)
        )
        val chat = chatArchive(
            "Added images", listOf(
                ChatArchiveMessage(2L, ChatArchiveMessageRole.User, "", listOf(image, image)),
                ChatArchiveMessage(3L, ChatArchiveMessageRole.Narrator, "Narrator", listOf(image))
            )
        )
        val file = File(unpacked, "chats/added.jsonl").apply { parentFile!!.mkdirs() }
        file.writeText(ChatArchiveCodec(Gson()).encode(chat))
        restore(archive, unpacked)

        assertEquals(3L, scalar("SELECT COUNT(*) FROM message_images"))
        assertEquals(3L, scalar("SELECT COUNT(DISTINCT imageUuid) FROM message_images"))
        assertEquals(1L, scalar("SELECT COUNT(DISTINCT hash) FROM files"))
        val index = mPrimary.openHelper.readableDatabase.query("SELECT uuid FROM files LIMIT 1")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        assertArrayEquals(bytes, FileRepository(mContext, mPrimary).getFile(index)!!.readBytes())
    }

    /** 损坏内嵌数据和摘要不匹配必须在覆盖之前失败，当前数据库及附件始终可读。 */
    @Test
    fun invalidEmbeddedImagesLeaveCurrentDataAndFilesReadable() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        seedChat()
        val bytes = png(0xff0000ff.toInt())
        val uuid = attach(MessageType.Single, 20L, bytes)
        val (archive, unpacked) = editableArchive()
        val file = File(unpacked, "chats").listFiles()!!.single()
        val original = file.readText()
        val invalidImages = listOf(
            embeddedImage(bytes).put("data", "invalid-base64"),
            embeddedImage(bytes).put("hash", "0".repeat(64)),
            embeddedImage("not an image".toByteArray())
        )
        // 各种失败都发生在暂存阶段，不得关闭当前连接或替换已经提交的资源。
        invalidImages.forEach { image ->
            val lines =
                original.lineSequence().filter { line -> line.isNotBlank() }.map(::JSONObject)
                    .toList()
            imageArray(lines[1]).put(0, image)
            writeLines(file, lines)
            pack(unpacked, archive)
            assertTrue(runCatching { repository().restoreArchive(Uri.fromFile(archive)) }.isFailure)
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chat_messages"))
            assertArrayEquals(bytes, FileRepository(mContext, mPrimary).getFile(uuid)!!.readBytes())
            val remaining = mContext.cacheDir.listFiles().orEmpty()
                .filter { cached -> cached.name.startsWith("portable-") }
            assertTrue(
                "Backup workspaces were not removed: ${remaining.map { cached -> cached.name }}",
                remaining.isEmpty()
            )
        }
    }

    /** 删除群聊后不留下孤立图片关系，后续复用主键的新消息也不会继承旧引用。 */
    @Test
    fun deletingGroupDocumentPreventsImagesFromAttachingToFutureMessages() = runBlocking {
        seedGroup()
        attach(MessageType.Group, 1L, png(0xff0000ff.toInt()))
        val (archive, unpacked) = editableArchive()
        File(unpacked, "group-chats").deleteRecursively()
        restore(archive, unpacked)

        // 重建库中的自增序列允许复用已删除的数字，必须靠恢复关系清理保证隔离。
        assertEquals(0L, scalar("SELECT COUNT(*) FROM message_images"))
        seedGroup()
        assertTrue(mPrimary.getMessageImageDao().getByMessage(MessageType.Group, 1L).isEmpty())
    }

    /** 新增故事按提示匹配角色和新世界书，已有故事仍严格保留来源及运行状态。 */
    @Test
    fun addedStoryMatchesHintsBeforeNewWorldbookIdsAreAssigned() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        mPrimary.getLorebookDao().insertOrReplace(Lorebook(id = 7L, name = "Book"))
        mPrimary.getLorebookEntryDao().insertOrReplace(entry())
        seedStory()
        mPrimary.getStoryCharacterDao().insertOrReplace(
            StoryCharacter(
                storyId = 9L,
                characterId = 5L,
                sortOrder = 0,
                activationMode = StoryCharacter.ACTIVATION_PRIMARY
            )
        )
        mPrimary.getStoryLorebookEntryDao().insertOrReplace(
            StoryLorebookEntry(
                storyId = 9L, lorebookEntryId = 8L, activatedAtStep = 1, stickyUntilStep = 4
            )
        )
        val (archive, unpacked) = editableArchive()
        val original = File(unpacked, "stories").listFiles()!!.single()
        val added = JSONObject(original.readText()).apply { remove(BackupV3.SIDECAR) }
        added.getJSONObject("story").put("title", "Added story")
        added.getJSONArray("lorebookHints").getJSONObject(0).put("lorebookName", "New book")
        File(unpacked, "stories/added.rpstory.json").writeText(added.toString())
        val book = JSONObject(File(unpacked, "worldbooks").listFiles()!!.single().readText())
        book.remove(BackupV3.SIDECAR)
        book.put("name", "New book")
        File(unpacked, "worldbooks/new.json").writeText(book.toString())
        restore(archive, unpacked)

        // 两个条目正文相同，指纹有歧义；必须用书名和条目名匹配尚未分配 ID 的新书。
        val storyId = scalar("SELECT id FROM stories WHERE title = 'Added story'")
        val selected = mPrimary.getStoryCharacterDao().getByStoryId(storyId).single()
        assertEquals(5L, selected.characterId)
        assertEquals(StoryCharacter.ACTIVATION_PRIMARY, selected.activationMode)
        val relation = mPrimary.getStoryLorebookEntryDao().getByStoryId(storyId).single()
        assertEquals(
            scalar("SELECT e.id FROM lorebook_entries e JOIN lorebooks b ON b.id=e.lorebookId WHERE b.name='New book'"),
            relation.lorebookEntryId
        )
        assertEquals(null, relation.stickyUntilStep)
        assertEquals(
            4,
            mPrimary.getStoryLorebookEntryDao().getByStoryId(9L).single().stickyUntilStep
        )
    }

    /** 已启用搜索的备份恢复后保持关闭，用户手动开启才按修改后的三类正文重建。 */
    @Test
    fun restoredSearchRemainsDisabledUntilUserEnablesFreshBuild() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        seedChat(content = "originalword")
        mPrimary.getChatMessageDao().insertOrReplace(message(id = 21L, content = "deletedneedle"))
        seedGroup(content = "originalword")
        seedStory(content = "originalword")
        val previousIndex = index()
        previousIndex.enable()
        awaitReady(previousIndex)
        assertEquals(
            3,
            previousIndex.search(SearchRequest("originalword", scope = SearchScope.Body)).hits.size
        )
        previousIndex.stop()
        val (archive, unpacked) = editableArchive()
        val chat = File(unpacked, "chats").listFiles()!!.single()
        val chatLines = chat.readLines().map(::JSONObject)
        chatLines[1].put("mes", "replacementword")
        writeLines(chat, chatLines.take(2))
        writeChat(unpacked, "added.jsonl", "Added chat", "addedneedle")
        val group = File(unpacked, "group-chats").listFiles()!!.single()
        group.writeText(group.readText().replace("originalword", "replacementword"))
        val story = File(unpacked, "stories").listFiles()!!.single()
        story.writeText(story.readText().replace("originalword", "replacementword"))
        // 模拟此前导出的 v3 中仍带有旧缓存与启用状态，恢复时必须忽略这些内容。
        listOf("search_sources", "search_chunks", "search_progress").forEach { table ->
            BackupV3.dumpTable(
                mPrimary.openHelper.readableDatabase,
                table,
                File(unpacked, "tables/$table.jsonl")
            )
        }
        restore(archive, unpacked)

        val restoredIndex = index()
        restoredIndex.restore()
        awaitDisabled(restoredIndex)
        val request = SearchRequest("replacementword", scope = SearchScope.Body)
        val scanned = restoredIndex.search(request)
        assertEquals(setOf(1, 2, 3), scanned.hits.map { hit -> hit.kind }.toSet())
        // 手动开启触发正式全量构建，新结果应与关闭加速时直接扫描的结果一致。
        restoredIndex.enable()
        awaitReady(restoredIndex)
        val changed =
            restoredIndex.search(SearchRequest("replacementword", scope = SearchScope.Body))
        assertEquals(setOf(1, 2, 3), changed.hits.map { hit -> hit.kind }.toSet())
        assertEquals(
            1,
            restoredIndex.search(SearchRequest("addedneedle", scope = SearchScope.Body)).hits.size
        )
        assertTrue(
            restoredIndex.search(
                SearchRequest(
                    "originalword",
                    scope = SearchScope.Body
                )
            ).hits.isEmpty()
        )
        assertTrue(
            restoredIndex.search(
                SearchRequest(
                    "deletedneedle",
                    scope = SearchScope.Body
                )
            ).hits.isEmpty()
        )
    }

    /** 原本关闭的搜索不会因恢复被开启，修改后的正文仍可经扫描模式检索。 */
    @Test
    fun restoreKeepsSearchDisabledAndScanReadsEditedContent() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        seedChat()
        val (archive, unpacked) = editableArchive()
        val file = File(unpacked, "chats").listFiles()!!.single()
        file.writeText(file.readText().replace("Original message", "changedneedle"))
        restore(archive, unpacked)

        val restoredIndex = index()
        restoredIndex.restore()
        awaitDisabled(restoredIndex)
        assertEquals(
            1,
            restoredIndex.search(SearchRequest("changedneedle", scope = SearchScope.Body)).hits.size
        )
    }

    /** 旧 SQLite 快照恢复也不能继承搜索开关或缓存，业务正文仍可手动重新索引。 */
    @Test
    fun legacySnapshotRestoreAlsoDisablesSearchAcceleration() = runBlocking {
        mPrimary.getCharacterDao().insertOrReplace(character())
        seedChat(content = "legacyneedle")
        val previousIndex = index()
        previousIndex.enable()
        awaitReady(previousIndex)
        previousIndex.stop()
        mPrimary.close()
        mLogs.close()
        val unpacked = File(mRoot, "legacy-snapshot").apply { mkdirs() }
        val snapshot = File(unpacked, "database/primary.sqlite").apply { parentFile!!.mkdirs() }
        mContext.getDatabasePath("primary.sqlite").copyTo(snapshot)
        File(unpacked, "manifest.json").writeText(
            JSONObject()
                .put("format", "rpclient-backup").put("version", 1)
                .put("databaseSha256", BackupArchiveFiles.sha256(snapshot)).toString()
        )
        // 原始快照已包含真实启用状态和 FTS 数据，覆盖前仍须执行统一恢复准备。
        val archive = File(mRoot, "legacy.zip")
        pack(unpacked, archive)
        repository().restoreArchive(Uri.fromFile(archive))
        openDatabases()
        val restoredIndex = index()
        restoredIndex.restore()
        awaitDisabled(restoredIndex)
        restoredIndex.enable()
        awaitReady(restoredIndex)
        assertEquals(
            1,
            restoredIndex.search(SearchRequest("legacyneedle", scope = SearchScope.Body)).hits.size
        )
    }

    /** 统一准备有效角色，避免测试依赖应用默认示例。 */
    private fun character(id: Long = 5L) = Character(
        id = id, name = "Alice", avatar = "", characterTags = "[]", description = "Description",
        personality = "", scenario = "", firstMessages = "Hello", examplesOfDialogue = "",
        postHistoryInstructions = ""
    )

    /** 构造包含完整必填字段的世界书条目。 */
    private fun entry() = LorebookEntry(
        id = 8L, lorebookId = 7L, name = "Entry", keywords = "[]", secondaryKeywords = "[]",
        order = 0, depth = 4, category = "[]", content = "Entry text"
    )

    /** 以固定来源标识创建单聊及一条普通消息。 */
    private suspend fun seedChat(content: String = "Original message") {
        mPrimary.getChatSessionDao().insertOrReplace(
            ChatSession(
                id = 15L, characterId = 5L, createTime = 1L, latestTime = 3L,
                lorebookEntrySet = "[]", title = "Chat", userNote = ""
            )
        )
        mPrimary.getChatMessageDao().insertOrReplace(message(content = content))
    }

    /** 每条消息具有独立时间，保证测试只聚焦关联与正文恢复。 */
    private fun message(id: Long = 20L, content: String = "Original message") = ChatMessage(
        id = id,
        sessionId = 15L,
        createTime = id,
        source = ChatMessage.Source.User,
        content = content
    )

    /** 创建不依赖角色成员的用户群聊消息。 */
    private suspend fun seedGroup(content: String = "Group message") {
        mPrimary.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(
                id = 1L,
                title = "Group",
                createTime = 1L,
                latestTime = 2L,
                userName = "User",
                userDescription = ""
            )
        )
        mPrimary.getGroupChatMessageDao().insertOrReplace(
            GroupChatMessage(
                id = 1L, sessionId = 1L, createTime = 2L, source = GroupChatMessage.Source.User,
                content = content, speakerNameSnapshot = "User"
            )
        )
    }

    /** 创建有效故事及一章正文，提示关系由具体场景添加。 */
    private suspend fun seedStory(content: String = "Chapter text") {
        mPrimary.getStoryDao()
            .insertOrReplace(Story(id = 9L, title = "Story", createTime = 1L, latestTime = 2L))
        mPrimary.getStoryChapterDao().insertOrReplace(
            StoryChapter(
                id = 10L, storyId = 9L, title = "Chapter", content = content, sortOrder = 0,
                createTime = 1L, latestTime = 2L
            )
        )
    }

    /** 使用实际文件仓库存储原图和独立索引，再建立有效消息关系。 */
    private suspend fun attach(type: MessageType, messageId: Long, bytes: ByteArray): String {
        val uuid = FileRepository(mContext, mPrimary).saveFile(bytes, "image/png")
        mPrimary.getMessageImageDao().insertAll(
            listOf(
                MessageImageEntity(
                    messageType = type, messageId = messageId, position = 0, imageUuid = uuid
                )
            )
        )
        return uuid
    }

    /** 小尺寸真实 PNG 能覆盖格式校验，同时避免分配大图。 */
    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 构造可编辑的图片协议节点，hash 缺省时由恢复端对原始字节计算。 */
    private fun embeddedImage(bytes: ByteArray) = JSONObject()
        .put("mime_type", "image/png").put("compression", "none")
        .put("data", Base64.getEncoder().encodeToString(bytes))

    /** 读取本项目导出的图片数组。 */
    private fun imageArray(message: JSONObject): JSONArray =
        message.getJSONObject("extra").getJSONObject("rpclient").getJSONArray("images")

    /** 构造菜单协议的新增聊天，不携带本地数据库标识。 */
    private fun chatArchive(title: String, messages: List<ChatArchiveMessage>) = ChatArchive(
        title = title, createTime = 1L, latestTime = 3L, userName = "User", userDescription = "",
        userNote = "", creatorNotes = null, lorebookEntrySet = "[]", worldInfoStateJson = "{}",
        autoSummaryPaused = false, characterNameHint = "Alice", characterFingerprint = null,
        messages = messages, summary = null
    )

    /** 用正式聊天编码器向归档添加一份无本地主键的文档。 */
    private fun writeChat(
        root: File,
        name: String,
        title: String,
        content: String = "New message"
    ) {
        val chat =
            chatArchive(title, listOf(ChatArchiveMessage(2L, ChatArchiveMessageRole.User, content)))
        File(root, "chats/$name").apply { parentFile!!.mkdirs() }
            .writeText(ChatArchiveCodec(Gson()).encode(chat))
    }

    /** 原样保留 JSONL 的对象顺序。 */
    private fun writeLines(file: File, lines: List<JSONObject>) {
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    /** 导出并解包真实 ZIP，模拟用户在外部编辑。 */
    private suspend fun editableArchive(): Pair<File, File> {
        val archive = File(mRoot, "${UUID.randomUUID()}.zip")
        repository().exportArchive(Uri.fromFile(archive))
        val unpacked = File(mRoot, "edit-${UUID.randomUUID()}").apply { mkdirs() }
        BackupArchiveFiles.extractArchive(mContext, Uri.fromFile(archive), unpacked)
        return archive to unpacked
    }

    /** 通过公共恢复入口实际替换，并重新创建 Room 连接。 */
    private suspend fun restore(archive: File, unpacked: File) {
        pack(unpacked, archive)
        repository().restoreArchive(Uri.fromFile(archive))
        openDatabases()
    }

    /** 将编辑结果压回 ZIP，目录条目也保持一致。 */
    private fun pack(root: File, target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            // 只打包测试编辑目录，避免把 ZIP 自身再次加入归档。
            root.walkTopDown().drop(1).forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(if (file.isDirectory) "$path/" else path))
                if (file.isFile) file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** 每次恢复后绑定新的数据库实例。 */
    private fun repository() = PortableBackupRepository(
        mContext, mPrimary, mLogs, BackupArchiveRepository(mContext, mPrimary)
    )

    /** 测试库也使用正式打开回调，覆盖搜索状态的跨连接恢复。 */
    private fun openDatabases() {
        mPrimary = Room.databaseBuilder(
            mContext,
            AppDatabase::class.java,
            mContext.getDatabasePath("primary.sqlite").path
        )
            .addCallback(SearchDatabaseCallback).build()
        mLogs = Room.databaseBuilder(
            mContext,
            RequestLogDatabase::class.java,
            mContext.getDatabasePath("request_logs.sqlite").path
        )
            .build()
    }

    /** 检索管理器由清理账本持有，避免测试退出后继续访问已关闭数据库。 */
    private fun index() = ContentSearchRepository(mPrimary).also { value -> mIndexes.add(value) }

    /** 等待真实异步投影完成，设置短超时避免失败时无限等待。 */
    private suspend fun awaitReady(index: ContentSearchRepository) {
        withTimeout(10_000L) {
            while (index.indexState.value != SearchIndexState.Ready) delay(25L)
        }
    }

    /** 等待恢复后的关闭状态，并确认没有来源、词条或待恢复的构建检查点。 */
    private suspend fun awaitDisabled(index: ContentSearchRepository) {
        withTimeout(10_000L) {
            while (index.indexState.value != SearchIndexState.Disabled) delay(25L)
        }
        assertEquals(0L, scalar("SELECT COUNT(*) FROM search_sources"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM search_chunks"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM search_progress WHERE kind != -2"))
        assertEquals(
            0L,
            scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND (name GLOB 'search_source_*' OR name GLOB 'search_meta_*')")
        )
    }

    /** 标量断言只读取测试固定 SQL，不将用户输入拼入查询。 */
    private fun scalar(sql: String): Long =
        mPrimary.openHelper.readableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    /** 比较稳定索引集合，不依赖随机 UUID 的实际文本。 */
    private fun imageUuids(): Set<String> =
        mPrimary.openHelper.readableDatabase.query("SELECT imageUuid FROM message_images")
            .use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
}
