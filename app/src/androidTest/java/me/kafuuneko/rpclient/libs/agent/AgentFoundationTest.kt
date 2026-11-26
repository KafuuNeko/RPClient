package me.kafuuneko.rpclient.libs.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.AgentRestoreValidator
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState
import me.kafuuneko.rpclient.libs.agent.runtime.FileAgentCheckpointStore
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolCallRequest
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutor
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.PromptPreset
import me.kafuuneko.rpclient.libs.room.migration.AgentDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.migration.MemoryDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.repository.AgentAuthorizationException
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.libs.room.withTestPersonas
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真实 Room 与检查点文件验证授权、并发撤权、幂等和消息生命周期。 */
@RunWith(AndroidJUnit4::class)
class AgentFoundationTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var mDatabase: AppDatabase
    private lateinit var mRuns: AgentRunRepository
    private lateinit var mScenes: SceneStateRepository
    private lateinit var mChats: ChatRepository
    private lateinit var mGroups: GroupChatRepository
    private lateinit var mStore: FileAgentCheckpointStore
    private lateinit var mDirectory: File
    private lateinit var mSingle: ConversationRef.Single
    private lateinit var mGroup: ConversationRef.Group
    private var mCharacterId = 0L

    /** 创建隔离数据库、真实角色及两种会话，不依赖模型服务。 */
    @Before
    fun setup(): Unit = runBlocking {
        mDatabase = Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java)
            .withTestPersonas().addCallback(MemoryDatabaseConstraints)
            .addCallback(AgentDatabaseConstraints).build()
        mRuns = AgentRunRepository(mDatabase)
        mScenes = SceneStateRepository(mDatabase, mRuns)
        mDirectory =
            File(mContext.cacheDir, "agent-foundation-${System.nanoTime()}").apply { mkdirs() }
        mStore = FileAgentCheckpointStore(mDirectory)
        val files = FileRepository(mContext, mDatabase)
        val images = MessageImageRepository(mDatabase, files)
        val personas = UserPersonaRepository(mDatabase, files)
        val memory = MemoryRepository(mDatabase)
        mChats = ChatRepository(mDatabase, Gson(), images, personas, memory, mScenes)
        mGroups = GroupChatRepository(mDatabase, Gson(), images, personas, memory, mScenes)
        // 相同发言角色同时参与单聊和群聊，数字 ID 冲突也不能共享配置。
        mCharacterId = mDatabase.getCharacterDao().insertOrReplace(character("Alice"))
        val other = mDatabase.getCharacterDao().insertOrReplace(character("Bob"))
        mSingle = ConversationRef.Single(
            mChats.createSession(
                characterId = mCharacterId,
                title = "Single",
                userNote = "",
                lorebookEntryIds = emptyList(),
                userPersonaId = 1
            )
        )
        mGroup = ConversationRef.Group(
            mGroups.createSession(
                title = "Group",
                characterIds = listOf(mCharacterId, other),
                activationStrategy = GroupChatSession.ActivationStrategy.Manual,
                allowSelfResponses = false,
                userPersonaId = 1
            )
        )
    }

    /** 只清理本测试创建的数据库和文件。 */
    @After
    fun close() {
        mDatabase.close()
        mDirectory.deleteRecursively()
    }

    /** 旧设置草稿不能覆盖 Agent 字段，删除绑定预设后外键回退为跟随全局。 */
    @Test
    fun settingsAreIndependentAndStaleFormsCannotOverwriteAgentFields(): Unit = runBlocking {
        // 持有旧页面草稿时并发开启 Agent，再保存普通字段。
        val draft = requireNotNull(mChats.getSessionById(mSingle.id))
        val preset = mDatabase.getPromptPresetDao()
            .insertPreset(PromptPreset(name = "Agent", createdAt = 1, updatedAt = 1))
        mRuns.saveSettings(mSingle, true, preset)
        mChats.updateSession(draft.copy(title = "Edited"))
        val latest = requireNotNull(mChats.getSessionById(mSingle.id))
        assertTrue(latest.agentEnabled)
        assertEquals(1L, latest.agentSettingsVersion)
        assertEquals(preset, latest.agentPromptPresetId)
        assertFalse(requireNotNull(mGroups.getSessionById(mGroup.id)).agentEnabled)
        mDatabase.openHelper.writableDatabase.execSQL(
            "DELETE FROM prompt_presets WHERE id = ?",
            arrayOf(preset)
        )
        assertNull(requireNotNull(mChats.getSessionById(mSingle.id)).agentPromptPresetId)
    }

    /** 普通历史写入立即失效旧令牌，另一个身份域的运行不受影响。 */
    @Test
    fun historyAndExecutionEpochRejectLateCommits(): Unit = runBlocking {
        // 单聊历史变化只撤销该身份域的提交资格。
        enable(mSingle)
        enable(mGroup)
        val old = mRuns.start(mSingle, mCharacterId, 1)
        val group = mRuns.start(mGroup, mCharacterId, 1)
        mDatabase.getChatMessageDao().insertOrReplace(
            ChatMessage(
                sessionId = mSingle.id,
                createTime = 1,
                source = ChatMessage.Source.User,
                content = "new history"
            )
        )
        assertThrows(AgentAuthorizationException::class.java) {
            runBlocking {
                mRuns.commitAuthorized(
                    old
                ) { error("Must not commit") }
            }
        }
        mRuns.commitAuthorized(group) { }
        mRuns.revoke(mSingle)
        val fresh = mRuns.start(mSingle, mCharacterId, 1)
        assertTrue(fresh.executionEpoch > old.executionEpoch)
        assertThrows(AgentAuthorizationException::class.java) {
            runBlocking {
                mRuns.commitAuthorized(
                    old
                ) { }
            }
        }
    }

    /** 本地写入完成后回放原结果，不按远程 callId 去重，也不再次提交。 */
    @Test
    fun localWriteAndCompletionMarkerAreAtomicAndReplayable(): Unit = runBlocking {
        // 同一客户端调用重试必须回放，远程关联 ID 重复不能合并新调用。
        var commits = 0
        val tool = tool(ToolEffect.LocalWrite) {
            ToolAction.LocalWrite(success(ToolWriteDisposition.Committed)) {
                mDatabase.getChatSessionDao().updateSessionUserNote(mSingle.id, "committed")
                commits++
            }
        }
        val registry = ToolRegistry(listOf(tool))
        val executor = ToolExecutor(registry, mRuns, mStore)
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val step = mRuns.appendStep(token, "Tools")
        val call = call(registry, step, 0)
        val first = executor.execute(token, call)
        assertEquals(first, executor.execute(token, call))
        assertEquals(1, commits)
        assertEquals("committed", requireNotNull(mChats.getSessionById(mSingle.id)).userNote)
        assertTrue(executor.execute(token, call.copy(ordinal = 1)) is ToolResult.Success)
        assertEquals(2, commits)
        AgentRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** prepare 不持有数据库锁，关闭设置可即时撤权并阻断迟到本地变更。 */
    @Test
    fun revocationDuringPreparationPreventsLocalWrite(): Unit = runBlocking {
        // 准备阶段故意挂起，验证关闭设置无需等待工具完成。
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val tool = tool(ToolEffect.LocalWrite) {
            entered.complete(Unit)
            release.await()
            ToolAction.LocalWrite(success(ToolWriteDisposition.Committed)) {
                mDatabase.getChatSessionDao().updateSessionUserNote(mSingle.id, "late")
            }
        }
        val registry = ToolRegistry(listOf(tool))
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val call = call(registry, mRuns.appendStep(token, "Tools"), 0)
        val job = async { ToolExecutor(registry, mRuns, mStore).execute(token, call) }
        entered.await()
        mRuns.saveSettings(mSingle, false, null)
        release.complete(Unit)
        assertEquals(ToolResult.FatalError("tool_authorization_revoked"), job.await())
        assertEquals("", requireNotNull(mChats.getSessionById(mSingle.id)).userNote)
    }

    /** 随机读取被取消后只留下结果未知，重试不能再次产生随机结果。 */
    @Test
    fun cancellationNeverRerunsNonDeterministicRead(): Unit = runBlocking {
        // 在产生随机结果后取消，后续重试不得再执行一次。
        var executions = 0
        val entered = CompletableDeferred<Unit>()
        val block = CompletableDeferred<Unit>()
        val tool = tool(ToolEffect.NonDeterministicRead) {
            ToolAction.Read {
                executions++
                entered.complete(Unit)
                block.await()
                success()
            }
        }
        val registry = ToolRegistry(listOf(tool))
        val executor = ToolExecutor(registry, mRuns, mStore)
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val call = call(registry, mRuns.appendStep(token, "Tools"), 0)
        val job = async { executor.execute(token, call) }
        entered.await()
        job.cancelAndJoin()
        assertEquals(ToolResult.FatalError("tool_outcome_unknown"), executor.execute(token, call))
        assertEquals(1, executions)
        val id = AgentJson.digest("${token.runId}/${call.stepId}/0")
        assertEquals(
            ToolInvocationState.UnknownOutcome,
            mDatabase.getAgentRunDao().invocation(id)?.state
        )
    }

    /** 外部写入必须先持久化等待态；确认只绑定当前调用，不重复发送。 */
    @Test
    fun externalWriteWaitsForDurableUserConfirmation(): Unit = runBlocking {
        // 等待态先写 Room 与检查点，确认之前不产生外部副作用。
        var sends = 0
        val tool = tool(ToolEffect.ExternalWrite) {
            ToolAction.ExternalWrite(
                "example.invalid",
                JsonObject()
            ) { sends++; success() }
        }
        val registry = ToolRegistry(listOf(tool))
        val executor = ToolExecutor(registry, mRuns, mStore)
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val call = call(registry, mRuns.appendStep(token, "Tools"), 0)
        val waiting = executor.execute(token, call) as ToolResult.AwaitingUser
        assertEquals(ToolUserRequestKind.ExternalWriteConfirmation, waiting.kind)
        assertEquals(0, sends)
        assertEquals(
            AgentRunState.WaitingForUser,
            mDatabase.getAgentRunDao().run(token.runId)?.state
        )
        mRuns.confirmInvocation(token, AgentJson.digest("${token.runId}/${call.stepId}/0"))
        assertTrue(executor.execute(token, call) is ToolResult.Success)
        assertTrue(executor.execute(token, call) is ToolResult.Success)
        assertEquals(1, sends)
    }

    /** 记忆只读策略和未知参数应在处理器准备阶段之前拒绝。 */
    @Test
    fun memoryReadOnlyAndSchemaRejectExecution(): Unit = runBlocking {
        // 权限和结构错误均不得进入本地写入处理器。
        var preparations = 0
        val tool = object : AgentTool {
            override val definition = definition(ToolEffect.LocalWrite).copy(writesMemory = true)
            override suspend fun prepare(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolAction {
                preparations++
                return ToolAction.LocalWrite(success(ToolWriteDisposition.Proposed)) { error("Must not commit") }
            }
        }
        val registry = ToolRegistry(listOf(tool))
        val policy =
            mRuns.policy(mSingle).copy(enabledToolIdsJson = "[\"test\"]", memoryReadOnly = true)
        mRuns.saveSettings(mSingle, true, null, policy)
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val call = call(registry, mRuns.appendStep(token, "Tools"), 0)
        val executor = ToolExecutor(registry, mRuns, mStore)
        assertEquals(
            ToolResult.FatalError("tool_authorization_revoked"),
            executor.execute(token, call)
        )
        assertEquals(
            ToolResult.RecoverableError("invalid_tool_arguments"),
            executor.execute(token, call.copy(argumentsJson = "{\"extra\":1}"))
        )
        assertEquals(0, preparations)
    }

    /** 分支与截断必须使用生效边界的快照，不能继承源会话未来剧情。 */
    @Test
    fun groupBranchesAndTruncationPreserveSceneBoundaries(): Unit = runBlocking {
        // 两版剧情分别绑定已提交消息，分支不能读到第二版。
        enable(mGroup, "test")
        val first = groupMessage(1)
        val second = groupMessage(2)
        mScenes.update(mGroup, 0, location("tower"), first)
        mScenes.update(mGroup, 1, location("harbor"), second)
        val branch = ConversationRef.Group(mGroups.createBranchSession(mGroup.id, first, "Branch"))
        assertTrue(requireNotNull(mGroups.getSessionById(branch.id)).agentEnabled)
        assertEquals("tower", sceneLocation(branch))
        assertEquals("[\"test\"]", mRuns.policy(branch).enabledToolIdsJson)
        mGroups.deleteMessagesFrom(second)
        assertEquals("tower", sceneLocation(mGroup))
        assertTrue(mScenes.revisions(mGroup).none { it.effectiveAfterMessageId == second })
        AgentRestoreValidator.validate(mDatabase.openHelper.writableDatabase)
    }

    /** 整库恢复保留配置和剧情，但不能续接没有导出的检查点。 */
    @Test
    fun restoreRevokesRuntimeReferencesAndValidatesOwners(): Unit = runBlocking {
        // 恢复只保留权威配置和剧情，不保留旧执行资格。
        enable(mSingle, "test")
        mScenes.update(mSingle, 0, location("tower"), 0)
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val db = mDatabase.openHelper.writableDatabase
        AgentRestoreValidator.prepareForRestore(db)
        assertEquals(AgentRunState.Interrupted, mDatabase.getAgentRunDao().run(token.runId)?.state)
        assertNull(mDatabase.getAgentRunDao().activeRun(mSingle.key))
        assertEquals("tower", sceneLocation(mSingle))
        assertEquals("[\"test\"]", mRuns.policy(mSingle).enabledToolIdsJson)
        assertThrows(Exception::class.java) {
            db.execSQL(
                "UPDATE conversation_tool_policies SET conversationKey = 'group/999' WHERE conversationKey = ?",
                arrayOf(mSingle.key)
            )
        }
    }

    /** 普通提问可回放，但不能借“确认”入口授权外部写入，过期后必须释放归属。 */
    @Test
    fun inputWaitingReplaysWithoutBecomingWriteAuthorization(): Unit = runBlocking {
        var preparations = 0
        val tool = tool(ToolEffect.ReadOnly) {
            preparations++
            ToolAction.AwaitingUser(
                ToolResult.AwaitingUser(
                    "Choose a route",
                    listOf("north", "south")
                )
            )
        }
        val registry = ToolRegistry(listOf(tool))
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val call = call(registry, mRuns.appendStep(token, "Tools"), 0)
        val first = ToolExecutor(registry, mRuns, mStore).execute(token, call)
        // 重建仓库与文件存储后只回放等待问题，不重新执行处理器。
        val restarted = ToolExecutor(
            registry,
            AgentRunRepository(mDatabase),
            FileAgentCheckpointStore(mDirectory)
        )
        assertEquals(first, restarted.execute(token, call))
        assertEquals(1, preparations)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                mRuns.confirmInvocation(token, AgentJson.digest("${token.runId}/${call.stepId}/0"))
            }
        }
        mRuns.recoverInterrupted(System.currentTimeMillis() + 86_400_001)
        assertEquals(AgentRunState.Expired, mDatabase.getAgentRunDao().run(token.runId)?.state)
        assertNull(mDatabase.getAgentRunDao().activeRun(mSingle.key))
    }

    /** 写入中途异常必须同时回滚业务数据和完成标记，重试也不得二次执行。 */
    @Test
    fun failedLocalCommitRollsBackBusinessData(): Unit = runBlocking {
        var attempts = 0
        val tool = tool(ToolEffect.LocalWrite) {
            ToolAction.LocalWrite(success(ToolWriteDisposition.Committed)) {
                attempts++
                mDatabase.getChatSessionDao().updateSessionUserNote(mSingle.id, "partial")
                error("Simulated local commit failure")
            }
        }
        val registry = ToolRegistry(listOf(tool))
        val executor = ToolExecutor(registry, mRuns, mStore)
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val call = call(registry, mRuns.appendStep(token, "Tools"), 0)
        // 结果文件已发布并不能替代事务内的完成标记。
        assertEquals(ToolResult.FatalError("tool_execution_failed"), executor.execute(token, call))
        assertEquals("", requireNotNull(mChats.getSessionById(mSingle.id)).userNote)
        assertEquals(ToolResult.FatalError("tool_outcome_unknown"), executor.execute(token, call))
        assertEquals(1, attempts)
    }

    /** 同轮只允许最多两个显式独立的确定性读取并发，结果保持原序。 */
    @Test
    fun independentReadsUseBoundedConcurrency(): Unit = runBlocking {
        var active = 0
        var peak = 0
        var started = 0
        val ready = CompletableDeferred<Unit>()
        val tool = object : AgentTool {
            override val definition = definition(ToolEffect.ReadOnly).copy(parallelSafe = true)
            override suspend fun prepare(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolAction = ToolAction.Read {
                active++
                peak = maxOf(peak, active)
                started++
                if (started == 2) ready.complete(Unit)
                if (started <= 2) ready.await()
                delay(10)
                active--
                success()
            }
        }
        val registry = ToolRegistry(listOf(tool))
        enable(mSingle, "test")
        val token = mRuns.start(mSingle, mCharacterId, 1)
        val step = mRuns.appendStep(token, "Tools")
        // 两个读取之间的数据库准入不能跨读取过程持锁。
        val calls = (0..2).map { call(registry, step, it).copy(independent = true) }
        assertTrue(
            ToolExecutor(registry, mRuns, mStore).executeBatch(token, calls)
                .all { it is ToolResult.Success })
        assertEquals(2, peak)
        assertEquals(3, mDatabase.getAgentRunDao().run(token.runId)?.toolCalls)
    }

    private suspend fun enable(conversation: ConversationRef, toolId: String? = null) {
        val policy = mRuns.policy(conversation)
            .copy(enabledToolIdsJson = toolId?.let { "[\"$it\"]" } ?: "[]")
        mRuns.saveSettings(conversation, true, null, policy)
    }

    private fun definition(effect: ToolEffect) =
        AgentToolDefinition("test", "test", 1, "Test", ToolSchema(ToolSchema.Type.Object), effect)

    private fun tool(effect: ToolEffect, prepare: suspend () -> ToolAction): AgentTool =
        object : AgentTool {
            override val definition = definition(effect)
            override suspend fun prepare(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolAction = prepare()
        }

    private fun success(disposition: ToolWriteDisposition? = null) =
        ToolResult.Success(JsonObject().apply { addProperty("ok", true) }, disposition)

    private fun call(registry: ToolRegistry, step: Long, ordinal: Int) = ToolCallRequest(
        step,
        ordinal,
        "test",
        1,
        requireNotNull(registry.find("test")).digest,
        "{}",
        "same-remote-id"
    )

    private fun location(value: String) =
        listOf(SceneStateChange(listOf("location"), JsonPrimitive(value)))

    private suspend fun sceneLocation(conversation: ConversationRef) =
        AgentJson.parse(mScenes.get(conversation).stateJson).asJsonObject["location"].asString

    private suspend fun groupMessage(time: Long) = mDatabase.getGroupChatMessageDao()
        .insertOrReplace(
            GroupChatMessage(
                sessionId = mGroup.id,
                createTime = time,
                source = GroupChatMessage.Source.User,
                content = "event $time",
                speakerNameSnapshot = "User"
            )
        )

    private fun character(name: String) = Character(
        name = name,
        avatar = "",
        characterTags = "[]",
        description = "",
        personality = "",
        scenario = "",
        firstMessages = "[]",
        examplesOfDialogue = "",
        postHistoryInstructions = ""
    )
}
