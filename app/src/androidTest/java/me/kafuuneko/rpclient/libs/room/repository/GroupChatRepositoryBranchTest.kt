package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.model.MessageWithImages
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.withTestPersonas
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

/**
 * 使用真实 Room 和独占文件目录验证群聊分支的数据生命周期。
 * - 覆盖消息截断、摘要边界与源会话的双向隔离。
 * - 验证跨消息类型的附件引用、删除回收和跨表失败回滚。
 */
@RunWith(AndroidJUnit4::class)
class GroupChatRepositoryBranchTest {
    private lateinit var mDatabase: AppDatabase
    private lateinit var mFiles: FileRepository
    private lateinit var mImages: MessageImageRepository
    private lateinit var mPersonas: UserPersonaRepository
    private lateinit var mRepository: GroupChatRepository
    private lateinit var mDirectory: File
    private lateinit var mCharacterIds: List<Long>
    private var mSessionId = 0L

    /** 创建真实成员及带完整设置的源群聊，文件操作限制在本测试独占目录。 */
    @Before
    fun setUp() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        mDirectory = File(base.cacheDir, "group-branch-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int): File =
                File(mDirectory, name).apply { mkdirs() }
        }
        mDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withTestPersonas().build()
        mFiles = FileRepository(context, mDatabase)
        mImages = MessageImageRepository(mDatabase, mFiles)
        mPersonas = UserPersonaRepository(mDatabase, mFiles)
        mRepository = GroupChatRepository(mDatabase, Gson(), mImages, mPersonas)
        // 群聊关系采用非默认顺序和静音状态，防止复制时误走新建群聊的默认配置。
        mCharacterIds = listOf("Alice", "Bob", "Carol").map { name -> insertCharacter(name) }
        mSessionId = mRepository.createSession(
            title = "Source",
            userName = "User",
            userDescription = "Session persona",
            characterIds = mCharacterIds.reversed(),
            activationStrategy = GroupChatSession.ActivationStrategy.Pooled,
            allowSelfResponses = true,
            createTime = 100L,
        )
        mRepository.updateMemberMuted(mSessionId, mCharacterIds[1], muted = true)
        mRepository.updateSession(
            requireNotNull(mRepository.getSessionById(mSessionId)).copy(
                scenario = "Current scene",
                userNote = "Current note",
                lorebookEntrySet = "[11,12]",
                worldInfoStateJson = "{\"future\":true}",
                systemPromptOverride = "System override",
                groupNudgePromptOverride = "Nudge override",
                newGroupChatPromptOverride = "Opening override",
                characterCardMode = GroupChatSession.CharacterCardMode.Join,
                includeMutedCards = true,
                includeCharacterExamples = false,
                autoModeEnabled = true,
                trimOtherSpeakers = false,
                autoSummaryPaused = true,
            ),
        )
    }

    /** 只释放本测试创建的内存数据库和独占目录。 */
    @After
    fun tearDown() {
        mDatabase.close()
        mDirectory.deleteRecursively()
    }

    /** 对分支的成员、正文、摘要和配置写入不会改变源群聊，源删除也不截断分支。 */
    @Test
    fun branchCopiesPrefixAndKeepsChangesIndependent() = runBlocking {
        val ids = listOf(
            message("Question", 1_000L, source = GroupChatMessage.Source.User),
            message("First reply", 1_001L, batchId = "source-batch"),
            message("Second reply", 1_002L, batchId = "source-batch"),
            message("Narration", 1_003L, source = GroupChatMessage.Source.System),
            message("Future reply", 1_004L),
        )
        mRepository.saveSummary(mSessionId, "Original memory", ids.first())
        val original = requireNotNull(mRepository.getGroupChatData(mSessionId))
        val originalMembers = mDatabase.getGroupChatMemberDao().getMembers(mSessionId)
        val branchId = mRepository.createBranchSession(mSessionId, ids[3], "Branch", 5_000L)
        val branch = requireNotNull(mRepository.getGroupChatData(branchId))

        // 保留原始内容和历史署名，新的消息及批次不能复用源标识。
        assertEquals(
            original.messages.take(4).map { it.content },
            branch.messages.map { it.content })
        assertEquals(
            original.messages.take(4).map { it.speakerNameSnapshot },
            branch.messages.map { it.speakerNameSnapshot })
        assertTrue(branch.messages.none { it.id in ids })
        assertEquals(branch.messages[1].generationBatchId, branch.messages[2].generationBatchId)
        assertNotEquals("source-batch", branch.messages[1].generationBatchId)
        assertNull(branch.messages.first().generationBatchId)
        assertEquals(
            original.session.copy(
                id = branchId,
                title = "Branch",
                createTime = 5_000L,
                latestTime = 5_000L,
                worldInfoStateJson = "{}"
            ),
            branch.session,
        )
        assertEquals(
            originalMembers.map { it.copy(sessionId = branchId) },
            mDatabase.getGroupChatMemberDao().getMembers(branchId)
        )
        assertEquals(branch.messages.first().id, branch.summary?.coveredMessageId)
        assertEquals(original, mRepository.getGroupChatData(mSessionId))

        // 分支修改触发自身摘要失效，源摘要及成员记录仍保持原样。
        mRepository.updateSession(
            branch.session.copy(
                scenario = "Branch scene",
                userNote = "Branch note"
            )
        )
        mRepository.updateMemberMuted(branchId, mCharacterIds[1], muted = false)
        mRepository.removeMember(branchId, mCharacterIds.last())
        mRepository.updateMessageContent(branch.messages.first().id, "Edited question")
        mRepository.deleteMessage(branch.messages[1].id)
        assertNull(mDatabase.getGroupChatSummaryDao().getLatest(branchId))
        assertEquals(original, mRepository.getGroupChatData(mSessionId))
        assertEquals(originalMembers, mDatabase.getGroupChatMemberDao().getMembers(mSessionId))
        mRepository.deleteMessage(ids[3])
        assertNotNull(mRepository.getMessageById(branch.messages[3].id))
        assertEquals(
            "Edited question",
            mRepository.getMessageById(branch.messages.first().id)?.content
        )
        assertEquals(3, mRepository.getMessageCount(branchId))
    }

    /** 相同时间按 ID 截断，非单调时间的边界按真实消息顺序选择而非只比较 ID。 */
    @Test
    fun summaryUsesStableInclusiveMessageBoundary() = runBlocking {
        val first = message("First", 100L)
        val second = message("Second", 200L)
        val third = message("Third", 200L)
        val fourth = message("Fourth", 300L)
        val insertedEarlier = message("Earlier time", 150L)
        val summaries = mDatabase.getGroupChatSummaryDao()
        listOf(first to "First memory", third to "Third memory", fourth to "Future memory")
            .forEach { (boundary, content) ->
                summaries.insertOrReplace(
                    GroupChatSummary(
                        sessionId = mSessionId,
                        createTime = 1L,
                        content = content,
                        coveredMessageId = boundary
                    )
                )
            }
        val cases = listOf(
            Triple(first, "First memory", "First"),
            Triple(second, "First memory", "First"),
            Triple(third, "Third memory", "Third"),
            Triple(fourth, "Future memory", "Fourth"),
            Triple(insertedEarlier, "First memory", "First"),
        )
        // 每个候选都通过完整分支事务，验证摘要 ID 映射后的实际上下文。
        cases.forEach { (throughId, expectedMemory, expectedBoundaryContent) ->
            val branchId = mRepository.createBranchSession(mSessionId, throughId, "Branch")
            val branch = requireNotNull(mRepository.getGroupChatData(branchId))
            val summary = requireNotNull(branch.summary)
            assertEquals(expectedMemory, summary.content)
            val boundaryContent =
                branch.messages.single { it.id == summary.coveredMessageId }.content
            assertEquals(expectedBoundaryContent, boundaryContent)
            assertEquals(
                mRepository.getMessageById(throughId)?.content,
                branch.messages.last().content
            )
        }
    }

    /** 主动清空的摘要优先于旧记忆，损坏或跨会话的覆盖边界不能复活旧内容。 */
    @Test
    fun clearedSummarySurvivesInvalidNewerBoundaries() = runBlocking {
        val first = message("First", 100L)
        val last = message("Last", 200L)
        mRepository.saveSummary(mSessionId, "Old memory", last)
        mRepository.updateCurrentSummary(mSessionId, "")
        val foreignSession = mDatabase.getGroupChatSessionDao().insertOrReplace(
            requireNotNull(mRepository.getSessionById(mSessionId)).copy(id = 0L),
        )
        val foreignMessage = mDatabase.getGroupChatMessageDao().insertOrReplace(
            requireNotNull(mRepository.getMessageById(first)).copy(
                id = 0L,
                sessionId = foreignSession
            ),
        )
        // 覆盖边界本身没有外键约束，模拟已有损坏记录时只能选择仍有效的空快照。
        listOf(foreignMessage, Long.MAX_VALUE).forEach { boundary ->
            mDatabase.getGroupChatSummaryDao().insertOrReplace(
                GroupChatSummary(
                    sessionId = mSessionId,
                    createTime = 1L,
                    content = "Invalid memory",
                    coveredMessageId = boundary
                ),
            )
        }
        val branchId = mRepository.createBranchSession(mSessionId, first, "Branch")
        val branch = requireNotNull(mRepository.getGroupChatData(branchId))
        assertEquals("", branch.summary?.content)
        assertEquals(0L, branch.summary?.coveredMessageId)
        assertEquals(
            listOf("First"),
            mRepository.getMessagesAfterLatestSummary(branchId).map { it.content })
    }

    /** 各来源附件只从群聊读取，源会话删除后分支仍可读，最后引用删除后才回收字节。 */
    @Test
    fun typedImagesSurviveSourceDeletionForEveryMessageSource() = runBlocking {
        val originals = GroupChatMessage.Source.entries.mapIndexed { index, source ->
            val id = message(source.name, 1_000L + index, source)
            mRepository.editMessageWithImages(
                sessionId = mSessionId,
                messageId = id,
                content = source.name,
                images = listOf(
                    MessageImageInput.Prepared(prepared("${source.name}-first")),
                    MessageImageInput.Prepared(prepared("${source.name}-second")),
                ),
            )
        }
        // 创建同数值单聊消息，确保群聊复制不会串读 single 类型的附件。
        val singleId = mDatabase.getChatSessionDao().insertOrReplace(
            ChatSession(
                characterId = mCharacterIds.first(),
                createTime = 1L,
                latestTime = 1L,
                lorebookEntrySet = "[]",
                title = "Single",
                userNote = ""
            ),
        )
        val single = ChatRepository(mDatabase, Gson(), mImages, mPersonas)
            .createUserMessageWithImages(
                singleId,
                "Single",
                listOf(MessageImageInput.Prepared(prepared("single-only")))
            )
        assertEquals(originals.first().key.messageId, single.key.messageId)
        val branchId =
            mRepository.createBranchSession(mSessionId, originals.last().key.messageId, "Branch")
        val copied = mRepository.getMessagesWithImages(
            requireNotNull(mRepository.getGroupChatData(branchId)).messages.map { it.id },
        )
        originals.zip(copied)
            .forEach { (original, copy) -> assertIndependentImages(original, copy) }

        mRepository.deleteSession(mSessionId)
        // 读取真实字节证明分支引用仍有效，单聊同 ID 的原图也不能被回收。
        copied.forEach { message ->
            message.images.forEachIndexed { index, image ->
                mFiles.withFileLease(image.image.imageUuid) { file ->
                    assertEquals(
                        "${message.source}-${if (index == 0) "first" else "second"}",
                        file.readText()
                    )
                }
            }
        }
        mFiles.withFileLease(single.images.single().image.imageUuid) { file ->
            assertEquals(
                "single-only",
                file.readText()
            )
        }
        mRepository.deleteSession(branchId)
        copied.flatMap { it.images }.forEach { image ->
            val hash = requireNotNull(image.file).hash
            assertEquals(0, mDatabase.getFileDao().countByHash(hash))
            assertFalse(File(mDirectory, "repository/$hash").exists())
        }
    }

    /** 删除分支及其独占引用不能释放源会话的索引或原图。 */
    @Test
    fun deletingBranchKeepsSourceImagesReadable() = runBlocking {
        val original = mRepository.createUserMessageWithImages(
            mSessionId,
            "Question",
            listOf(MessageImageInput.Prepared(prepared("source-image"))),
            "User",
        )
        val branchId = mRepository.createBranchSession(mSessionId, original.key.messageId, "Branch")
        val copied = mRepository.getMessagesWithImages(
            requireNotNull(mRepository.getGroupChatData(branchId)).messages.map { it.id },
        ).single()
        assertIndependentImages(original, copied)
        mRepository.deleteSession(branchId)
        val image = original.images.single()
        assertEquals(1, mDatabase.getFileDao().countByHash(requireNotNull(image.file).hash))
        assertNull(mFiles.getFileEntity(copied.images.single().image.imageUuid))
        mFiles.withFileLease(image.image.imageUuid) { file ->
            assertEquals(
                "source-image",
                file.readText()
            )
        }
    }

    /** 无效或跨会话的目标必须在写入前被拒绝，不能创建空的半成品分支。 */
    @Test
    fun invalidTargetsCannotCreateRecords() = runBlocking {
        val id = message("Source", 100L)
        val foreignSession = mDatabase.getGroupChatSessionDao().insertOrReplace(
            requireNotNull(mRepository.getSessionById(mSessionId)).copy(id = 0L),
        )
        val before = recordCounts()
        assertEquals(0L, mRepository.createBranchSession(mSessionId, Long.MAX_VALUE, "Invalid"))
        assertEquals(0L, mRepository.createBranchSession(foreignSession, id, "Foreign"))
        assertEquals(0L, mRepository.createBranchSession(Long.MAX_VALUE, id, "Missing"))
        assertEquals(before, recordCounts())
    }

    /** 最后写入摘要时失败，也必须回滚已经插入的成员、消息及附件 UUID。 */
    @Test
    fun failedSummaryInsertRollsBackTheEntireBranch() = runBlocking {
        val original = mRepository.createUserMessageWithImages(
            mSessionId,
            "Question",
            listOf(MessageImageInput.Prepared(prepared("kept-image"))),
            "User",
        )
        mRepository.saveSummary(mSessionId, "Memory", original.key.messageId)
        val before = recordCounts()
        mDatabase.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER reject_branch_summary BEFORE INSERT ON group_chat_summaries
                WHEN NEW.sessionId != $mSessionId
                BEGIN SELECT RAISE(ABORT, 'Rejected branch summary'); END""",
        )
        // 附件已参与事务后再拒绝提交，真实行数及原图读取共同验证回滚。
        try {
            val failure = runCatching {
                mRepository.createBranchSession(mSessionId, original.key.messageId, "Failed")
            }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(before, recordCounts())
            mFiles.withFileLease(original.images.single().image.imageUuid) { file ->
                assertEquals(
                    "kept-image",
                    file.readText()
                )
            }
        } finally {
            mDatabase.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_branch_summary")
        }
        val retriedId = mRepository.createBranchSession(mSessionId, original.key.messageId, "Retry")
        assertTrue(retriedId > 0L)
        assertEquals("Memory", mDatabase.getGroupChatSummaryDao().getLatest(retriedId)?.content)
    }

    /** 长前缀复制不能把末尾排到未来，立即发送的新消息必须仍位于末页。 */
    @Test
    fun longHistoryKeepsNewMessagesAfterTheCopiedPrefix() = runBlocking {
        val ids = mDatabase.getGroupChatMessageDao().insertOrReplaceAll(
            (0 until 2_000).map { index ->
                GroupChatMessage(
                    sessionId = mSessionId,
                    createTime = index / 2L,
                    source = GroupChatMessage.Source.User,
                    content = "Message $index",
                    speakerNameSnapshot = "User"
                )
            },
        )
        val createTime = System.currentTimeMillis()
        val branchId =
            mRepository.createBranchSession(mSessionId, ids.last(), "Long branch", createTime)
        val copied = requireNotNull(mRepository.getGroupChatData(branchId)).messages
        assertEquals(2_000, copied.size)
        assertEquals(createTime, copied.last().createTime)
        assertTrue(
            copied.zipWithNext().all { (before, after) -> before.createTime < after.createTime })
        // 通过实际末页查询验证继续发言的位置，避免只测试时间计算表达式。
        val nextId =
            mRepository.createMessage(branchId, GroupChatMessage.Source.User, "Next", null, "User")
        val page = requireNotNull(mRepository.getGroupChatPageData(branchId, pageSize = 5))
        assertEquals(nextId, page.data.messages.last().id)
        assertEquals("Message 1999", page.data.messages[3].content)
    }

    /** 构造真实角色外键，群聊成员的独立关系通过业务仓库验证。 */
    private suspend fun insertCharacter(name: String): Long =
        mDatabase.getCharacterDao().insertOrReplace(
            Character(
                name = name,
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            ),
        )

    /** 持久化包含历史署名的消息，创建时间可模拟相同时间与乱序时间。 */
    private suspend fun message(
        content: String,
        createTime: Long,
        source: GroupChatMessage.Source = GroupChatMessage.Source.Character,
        batchId: String? = null,
    ): Long = mRepository.createMessage(
        sessionId = mSessionId,
        source = source,
        content = content,
        speakerCharacterId = mCharacterIds.first()
            .takeIf { source == GroupChatMessage.Source.Character },
        speakerNameSnapshot = "Historical ${source.name}",
        generationBatchId = batchId,
        createTime = createTime,
    )

    /** 测试仅消费原始字节，格式解码不属于附件引用复制的职责。 */
    private suspend fun prepared(content: String): PreparedFile =
        mFiles.prepareStream(
            "group-branch",
            ByteArrayInputStream(content.toByteArray()),
            "image/png"
        )

    /** 核对附件排序、来源、共享 hash 和独立 UUID，避免同数值消息 ID 串读。 */
    private fun assertIndependentImages(original: MessageWithImages, copied: MessageWithImages) {
        assertEquals(original.key.messageType, copied.key.messageType)
        assertEquals(original.source, copied.source)
        assertEquals(
            original.images.map { it.image.position },
            copied.images.map { it.image.position })
        assertEquals(original.images.map { it.file?.hash }, copied.images.map { it.file?.hash })
        val originalUuids = original.images.map { it.image.imageUuid }.toSet()
        assertTrue(copied.images.none { it.image.imageUuid in originalUuids })
    }

    /** 记录所有分支相关表的实际行数，覆盖创建中途失败后的跨表残留。 */
    private fun recordCounts(): List<Long> =
        listOf(
            "group_chat_sessions",
            "group_chat_members",
            "group_chat_messages",
            "group_chat_summaries",
            "message_images",
            "files"
        )
            .map { table ->
                mDatabase.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table")
                    .use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getLong(0)
                    }
            }
}
