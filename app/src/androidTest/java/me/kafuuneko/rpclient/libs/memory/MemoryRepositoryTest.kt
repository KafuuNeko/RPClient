package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemorySource
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemorySource
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.migration.MemoryDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.libs.room.withTestPersonas
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 SQLite 验证隔离、事务回滚、来源清理及历史分支。 */
@RunWith(AndroidJUnit4::class)
class MemoryRepositoryTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var mDatabase: AppDatabase
    private lateinit var mMemory: MemoryRepository
    private lateinit var mChat: ChatRepository
    private lateinit var mGroupChat: GroupChatRepository
    private var mCharA = 0L
    private var mCharB = 0L
    private var mSession = 0L
    private var mGroup = 0L

    @Before
    fun setup(): Unit = runBlocking {
        // 初始化内存数据库并安装跨字段约束触发器。
        mDatabase = Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java)
            .withTestPersonas()
            .addCallback(MemoryDatabaseConstraints)
            .allowMainThreadQueries()
            .build()
        mMemory = MemoryRepository(mDatabase)
        val files = FileRepository(mContext, mDatabase)
        val images = MessageImageRepository(mDatabase, files)
        val personas = UserPersonaRepository(mDatabase, files)
        mChat = ChatRepository(
            mAppDatabase = mDatabase,
            mGson = Gson(),
            mImages = images,
            mPersonas = personas,
            mMemory = mMemory,
            mScenes = SceneStateRepository(mDatabase, AgentRunRepository(mDatabase)),
        )
        mGroupChat = GroupChatRepository(
            mDatabase,
            Gson(),
            images,
            personas,
            mMemory,
            SceneStateRepository(mDatabase, AgentRunRepository(mDatabase))
        )
        // 创建同名角色卡、单聊会话和群聊成员基线数据。
        mCharA = mDatabase.getCharacterDao().insertOrReplace(character("Same name"))
        mCharB = mDatabase.getCharacterDao().insertOrReplace(character("Same name"))
        mSession = mDatabase.getChatSessionDao().insertOrReplace(
            ChatSession(
                characterId = mCharA,
                createTime = 1,
                latestTime = 1,
                lorebookEntrySet = "[]",
                title = "Single",
                userNote = "",
                userPersonaId = 1,
            ),
        )
        mGroup = mDatabase.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(
                title = "Group",
                createTime = 1,
                latestTime = 1,
                userPersonaId = 1,
            ),
        )
        mDatabase.getGroupChatMemberDao().insertOrReplaceAll(
            listOf(GroupChatMember(mGroup, mCharA, 0), GroupChatMember(mGroup, mCharB, 1)),
        )
    }

    @After
    fun close() = mDatabase.close()

    /** 同名卡、三表同主键、人设和局部覆盖都使用完整身份进行隔离。 */
    @Test
    fun ownerPersonaAndOverrideIsolationWithCollidingIds() = runBlocking {
        // 写入三张物理表并确认相同自增主键不能跨表误删。
        val common = mMemory.save(MemoryTarget.Character(mCharA), MemoryDraft("tea", pinned = true))
        val local = mMemory.save(MemoryTarget.Chat(mSession), MemoryDraft("local"))
        val private = mMemory.save(
            MemoryTarget.Group(mGroup),
            MemoryDraft("private", privateCharacterId = mCharB),
        )
        assertEquals(common.ref.id, local.ref.id)
        assertEquals(common.ref.id, private.ref.id)
        assertTrue(
            runCatching { mMemory.delete(MemoryTarget.Chat(mSession), common.ref, 1) }.isFailure,
        )
        // 验证不同人设、不同角色与错误受众的读取和写入隔离。
        val secondPersona = mDatabase.getUserPersonaDao().insert(UserPersona(name = "Other"))
        mMemory.save(
            MemoryTarget.Character(mCharA),
            MemoryDraft("other identity", personaId = secondPersona),
        )
        mMemory.save(MemoryTarget.Character(mCharB), MemoryDraft("other character"))
        assertEquals(
            listOf("tea"),
            mMemory.inherited(MemoryTarget.Chat(mSession), mCharA, 1)
                .map { it.fields.content.body },
        )
        assertTrue(mMemory.inherited(MemoryTarget.Chat(mSession), mCharB, 1).isEmpty())
        assertTrue(
            runCatching {
                mMemory.save(
                    MemoryTarget.Chat(mSession),
                    MemoryDraft("wrong persona", personaId = secondPersona),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                mMemory.save(
                    MemoryTarget.Chat(mSession),
                    MemoryDraft("wrong reader", privateCharacterId = mCharB),
                )
            }.isFailure,
        )
        // 验证群聊局部覆盖仅屏蔽当前群的对应角色继承，不影响单聊和角色目录计数。
        mMemory.save(
            MemoryTarget.Group(mGroup),
            MemoryDraft(
                body = "local tea",
                privateCharacterId = mCharA,
                overrideCharacterMemoryId = common.ref.id,
            ),
        )
        assertTrue(mMemory.inherited(MemoryTarget.Group(mGroup), mCharA, 1).isEmpty())
        assertEquals(
            listOf("tea"),
            mMemory.inherited(MemoryTarget.Chat(mSession), mCharA, 1)
                .map { it.fields.content.body },
        )
        assertEquals(
            listOf(1, 2),
            mMemory.observeCharacters().first().map { it.memoryCount }.sorted(),
        )
    }

    /** 并发版本及证据插入失败都不能推进主表或留下半个修订。 */
    @Test
    fun compareAndSetAndEvidenceFailureAreAtomic() = runBlocking {
        // 先验证过期版本号触发冲突异常。
        val target = MemoryTarget.Character(mCharA)
        val first = mMemory.save(target, MemoryDraft("original"))
        val saved = mMemory.save(target, MemoryDraft("updated"), first.ref, 1)
        assertTrue(
            runCatching {
                mMemory.save(target, MemoryDraft("stale"), first.ref, 1)
            }.exceptionOrNull() is MemoryConflictException,
        )
        // 注入证据表写入失败触发器，确认主表快照与修订表整体回滚。
        mDatabase.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_memory_evidence BEFORE INSERT ON character_memory_sources BEGIN SELECT RAISE(ABORT, 'fixture'); END",
        )
        assertTrue(
            runCatching {
                mMemory.save(target, MemoryDraft("must rollback"), saved.ref, 2)
            }.isFailure,
        )
        assertEquals(
            "updated",
            mDatabase.getCharacterMemoryDao().get(first.ref.id)?.fields?.content?.body,
        )
        assertEquals(listOf(2L, 1L), mMemory.history(target, first.ref).map { it.fields.revision })
    }

    /** 元数据不会生成独立确认；真正确认后的跨会话事实在删除来源时仍可保留。 */
    @Test
    fun hardDeleteScrubsDerivedHistoryButPreservesUserConfirmedBody() = runBlocking {
        // 仅修改置顶不生成用户确认证据，显式 confirm 后追加独立证据。
        val messageId = message("tea evidence")
        val derived = derived(messageId, "tea evidence")
        val pinned = mMemory.save(
            derived.target,
            MemoryDraft("tea evidence", pinned = true),
            derived.ref,
            1,
        )
        assertEquals(
            listOf("ChatMessage"),
            mMemory.history(pinned.target, pinned.ref).first().sources.map { it.sourceKind },
        )
        val confirmed = derived(messageId, "confirmed evidence")
        mMemory.confirm(confirmed.target, confirmed.ref, 1)
        // 硬删除原消息后，无独立确认的条目被物理清理，已确认条目仅保留用户确认修订。
        mChat.deleteMessage(messageId)
        assertNull(mDatabase.getCharacterMemoryDao().get(derived.ref.id))
        assertNotNull(mDatabase.getCharacterMemoryDao().get(confirmed.ref.id))
        val history = mMemory.history(confirmed.target, confirmed.ref)
        assertEquals(1, history.size)
        assertEquals(listOf("UserEdit"), history.single().sources.map { it.sourceKind })
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 编辑撤回唯一来源会失效当前事实，重生成也必须排除目标作为唯一证据。 */
    @Test
    fun sourceEditsInvalidateAndRegenerationExcludesUniqueEvidence() = runBlocking {
        // 重生成排除目标消息作为唯一来源时不可召回，编辑消息后条目变为失效并清空摘录。
        val messageId = message("old source")
        val derived = derived(messageId, "old fact")
        assertTrue(mMemory.inherited(MemoryTarget.Chat(mSession), mCharA, 1, messageId).isEmpty())
        mChat.updateMessageContent(messageId, "new source")
        assertEquals(
            "Invalidated",
            mDatabase.getCharacterMemoryDao().get(derived.ref.id)?.fields?.content?.status,
        )
        assertTrue(mMemory.inherited(MemoryTarget.Chat(mSession), mCharA, 1).isEmpty())
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
        // 后续硬删除被编辑过的消息时，保留的外键关系仍可触发物理清理。
        val source = mMemory.history(derived.target, derived.ref).first().sources.single()
        assertFalse(source.valid)
        assertEquals("", source.quote)
        mChat.deleteMessage(messageId)
        assertNull(mDatabase.getCharacterMemoryDao().get(derived.ref.id))
    }

    /** 角色范围不混入会话备份，移出成员只级联当前群的私有记录。 */
    @Test
    fun memberRemovalPreservesSharedAndOtherScopes() = runBlocking {
        // 创建群共享、群私有和角色通用三条记录，更新静音与排序不得触发级联删除。
        val shared = mMemory.save(MemoryTarget.Group(mGroup), MemoryDraft("shared"))
        val private = mMemory.save(
            MemoryTarget.Group(mGroup),
            MemoryDraft("private", privateCharacterId = mCharA),
        )
        val other = mMemory.save(MemoryTarget.Character(mCharA), MemoryDraft("common"))
        mDatabase.getGroupChatMemberDao().updateMuted(mGroup, mCharA, true)
        mDatabase.getGroupChatMemberDao().updateSortOrder(mGroup, mCharA, 9)
        assertNotNull(mDatabase.getGroupChatMemoryDao().get(private.ref.id))
        // 移除成员只级联当前群该成员私有记录，保留群共享和角色通用记忆。
        mDatabase.getGroupChatMemberDao().deleteMember(mGroup, mCharA)
        assertNull(mDatabase.getGroupChatMemoryDao().get(private.ref.id))
        assertNotNull(mDatabase.getGroupChatMemoryDao().get(shared.ref.id))
        assertNotNull(mDatabase.getCharacterMemoryDao().get(other.ref.id))
        mDatabase.getGroupChatMemberDao().insertOrIgnore(GroupChatMember(mGroup, mCharA, 0))
        assertEquals(1, mDatabase.getGroupChatMemoryDao().list(mGroup).size)
    }

    /** 已提升的消息与摘要证据离群后成为快照，全部历史及便携包仍通过恢复校验。 */
    @Test
    fun promotedGroupEvidenceSurvivesMemberRemovalAndRestoreValidation() = runBlocking {
        allowGroupMemberRemoval()
        // 使用真实消息、摘要证据提升，避免用户独立确认掩盖失去成员资格的问题。
        val messageId = groupMessage("message evidence")
        val summaryId = mDatabase.getGroupChatSummaryDao().insertOrReplace(
            GroupChatSummary(
                sessionId = mGroup,
                createTime = 1,
                content = "summary evidence",
                coveredMessageId = messageId
            ),
        )
        val messageMemory = groupDerived("GroupMessage", messageId)
        val summaryMemory = groupDerived("GroupSummary", summaryId, privateId = mCharA)
        val promotedMessage = mMemory.promote(messageMemory.target, messageMemory.ref, 1, mCharA)
        val promotedSummary = mMemory.promote(summaryMemory.target, summaryMemory.ref, 1, mCharA)
        mMemory.save(
            promotedMessage.target,
            MemoryDraft(promotedMessage.fields.content.body, pinned = true),
            promotedMessage.ref,
            1
        )
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)

        // 走真实移除入口后群消息仍在，角色记忆的当前和历史来源都应独立于成员表。
        mGroupChat.removeMember(mGroup, mCharA, expectedMemoryCount = 1)
        assertNull(mDatabase.getGroupChatMemoryDao().get(summaryMemory.ref.id))
        assertNotNull(mDatabase.getGroupChatMessageDao().getMessageById(messageId))
        assertNotNull(mDatabase.getGroupChatSummaryDao().getById(summaryId))
        assertEquals(2, mMemory.history(promotedMessage.target, promotedMessage.ref).size)
        listOf(
            promotedMessage to "message evidence",
            promotedSummary to "summary evidence"
        ).forEach { (entry, quote) ->
            mMemory.history(entry.target, entry.ref).forEach { revision ->
                val source = revision.sources.single()
                assertEquals("PromotedSnapshot", source.sourceKind)
                assertNull(source.groupMessageId)
                assertNull(source.groupSummaryId)
                assertEquals(quote, source.quote)
                assertEquals(MemoryRestoreValidator.fingerprint(quote), source.fingerprint)
                assertTrue(source.originRef.startsWith("Group/$mGroup/"))
            }
        }
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)

        // 便携导出与重新导入保留快照来源类型，不把旧消息 ID 绑定到本机新证据。
        val exported = MemoryPackageCodec.decode(mMemory.exportPackage(promotedMessage.target))
        mMemory.delete(promotedMessage.target, promotedMessage.ref, 2)
        mMemory.delete(promotedSummary.target, promotedSummary.ref, 1)
        assertEquals(
            2,
            mMemory.importPackage(MemoryTarget.Character(mCharB), exported, emptyMap(), emptyMap())
        )
        mDatabase.getCharacterMemoryDao().list(mCharB).forEach { entry ->
            val history = mMemory.history(
                MemoryTarget.Character(mCharB),
                MemoryRef(MemoryTable.Character, entry.id)
            )
            assertTrue(history.flatMap { it.sources }.all { it.sourceKind == "PromotedSnapshot" })
        }
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 移出成员只冻结该角色引用本群的证据，其他角色和其他群的实时来源保持有效。 */
    @Test
    fun memberRemovalSnapshotsOnlyDepartingCharactersGroupEvidence() = runBlocking {
        allowGroupMemberRemoval()
        val messageId = groupMessage("current group")
        val source = groupDerived("GroupMessage", messageId)
        val departing = mMemory.promote(source.target, source.ref, 1, mCharA)
        val remaining = mMemory.promote(source.target, source.ref, 1, mCharB)
        // 同一角色在另一个群的证据仍须保留实时消息关联。
        val otherGroup = mDatabase.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(title = "Other", createTime = 1, latestTime = 1, userPersonaId = 1),
        )
        mDatabase.getGroupChatMemberDao().insertOrReplaceAll(
            listOf(GroupChatMember(otherGroup, mCharA, 0), GroupChatMember(otherGroup, mCharB, 1)),
        )
        val otherMessageId = groupMessage("other group", otherGroup)
        val otherSource = groupDerived("GroupMessage", otherMessageId)
        val other = mMemory.promote(otherSource.target, otherSource.ref, 1, mCharA)
        mGroupChat.removeMember(mGroup, mCharA)
        assertEquals(
            "PromotedSnapshot",
            mMemory.history(departing.target, departing.ref).single().sources.single().sourceKind
        )
        assertEquals(
            messageId,
            mMemory.history(remaining.target, remaining.ref)
                .single().sources.single().groupMessageId
        )
        assertEquals(
            otherMessageId,
            mMemory.history(other.target, other.ref).single().sources.single().groupMessageId
        )
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 成员删除失败时证据快照也必须回滚，不能留下半次离群操作。 */
    @Test
    fun memberRemovalRollsBackEvidenceSnapshotsOnFailure() = runBlocking {
        allowGroupMemberRemoval()
        val source = groupDerived("GroupMessage", groupMessage("evidence"))
        val promoted = mMemory.promote(source.target, source.ref, 1, mCharA)
        val before = mMemory.history(promoted.target, promoted.ref).single().sources
        // 在证据冻结后的成员删除阶段注入失败，验证整个外层事务的回滚边界。
        mDatabase.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_member_removal BEFORE DELETE ON group_chat_members WHEN OLD.sessionId = $mGroup AND OLD.characterId = $mCharA BEGIN SELECT RAISE(ABORT, 'fixture'); END",
        )
        assertTrue(runCatching { mGroupChat.removeMember(mGroup, mCharA) }.isFailure)
        assertTrue(
            mDatabase.getGroupChatMemberDao().getMembers(mGroup).any { it.characterId == mCharA })
        assertEquals(before, mMemory.history(promoted.target, promoted.ref).single().sources)
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 较早分支按真实消息前缀选择旧正文和旧可见范围，不能复制未来修订。 */
    @Test
    fun branchesUseRevisionAtCopiedBoundaryAndPreserveScopeSnapshot() = runBlocking {
        // 在较早消息后追加未来消息与范围变更，再从第一条消息创建会话分支。
        val firstMessage = message("first")
        val target = MemoryTarget.Chat(mSession)
        val original = mMemory.save(target, MemoryDraft("early fact"))
        message("future")
        val changed = mMemory.save(target, MemoryDraft("future fact"), original.ref, 1)
        mMemory.changeScope(target, changed.ref, 2, 1, mCharA)
        mMemory.save(target, MemoryDraft("future only"))
        val branch = mChat.createBranchSession(mSession, firstMessage, "Earlier")
        // 验证新分支仅保留分支点时的旧正文与原始不限人设/共享范围。
        val copied = mDatabase.getChatMemoryDao().list(branch)
        assertEquals(listOf("early fact"), copied.map { it.fields.content.body })
        assertNull(copied.single().fields.personaId)
        assertNull(copied.single().privateCharacterId)
        assertEquals(1L, copied.single().fields.currentRevision)
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 导出仅附带当前条目及历史修订引用的人设，避免泄露其他角色的身份。 */
    @Test
    fun exportPackageIncludesOnlyCurrentAndHistoricalPersonas() = runBlocking {
        // 其他角色持有私密人设时，不限人设的记忆包仍应省略全部人设名称与 ID。
        val unrelatedPersona = mDatabase.getUserPersonaDao().insert(UserPersona(name = "Unrelated"))
        mMemory.save(
            MemoryTarget.Character(mCharB),
            MemoryDraft("private fact", personaId = unrelatedPersona),
        )
        val target = MemoryTarget.Character(mCharA)
        mMemory.save(target, MemoryDraft("shared fact"))
        assertTrue(MemoryPackageCodec.decode(mMemory.exportPackage(target)).personas.isEmpty())

        // 更换人设后保留历史人设的名称，既支持显式导入映射，也排除未引用身份。
        val historicalPersona =
            mDatabase.getUserPersonaDao().insert(UserPersona(name = "Historical"))
        val currentPersona = mDatabase.getUserPersonaDao().insert(UserPersona(name = "Current"))
        val scoped =
            mMemory.save(target, MemoryDraft("personal fact", personaId = historicalPersona))
        mMemory.changeScope(target, scoped.ref, 1, currentPersona, null)
        val exported = MemoryPackageCodec.decode(mMemory.exportPackage(target))
        assertEquals(
            mapOf(historicalPersona to "Historical", currentPersona to "Current"),
            exported.personas,
        )
    }

    /** 单聊中途导入的全部修订只从导入边界生效，较早分支不能看到它们。 */
    @Test
    fun importedChatHistoryStartsAtImportBoundary() = runBlocking {
        // 便携包包含多版历史，原始消息边界不能决定本机会话中的生效时间。
        val target = MemoryTarget.Chat(mSession)
        val entry = mMemory.save(target, MemoryDraft("old imported fact"))
        mMemory.save(target, MemoryDraft("current imported fact"), entry.ref, 1)
        val exported = MemoryPackageCodec.decode(mMemory.exportPackage(target))
        mMemory.delete(target, entry.ref, 2)
        val firstMessage = message("early")
        val importBoundary = message("current")
        assertEquals(1, mMemory.importPackage(target, exported, emptyMap(), emptyMap()))
        val imported = mDatabase.getChatMemoryDao().list(mSession).single()
        assertEquals(
            listOf(importBoundary, importBoundary),
            mMemory.history(target, MemoryRef(MemoryTable.Chat, imported.id))
                .map { it.effectiveAfterMessageId },
        )

        // 从导入前的消息分支时不带入记忆，从导入边界分支时保留最新正文。
        val earlyBranch = mChat.createBranchSession(mSession, firstMessage, "Earlier")
        assertTrue(mDatabase.getChatMemoryDao().list(earlyBranch).isEmpty())
        val currentBranch = mChat.createBranchSession(mSession, importBoundary, "Current")
        assertEquals(
            listOf("current imported fact"),
            mDatabase.getChatMemoryDao().list(currentBranch).map { it.fields.content.body },
        )
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 群聊导入同样使用本机消息边界，不能把外部历史复制到更早的群聊分支。 */
    @Test
    fun importedGroupHistoryStartsAtImportBoundary() = runBlocking {
        // 群聊便携包保留完整历史，但所有版本都应从本次导入时才开始生效。
        val target = MemoryTarget.Group(mGroup)
        val entry = mMemory.save(target, MemoryDraft("old imported fact"))
        mMemory.save(target, MemoryDraft("current imported fact"), entry.ref, 1)
        val exported = MemoryPackageCodec.decode(mMemory.exportPackage(target))
        mMemory.delete(target, entry.ref, 2)
        val firstMessage = groupMessage("early")
        val importBoundary = groupMessage("current")
        assertEquals(1, mMemory.importPackage(target, exported, emptyMap(), emptyMap()))
        val imported = mDatabase.getGroupChatMemoryDao().list(mGroup).single()
        assertEquals(
            listOf(importBoundary, importBoundary),
            mMemory.history(target, MemoryRef(MemoryTable.Group, imported.id))
                .map { it.effectiveAfterMessageId },
        )

        // 同时验证导入前后的分支，防止修复时错误丢失本应保留的导入记忆。
        val earlyBranch = mGroupChat.createBranchSession(mGroup, firstMessage, "Earlier")
        assertTrue(mDatabase.getGroupChatMemoryDao().list(earlyBranch).isEmpty())
        val currentBranch = mGroupChat.createBranchSession(mGroup, importBoundary, "Current")
        assertEquals(
            listOf("current imported fact"),
            mDatabase.getGroupChatMemoryDao().list(currentBranch).map { it.fields.content.body },
        )
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 独立包按显式目标及人设映射，重复导入幂等，损坏快照整包回滚。 */
    @Test
    fun portablePackageMapsIdentitiesAndRejectsConflictingUuid() = runBlocking {
        // 导出后导入新角色、验证同目标幂等以及跨目标同 UUID 冲突拦截。
        val entry = mMemory.save(
            MemoryTarget.Character(mCharA),
            MemoryDraft("portable", personaId = 1),
        )
        val exported = MemoryPackageCodec.decode(mMemory.exportPackage(entry.target))
        mMemory.delete(entry.target, entry.ref, 1)
        assertEquals(
            1,
            mMemory.importPackage(
                MemoryTarget.Character(mCharB),
                exported,
                mapOf(1L to 1L),
                emptyMap(),
            ),
        )
        assertEquals(
            0,
            mMemory.importPackage(
                MemoryTarget.Character(mCharB),
                exported,
                mapOf(1L to 1L),
                emptyMap(),
            ),
        )
        assertTrue(
            runCatching {
                mMemory.importPackage(
                    MemoryTarget.Character(mCharA),
                    exported,
                    mapOf(1L to 1L),
                    emptyMap(),
                )
            }.exceptionOrNull() is MemoryConflictException,
        )
        // 篡改主表与修订不一致的快照时整包回滚且不留半成品。
        val invalid = exported.copy(
            entries = exported.entries.map {
                it.copy(
                    fields = it.fields.copy(
                        content = it.fields.content.copy(body = "mismatch"),
                    ),
                )
            },
        )
        assertTrue(
            runCatching {
                mMemory.importPackage(
                    MemoryTarget.Character(mCharA),
                    invalid,
                    mapOf(1L to 1L),
                    emptyMap(),
                )
            }.isFailure,
        )
        assertTrue(mDatabase.getCharacterMemoryDao().list(mCharA).isEmpty())
        MemoryRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 会话旧草稿不能覆盖新开关，恢复校验必须拒绝没有匹配修订的主表。 */
    @Test
    fun settingsArePartialAndRestoreRejectsTamperedSnapshot() = runBlocking {
        // 先单独更新会话记忆开关，再保存旧会话草稿确认不会回写开关字段。
        val draft = requireNotNull(mDatabase.getChatSessionDao().getSessionById(mSession))
        mMemory.updateSettings(MemoryTarget.Chat(mSession), inherit = false, recall = true)
        mChat.updateSession(draft.copy(title = "Renamed"))
        val saved = requireNotNull(mDatabase.getChatSessionDao().getSessionById(mSession))
        assertFalse(saved.inheritCharacterMemory)
        assertTrue(saved.recallMemoryInNormalChat)
        // 验证数据库触发器拦截非法 slotKey，且恢复校验器拒绝主表正文被篡改的库。
        val entry = mMemory.save(MemoryTarget.Character(mCharA), MemoryDraft("safe"))
        val sql = mDatabase.openHelper.writableDatabase
        assertTrue(
            runCatching {
                sql.execSQL(
                    "UPDATE character_memories SET slotKey = 'wrong' WHERE id = ?",
                    arrayOf(entry.ref.id),
                )
            }.isFailure,
        )
        sql.execSQL(
            "UPDATE character_memories SET body = 'tampered' WHERE id = ?",
            arrayOf(entry.ref.id),
        )
        assertTrue(runCatching { MemoryRestoreValidator.validate(sql) }.isFailure)
    }

    private fun character(name: String) = Character(
        name = name,
        avatar = "",
        characterTags = "[]",
        description = "",
        personality = "",
        scenario = "",
        firstMessages = "",
        examplesOfDialogue = "",
        postHistoryInstructions = "",
    )

    private suspend fun message(body: String) = mDatabase.getChatMessageDao().insertOrReplace(
        ChatMessage(
            sessionId = mSession,
            createTime = System.currentTimeMillis(),
            source = ChatMessage.Source.User,
            content = body,
        ),
    )

    private suspend fun groupMessage(body: String, sessionId: Long = mGroup) =
        mDatabase.getGroupChatMessageDao().insertOrReplace(
            GroupChatMessage(
                sessionId = sessionId,
                createTime = System.currentTimeMillis(),
                source = GroupChatMessage.Source.User,
                content = body,
                speakerNameSnapshot = "User",
            ),
        )

    /** 补足移除入口要求的三名成员，不改变其他测试的两成员基线。 */
    private suspend fun allowGroupMemberRemoval() {
        val third = mDatabase.getCharacterDao().insertOrReplace(character("Third"))
        mGroupChat.addMembers(mGroup, listOf(third))
    }

    /** 用真实群消息或摘要替换用户证据，模拟提取后再提升范围的来源链。 */
    private suspend fun groupDerived(
        sourceKind: String,
        sourceId: Long,
        privateId: Long? = null
    ): MemoryEntry {
        val (sessionId, body) = when (sourceKind) {
            "GroupMessage" -> requireNotNull(
                mDatabase.getGroupChatMessageDao().getMessageById(sourceId)
            ).let { it.sessionId to it.content }

            "GroupSummary" -> requireNotNull(
                mDatabase.getGroupChatSummaryDao().getById(sourceId)
            ).let { it.sessionId to it.content }

            else -> error("Unsupported group evidence fixture")
        }
        // 建立标准主表及修订，再移除用户独立确认，保证证据完全来自群内历史。
        val entry = mMemory.save(
            MemoryTarget.Group(sessionId),
            MemoryDraft("$sourceKind fact", privateCharacterId = privateId)
        )
        val revision = mDatabase.getGroupChatMemoryDao().revisions(entry.ref.id).single()
        mDatabase.openHelper.writableDatabase.execSQL(
            "DELETE FROM group_chat_memory_sources WHERE revisionId = ?",
            arrayOf(revision.id)
        )
        // 消息与摘要保留不同外键，恢复校验需分别验证各自的会话归属及指纹。
        mDatabase.getGroupChatMemoryDao().insertSources(
            listOf(
                GroupChatMemorySource(
                    fields = MemorySourceFields(
                        revisionId = revision.id,
                        sourceKind = sourceKind,
                        confidence = "Observed",
                        sourceKey = "$sourceKind/$sourceId",
                        groupMessageId = sourceId.takeIf { sourceKind == "GroupMessage" },
                        groupSummaryId = sourceId.takeIf { sourceKind == "GroupSummary" },
                        fingerprint = MemoryRestoreValidator.fingerprint(body),
                        quote = body,
                        createdAt = 1,
                    ),
                )
            )
        )
        return entry
    }

    /** 构造未来提取入口也会产生的消息来源，测试不依赖尚未接入的 Agent。 */
    private suspend fun derived(messageId: Long, body: String): MemoryEntry {
        // 先通过仓库创建标准条目，再将首版用户来源替换为指向目标消息的派生来源。
        val entry = mMemory.save(MemoryTarget.Character(mCharA), MemoryDraft(body))
        val revision = mDatabase.getCharacterMemoryDao().revisions(entry.ref.id).single()
        mDatabase.openHelper.writableDatabase.execSQL(
            "DELETE FROM character_memory_sources WHERE revisionId = ?",
            arrayOf(revision.id),
        )
        // 读取消息正文并计算指纹后写入观测来源记录。
        val message = requireNotNull(mDatabase.getChatMessageDao().getMessageById(messageId))
        mDatabase.getCharacterMemoryDao().insertSources(
            listOf(
                CharacterMemorySource(
                    fields = MemorySourceFields(
                        revisionId = revision.id,
                        sourceKind = "ChatMessage",
                        confidence = "Observed",
                        sourceKey = "chat/$messageId",
                        chatMessageId = messageId,
                        fingerprint = MemoryRestoreValidator.fingerprint(message.content),
                        quote = message.content,
                        createdAt = 1,
                    ),
                ),
            ),
        )
        return entry
    }
}
