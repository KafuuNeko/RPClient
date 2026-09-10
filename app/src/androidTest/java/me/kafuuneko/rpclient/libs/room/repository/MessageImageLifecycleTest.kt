package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.model.MessageKey
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/** 使用独立私有目录和真实 Room 验证跨存储提交，避免测试修改应用已有原图。 */
@RunWith(AndroidJUnit4::class)
class MessageImageLifecycleTest {
    private lateinit var database: AppDatabase
    private lateinit var files: FileRepository
    private lateinit var images: MessageImageRepository
    private lateinit var chat: ChatRepository
    private lateinit var group: GroupChatRepository
    private lateinit var characters: CharacterRepository
    private lateinit var directory: File
    private lateinit var context: Context
    private var characterId = 0L
    private var sessionId = 0L
    private var groupId = 0L

    @Before
    /** 创建隔离的数据库、目录和最小消息父记录。 */
    fun setUp() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "image-lifecycle-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int): File = File(directory, name).apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        files = FileRepository(context, database)
        images = MessageImageRepository(database, files)
        chat = ChatRepository(database, Gson(), images)
        group = GroupChatRepository(database, Gson(), images)
        characters = CharacterRepository(database, Gson(), RegexScriptCodec(Gson()), images)
        // 独立创建单聊与群聊，测试两套自增 ID 空间的附件隔离。
        characterId = characters.saveCharacter(Character(name = "test", avatar = "", characterTags = "[]",
            description = "", personality = "", scenario = "", firstMessages = "",
            examplesOfDialogue = "", postHistoryInstructions = ""))
        sessionId = database.getChatSessionDao().insertOrReplace(ChatSession(characterId = characterId,
            createTime = 1, latestTime = 1, lorebookEntrySet = "[]", title = "test", userNote = "",
            userName = "user", userDescription = ""))
        groupId = database.getGroupChatSessionDao().insertOrReplace(GroupChatSession(title = "test",
            createTime = 1, latestTime = 1, userName = "user", userDescription = ""))
    }

    @After
    /** 关闭数据库并释放本测试独占目录。 */
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    /** 准备的是不解码的原始字节；实际图片格式检查属于 MEDIA 阶段。 */
    private suspend fun prepared(bytes: String = "same original"): PreparedFile =
        files.prepareStream("draft", ByteArrayInputStream(bytes.toByteArray()), "image/png")

    /** 抛错路径必须真实拒绝操作，不能只验证 DAO 调用次数。 */
    private suspend fun rejected(block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertNotNull("Expected operation to fail", failure)
    }

    @Test
    /** 验证分支拥有独立引用，删除源会话后仍可读取原图。 */
    fun branchOwnsIndependentReferencesAndSurvivesSourceDeletion() = runBlocking {
        val first = prepared()
        val second = prepared()
        val message = chat.createUserMessageWithImages(sessionId, "", listOf(
            MessageImageInput.Prepared(first), MessageImageInput.Prepared(second)))
        assertEquals(2, database.getFileDao().countByHash(first.file.hash))
        // 复制后再删除源会话，读取结果证明文件所有权已经独立。
        val branch = chat.createBranchSession(sessionId, message.key.messageId, "branch")
        val copied = chat.getMessageImagePage(branch, 10).messages.single()
        assertEquals(listOf(0, 1), copied.images.map { it.image.position })
        assertNotEquals(message.images.map { it.image.imageUuid }, copied.images.map { it.image.imageUuid })
        chat.deleteSession(sessionId)
        copied.images.forEach { image ->
            files.withFileLease(image.image.imageUuid) { assertEquals("same original", it.readText()) }
        }
        assertEquals(2, database.getFileDao().countByHash(first.file.hash))
        chat.deleteSession(branch)
        assertFalse(File(directory, "repository/${first.file.hash}").exists())
    }

    @Test
    /** 验证图文编辑的排序、替换、移除与摘要失效。 */
    fun editReordersReplacesAndInvalidatesCoveredSummary() = runBlocking {
        val a = prepared("a")
        val b = prepared("b")
        val message = chat.createUserMessageWithImages(sessionId, "before", listOf(
            MessageImageInput.Prepared(a), MessageImageInput.Prepared(b)))
        // 编辑覆盖范围内的图片，旧摘要必须与正文一同失效。
        chat.saveSummary(sessionId, "summary", message.key.messageId)
        val edited = chat.editUserMessageWithImages(sessionId, message.key.messageId, "after", listOf(
            MessageImageInput.Existing(b.file.uuid), MessageImageInput.Existing(a.file.uuid)))
        assertEquals(listOf(b.file.uuid, a.file.uuid), edited.images.map { it.image.imageUuid })
        assertNull(database.getChatMessageDao().getLatestSummaryBySessionId(sessionId))
        val replacement = prepared("replacement")
        chat.editUserMessageWithImages(sessionId, message.key.messageId, "", listOf(
            MessageImageInput.Prepared(replacement)))
        assertNull(files.getFileEntity(a.file.uuid))
        rejected { chat.editUserMessageWithImages(sessionId, message.key.messageId, "", emptyList()) }
        assertEquals("", chat.getMessageById(message.key.messageId)?.content)
        chat.editUserMessageWithImages(sessionId, message.key.messageId, "text", emptyList())
        assertNull(files.getFileEntity(replacement.file.uuid))
    }

    @Test
    /** 验证关系写入失败后整体回滚并能使用同一草稿重试。 */
    fun failedRelationInsertRollsBackMessageAndFilesAndKeepsDraft() = runBlocking {
        val original = prepared()
        val survivor = chat.createUserMessageWithImages(sessionId, "kept", listOf(MessageImageInput.Prepared(original)))
        val retry = prepared()
        // 人为模拟关系提交失败；原图已经发布但数据库必须整体回滚。
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_images BEFORE INSERT ON message_images BEGIN SELECT RAISE(ABORT, 'test'); END")
        rejected { chat.createUserMessageWithImages(sessionId, "failed", listOf(MessageImageInput.Prepared(retry))) }
        assertEquals(1, chat.getMessageCountBySessionId(sessionId))
        assertNull(files.getFileEntity(retry.file.uuid))
        assertNotNull(files.restorePrepared("draft", retry.handle))
        files.withFileLease(original.file.uuid) { assertEquals("same original", it.readText()) }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_images")
        chat.createUserMessageWithImages(sessionId, "retry", listOf(MessageImageInput.Prepared(retry)))
        rejected { chat.createUserMessageWithImages(sessionId, "duplicate", listOf(MessageImageInput.Prepared(retry))) }
        assertEquals(2, chat.getMessageCountBySessionId(sessionId))
        chat.deleteMessage(survivor.key.messageId)
        assertNotNull(files.getFile(retry.file.uuid))
    }

    @Test
    /** 验证父消息、来源、会话及既有图片所有权检查。 */
    fun invalidParentAndForeignAttachmentCannotCommit() = runBlocking {
        val a = prepared()
        val message = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(a)))
        val foreign = group.createUserMessageWithImages(groupId, "group", emptyList(), "user")
        rejected { group.editUserMessageWithImages(groupId, foreign.key.messageId, "", listOf(MessageImageInput.Existing(a.file.uuid))) }
        // 提交失败不得提前持久化新草稿的文件索引。
        val b = prepared("b")
        rejected { chat.editUserMessageWithImages(sessionId, Long.MAX_VALUE, "", listOf(MessageImageInput.Prepared(b))) }
        rejected { chat.editUserMessageWithImages(Long.MAX_VALUE, message.key.messageId, "", listOf(MessageImageInput.Existing(a.file.uuid))) }
        val characterMessage = chat.createMessage(sessionId, ChatMessage.Source.Char, "character")
        rejected { chat.editUserMessageWithImages(sessionId, characterMessage, "", listOf(MessageImageInput.Prepared(b))) }
        assertNull(files.getFileEntity(b.file.uuid))
        assertNotNull(files.restorePrepared("draft", b.handle))
        assertEquals(1, chat.getMessagesWithImages(listOf(message.key.messageId)).single().images.size)
    }

    @Test
    /** 验证群聊截断和角色级联删除不影响其他会话与类型。 */
    fun groupTruncationAndCharacterCascadeRespectSessionAndType() = runBlocking {
        val a = prepared()
        val single = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(a)))
        val b = prepared()
        val kept = group.createUserMessageWithImages(groupId, "", listOf(MessageImageInput.Prepared(b)), "user")
        assertEquals(single.key.messageId, kept.key.messageId)
        val c = prepared("remove")
        val removed = group.createUserMessageWithImages(groupId, "", listOf(MessageImageInput.Prepared(c)), "user")
        val anotherGroup = database.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(title = "other", createTime = 1, latestTime = 1, userName = "u", userDescription = ""))
        val d = prepared("other session")
        group.createUserMessageWithImages(anotherGroup, "", listOf(MessageImageInput.Prepared(d)), "user")
        // 按会话截断后，再走角色级联删除验证两种类型隔离。
        group.deleteMessagesFrom(removed.key.messageId)
        assertNull(files.getFileEntity(c.file.uuid))
        assertNotNull(files.getFileEntity(d.file.uuid))
        characters.deleteCharacter(characterId)
        assertNull(files.getFileEntity(a.file.uuid))
        files.withFileLease(b.file.uuid) { assertEquals("same original", it.readText()) }
        group.deleteSession(groupId)
        assertNull(files.getFileEntity(b.file.uuid))
        assertNotNull(files.getFileEntity(d.file.uuid))
    }

    @Test
    /** 验证读取租约保护删除中的原图及并发新引用。 */
    fun leaseProtectsBytesAcrossDeleteAndConcurrentSave() = runBlocking {
        val a = prepared()
        val message = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(a)))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reader = async(Dispatchers.IO) {
            // 不同实例也应共享目录租约；取消读者时 finally 必须释放保护。
            FileRepository(context, database).withFileLease(a.file.uuid) {
                entered.complete(Unit)
                release.await()
                assertEquals("same original", it.readText())
            }
        }
        entered.await()
        chat.deleteMessage(message.key.messageId)
        assertTrue(File(directory, "repository/${a.file.hash}").exists())
        val b = prepared()
        val replacement = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(b)))
        release.complete(Unit)
        reader.await()
        assertNotNull(files.getFile(b.file.uuid))
        chat.deleteMessage(replacement.key.messageId)
        assertFalse(File(directory, "repository/${a.file.hash}").exists())
    }

    @Test
    /** 验证提交与返回之间取消仍保留已提交消息和原图。 */
    fun cancellationAfterTransactionStartsKeepsCommittedResource() = runBlocking {
        val a = prepared()
        // 在不可取消的短事务内部取消调用方，模拟提交成功但结果未返回。
        val worker = launch(Dispatchers.IO) {
            val caller = currentCoroutineContext()[Job]!!
            images.mutate(listOf(a)) {
                val id = chat.createMessage(sessionId, ChatMessage.Source.User, "committed")
                images.replaceInTransaction(this, MessageKey(MessageType.Single, id), sessionId,
                    listOf(MessageImageInput.Prepared(a)))
                caller.cancel()
            }
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertEquals(1, chat.getMessageCountBySessionId(sessionId))
        files.releasePrepared(a)
        assertNull(files.restorePrepared("draft", a.handle))
        files.withFileLease(a.file.uuid) { assertEquals("same original", it.readText()) }
    }

    @Test
    /** 验证流式上限、准备失败、恢复清理与头像索引保护。 */
    fun preparationLimitFailureCleanupAndRecoveryKeepAvatarIndex() = runBlocking {
        rejected { files.prepareStream("draft", ByteArrayInputStream(ByteArray(20)), "image/png", 10) }
        rejected {
            files.prepareStream("draft", object : InputStream() {
                override fun read(): Int = throw java.io.IOException("test")
            }, "image/png")
        }
        assertTrue(File(directory, "repository/staging").listFiles().orEmpty().isEmpty())
        val avatar = files.saveFile(File(directory, "avatar").apply { writeText("avatar") })
        val draft = prepared("recover")
        assertNull(files.restorePrepared("wrong owner", draft.handle))
        val recovered = FileRepository(context, database).restorePrepared("draft", draft.handle)
        assertEquals(draft, recovered)
        // 模拟崩溃留下的无索引 hash 和不完整旧草稿。
        val orphan = File(directory, "repository/${"a".repeat(64)}").apply { writeText("orphan") }
        val abandoned = File(directory, "repository/staging/abandoned").apply { writeText("partial"); setLastModified(1) }
        files.cleanupAbandonedFiles()
        assertFalse(orphan.exists())
        assertFalse(abandoned.exists())
        assertNotNull(files.getFile(avatar))
        assertNotNull(files.restorePrepared("draft", draft.handle))
        files.releasePrepared(draft)
        assertNull(files.restorePrepared("draft", draft.handle))
    }

    @Test
    /** 验证跨查询参数上限的批量读取及图文分页边界。 */
    fun batchReadAndPaginationKeepWholeMessages() = runBlocking {
        val ids = database.getChatMessageDao().insertOrReplaceAll((0..1004).map {
            ChatMessage(sessionId = sessionId, createTime = 10, source = ChatMessage.Source.User, content = "$it")
        })
        val a = prepared()
        chat.editUserMessageWithImages(sessionId, ids.last(), "", listOf(MessageImageInput.Prepared(a)))
        // 超过单批参数上限后，结果仍按调用方顺序且保留完整附件。
        val snapshot = chat.getMessagesWithImages(ids.reversed())
        assertEquals(ids.reversed(), snapshot.map { it.key.messageId })
        assertEquals(a.file.uuid, snapshot.first().images.single().image.imageUuid)
        val latest = chat.getMessageImagePage(sessionId, 2)
        assertEquals(ids.takeLast(2), latest.messages.map { it.key.messageId })
        val before = latest.messages.first()
        val previous = chat.getMessageImagePage(sessionId, 2, before.createTime, before.key.messageId)
        assertEquals(ids.dropLast(2).takeLast(2), previous.messages.map { it.key.messageId })
        assertEquals(1005, previous.totalMessageCount)
        chat.deleteMessagesBySessionId(sessionId)
        assertNull(files.getFileEntity(a.file.uuid))
    }

    @Test
    /** 验证取消读取者后释放最后一个物理文件保护。 */
    fun cancelledLeaseReleasesLastPhysicalReference() = runBlocking {
        val a = prepared()
        val message = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(a)))
        val entered = CompletableDeferred<Unit>()
        val reader = launch(Dispatchers.IO) {
            files.withFileLease(a.file.uuid) {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        entered.await()
        chat.deleteMessage(message.key.messageId)
        assertTrue(File(directory, "repository/${a.file.hash}").exists())
        reader.cancelAndJoin()
        assertFalse(File(directory, "repository/${a.file.hash}").exists())
    }

    @Test
    /** 验证删除回滚与损坏暂存的提交拒绝。 */
    fun failedDeletionRollsBackRelationsAndCorruptDraftCannotPublish() = runBlocking {
        val a = prepared()
        val message = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(a)))
        // 文件索引删除失败时，附件关系和消息删除也必须一起回滚。
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delete BEFORE DELETE ON files BEGIN SELECT RAISE(ABORT, 'test'); END")
        rejected { chat.deleteMessage(message.key.messageId) }
        assertEquals(a.file.uuid, chat.getMessagesWithImages(listOf(message.key.messageId)).single().images.single().image.imageUuid)
        files.withFileLease(a.file.uuid) { assertEquals("same original", it.readText()) }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete")
        val damaged = prepared("abcd")
        File(directory, "repository/staging/${damaged.handle}").writeText("dcba")
        rejected { chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(damaged))) }
        assertNull(files.getFileEntity(damaged.file.uuid))
        assertFalse(File(directory, "repository/${damaged.file.hash}").exists())
        files.releasePrepared(damaged)
    }

    @Test
    /** 验证群聊图片编辑失效摘要，纯图片不被当作空生成占位删除。 */
    fun groupImageEditInvalidatesSummaryAndPureImageIsNotEmptyPlaceholder() = runBlocking {
        val a = prepared()
        val b = prepared("second")
        val message = group.createUserMessageWithImages(groupId, "", listOf(
            MessageImageInput.Prepared(a), MessageImageInput.Prepared(b)), "user")
        // 图片顺序变化也必须失效覆盖摘要，不能只比较正文。
        group.saveSummary(groupId, "summary", message.key.messageId)
        val edited = group.editUserMessageWithImages(groupId, message.key.messageId, "", listOf(
            MessageImageInput.Existing(b.file.uuid), MessageImageInput.Existing(a.file.uuid)))
        assertEquals(listOf(b.file.uuid, a.file.uuid), edited.images.map { it.image.imageUuid })
        assertNull(database.getGroupChatSummaryDao().getLatest(groupId))
        val c = prepared("single")
        val single = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(c)))
        chat.commitGenerationResult(sessionId, single.key.messageId, ChatMessage.Source.User, "", true, "{}")
        assertNotNull(chat.getMessageById(single.key.messageId))
        assertNotNull(files.getFileEntity(c.file.uuid))
        val page = group.getMessageImagePage(groupId, 1)
        assertEquals(1, page.messages.size)
        assertEquals(2, page.messages.single().images.size)
        group.deleteMessage(message.key.messageId)
        assertNull(files.getFileEntity(a.file.uuid))
    }

    @Test
    /** 验证普通用户消息的非空约束与生成占位例外。 */
    fun textOnlyEntryRejectsEmptyUserMessageButGenerationPlaceholderRemainsAvailable() = runBlocking {
        rejected { chat.createMessage(sessionId, ChatMessage.Source.User, "") }
        rejected {
            group.createMessage(groupId, me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage.Source.User,
                "", null, "user")
        }
        assertEquals(0, chat.getMessageCountBySessionId(sessionId))
        assertEquals(0, group.getMessageCount(groupId))
        val placeholder = chat.createGenerationPlaceholder(sessionId, ChatMessage.Source.User)
        chat.commitGenerationResult(sessionId, placeholder, ChatMessage.Source.User, "", true, "{}")
        assertNull(chat.getMessageById(placeholder))
    }

    @Test
    /** 验证共享 hash 的并发保存和回收不会破坏仍存在的引用。 */
    fun concurrentSharedHashWritersAndCollectorsKeepRemainingReferences() = runBlocking {
        val survivor = prepared()
        chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(survivor)))
        coroutineScope {
            (0 until 8).map {
                async(Dispatchers.IO) {
                    val draft = prepared()
                    val message = chat.createUserMessageWithImages(sessionId, "", listOf(MessageImageInput.Prepared(draft)))
                    chat.deleteMessage(message.key.messageId)
                }
            }.awaitAll()
        }
        assertEquals(1, database.getFileDao().countByHash(survivor.file.hash))
        files.withFileLease(survivor.file.uuid) { assertEquals("same original", it.readText()) }
    }
}
