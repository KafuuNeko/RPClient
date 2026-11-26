package me.kafuuneko.rpclient.libs.agent

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.createAppModules
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.runtime.AgentApplicationScope
import me.kafuuneko.rpclient.libs.agent.runtime.AgentCheckpointCodec
import me.kafuuneko.rpclient.libs.agent.runtime.AgentCheckpointStore
import me.kafuuneko.rpclient.libs.agent.runtime.AgentPromptComposer
import me.kafuuneko.rpclient.libs.agent.runtime.AgentRunManager
import me.kafuuneko.rpclient.libs.agent.runtime.AgentRuntime
import me.kafuuneko.rpclient.libs.agent.runtime.AgentTurnInput
import me.kafuuneko.rpclient.libs.agent.runtime.AgentWaitingForUser
import me.kafuuneko.rpclient.libs.agent.runtime.ConversationGenerationActivityRegistry
import me.kafuuneko.rpclient.libs.agent.runtime.FileAgentCheckpointStore
import me.kafuuneko.rpclient.libs.agent.runtime.GroupChatAgentRuntime
import me.kafuuneko.rpclient.libs.agent.tool.ToolCallRequest
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutor
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.llm.ToolCallingCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.libs.prompt.request.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.migration.AgentDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.migration.MemoryDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository
import me.kafuuneko.rpclient.libs.room.withTestPersonas
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** 真实依赖图、Room、文件与 HTTP 验证运行闭环及等待恢复，避免只验证模拟调用次数。 */
@RunWith(AndroidJUnit4::class)
class AgentRuntimeIntegrationTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var mDatabase: AppDatabase
    private lateinit var mLogs: RequestLogDatabase
    private lateinit var mApplication: KoinApplication
    private lateinit var mDirectory: File
    private lateinit var mRuns: AgentRunRepository
    private lateinit var mChats: ChatRepository
    private lateinit var mGroups: GroupChatRepository
    private lateinit var mManager: AgentRunManager
    private lateinit var mConversation: ConversationRef.Single
    private var mCharacterId = 0L
    private var mOtherId = 0L
    private var mOldProvider = 0L
    private var mOldDebug = false

    @Before
    fun setup(): Unit = runBlocking {
        mOldProvider = AppModel.currentLLMProvider
        mOldDebug = AppModel.debugModeEnabled
        AppModel.debugModeEnabled = false
        mDatabase =
            Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java).withTestPersonas()
                .addCallback(MemoryDatabaseConstraints).addCallback(AgentDatabaseConstraints)
                .build()
        mLogs = Room.inMemoryDatabaseBuilder(mContext, RequestLogDatabase::class.java).build()
        mDirectory = File(mContext.cacheDir, "agent-runtime-${System.nanoTime()}")
        mApplication = koinApplication {
            androidContext(mContext)
            modules(createAppModules(), module {
                single<AppDatabase> { mDatabase }
                single<RequestLogDatabase> { mLogs }
                single<AgentCheckpointStore> { FileAgentCheckpointStore(mDirectory) }
            })
        }
        mRuns = get()
        mChats = get()
        mGroups = get()
        mManager = get()
        mCharacterId = mDatabase.getCharacterDao().insertOrReplace(character("Alice"))
        mOtherId = mDatabase.getCharacterDao().insertOrReplace(character("Bob"))
        mConversation = ConversationRef.Single(
            mChats.createSession(
                characterId = mCharacterId,
                title = "Single",
                userNote = "",
                lorebookEntryIds = emptyList(),
                userPersonaId = 1
            )
        )
        mRuns.saveSettings(mConversation, true, null)
    }

    @After
    fun close(): Unit = runBlocking {
        mManager.stopAll()
        get<AgentApplicationScope>().scope.cancel()
        get<OkHttpClient>().dispatcher.cancelAll()
        mApplication.close()
        mDatabase.close()
        mLogs.close()
        mDirectory.deleteRecursively()
        AppModel.currentLLMProvider = mOldProvider
        AppModel.debugModeEnabled = mOldDebug
    }

    @Test
    fun questionSurvivesColdManagerAndResumesTheSameInvocation(): Unit = runBlocking {
        AgentHttpServer { index, _ ->
            AgentHttpServer.Reply(
                if (index == 1) call(
                    "ask_user",
                    jsonObject(
                        "question" to "Which road?",
                        "options" to JsonArray().apply {
                            add("North")
                            add("South")
                        })
                ) else text("Takes the northern road.")
            )
        }.use { server ->
            val input = input(provider(server))
            try {
                mManager.run(input)
                error("Expected waiting")
            } catch (_: AgentWaitingForUser) {
            }
            val waiting = requireNotNull(mRuns.active(mConversation))
            assertEquals(AgentRunState.WaitingForUser, waiting.state)
            assertEquals("Which road?", mManager.waiting(mConversation)?.question)
            assertFalse(get<ConversationGenerationActivityRegistry>().isBusy(mConversation))
            val scope = AgentApplicationScope()
            val cold = AgentRunManager(
                mRuns,
                get<AgentRuntime>(),
                get<AgentPromptComposer>(),
                get<AgentCheckpointStore>(),
                get<AgentCheckpointCodec>(),
                get<LLMRepository>(),
                mChats,
                mGroups,
                get<ToolCallingCapabilityResolver>(),
                get<ConversationGenerationActivityRegistry>(),
                scope
            )
            try {
                cold.resume(mConversation, "North", false)
                assertEquals(AgentRunState.Completed, mRuns.get(waiting.id)?.state)
                assertEquals(1, mRuns.invocations(waiting.id).size)
                assertEquals(1, mRuns.get(waiting.id)?.toolCalls)
                assertEquals(
                    "Takes the northern road.",
                    mChats.getLatestMessageBySessionId(mConversation.id)?.content
                )
                assertTrue(server.requests[1].toString().contains("North"))
            } finally {
                scope.scope.cancel()
            }
        }
    }

    @Test
    fun proposedSceneAndMemoryCommitOnlyWithAcceptedFinalEvidence(): Unit = runBlocking {
        val evidence = "The party reaches the gate."
        AgentHttpServer { index, _ ->
            AgentHttpServer.Reply(
                when (index) {
                    1 -> call(
                        "update_scene_state",
                        jsonObject(
                            "expectedRevision" to 0,
                            "sourceMessageId" to 0,
                            "evidence" to evidence,
                            "changes" to jsonArray(listOf(jsonObject("path" to JsonArray().apply {
                                add("location")
                            }, "value_json" to "\"gate\"")))
                        )
                    )

                    2 -> call(
                        "upsert_memory",
                        jsonObject(
                            "body" to "The party reached the gate.",
                            "kind" to "event",
                            "sourceMessageId" to 0,
                            "evidence" to evidence
                        )
                    )

                    else -> text(evidence)
                }
            )
        }.use { server ->
            mManager.run(input(provider(server)))
            assertEquals(
                "gate",
                AgentJson.parse(get<SceneStateRepository>().get(mConversation).stateJson).asJsonObject["location"].asString
            )
            val memories =
                get<MemoryRepository>().observeEntries(MemoryTarget.Chat(mConversation.id)).first()
            assertEquals(1, memories.size)
            assertEquals(
                "AgentTool",
                get<MemoryRepository>().history(
                    MemoryTarget.Chat(mConversation.id),
                    memories.single().ref
                ).last().fields.origin
            )
            val message = requireNotNull(mChats.getLatestMessageBySessionId(mConversation.id))
            assertEquals(
                message.id,
                get<SceneStateRepository>().revisions(mConversation).last().effectiveAfterMessageId
            )
            assertEquals(
                message.id,
                get<MemoryRepository>().history(
                    MemoryTarget.Chat(mConversation.id),
                    memories.single().ref
                ).last().sources.single().chatMessageId
            )
        }
    }

    @Test
    fun unacceptedEvidenceAndCancellationLeaveNoSceneMutation(): Unit = runBlocking {
        AgentHttpServer { index, _ ->
            if (index == 1) AgentHttpServer.Reply(
                call(
                    "update_scene_state",
                    jsonObject(
                        "expectedRevision" to 0,
                        "sourceMessageId" to 0,
                        "evidence" to "Invented event",
                        "changes" to jsonArray(
                            listOf(
                                jsonObject(
                                    "path" to JsonArray().apply { add("location") },
                                    "value_json" to "\"invented\""
                                )
                            )
                        )
                    )
                )
            ) else AgentHttpServer.Reply("", stream = true, hold = true)
        }.use { server ->
            val executing = async { mManager.run(input(provider(server)).copy(streaming = false)) }
            withTimeout(4000) {
                server.requested.receive()
                server.requested.receive()
            }
            val runId = requireNotNull(mRuns.active(mConversation)).id
            mManager.stop(mConversation)
            assertTrue(runCatching { executing.await() }.isFailure)
            assertEquals(AgentRunState.Cancelled, mRuns.get(runId)?.state)
            assertEquals("{}", get<SceneStateRepository>().get(mConversation).stateJson)
            assertEquals(0, mChats.getMessageCountBySessionId(mConversation.id))
        }
    }

    @Test
    fun limitsReserveAToolFreeFinalRequestAndFreezePrefix(): Unit = runBlocking {
        mRuns.saveSettings(
            mConversation,
            true,
            null,
            mRuns.policy(mConversation).copy(maxToolRounds = 1, maxModelRequests = 3)
        )
        AgentHttpServer { index, _ ->
            AgentHttpServer.Reply(
                if (index == 1) call(
                    "calculate",
                    jsonObject("expression" to "2^3")
                ) else text("Eight.")
            )
        }.use { server ->
            val plan = mManager.run(input(provider(server)))
            assertEquals("none", server.requests[1]["tool_choice"].asString)
            val first = server.requests[0].getAsJsonArray("messages")
            val second = server.requests[1].getAsJsonArray("messages")
            first.forEachIndexed { index, message -> assertEquals(message, second[index]) }
            val run = mRuns.observe(mConversation).first().first()
            assertEquals(2, run.modelRequests)
            assertTrue(run.totalTokens > 0)
            assertNotNull(run.messageId)
            assertFalse(plan.providerDigest.contains("secret"))
            // 不可裁剪内容超限时保留具体预算错误，且不能先发一次模型请求。
            val sent = server.requests.size
            try {
                mManager.run(input(plan.input.provider.copy(contextTokens = 256)))
                error("Expected prompt budget failure")
            } catch (_: PromptBudgetExceededException) {
            }
            assertEquals(sent, server.requests.size)
        }
    }

    @Test
    fun watermarkCompactsOnlyOldHistoryAndCountsTheSummaryRequest(): Unit = runBlocking {
        mRuns.saveSettings(
            mConversation,
            true,
            null,
            mRuns.policy(mConversation).copy(compactionWatermarkPercent = 1)
        )
        AgentHttpServer { index, _ ->
            AgentHttpServer.Reply(
                if (index == 1) text("Old events summarized.") else text(
                    "Final reply."
                )
            )
        }.use { server ->
            val base = input(provider(server))
            val histories = (1L..6L).map { id ->
                PromptMessageDraft(
                    if (id % 2 == 0L) LLMMessageRole.Assistant else LLMMessageRole.User,
                    "Historical event $id.",
                    PromptSource(PromptSourceKind.ChatHistory, referenceId = id),
                    100,
                    true
                )
            }
            val plan = mManager.run(
                base.copy(
                    drafts = histories,
                    request = base.request.copy(messages = histories.map {
                        LLMMessage(
                            it.role,
                            it.content
                        )
                    })
                )
            )
            assertEquals(4L, plan.compactionCoveredMessageId)
            assertFalse(server.requests[0].has("tools"))
            assertTrue(server.requests[1].toString().contains("Old events summarized."))
            assertTrue(server.requests[1].toString().contains("Historical event 6."))
            assertEquals(2, mRuns.observe(mConversation).first().first().modelRequests)
        }
    }

    @Test
    fun groupQuestionPausesAndRestoresTheOriginalRemainingQueue(): Unit = runBlocking {
        val group = ConversationRef.Group(
            mGroups.createSession(
                title = "Group",
                characterIds = listOf(mCharacterId, mOtherId),
                activationStrategy = GroupChatSession.ActivationStrategy.Manual,
                allowSelfResponses = false,
                userPersonaId = 1
            )
        )
        mRuns.saveSettings(group, true, null)
        AgentHttpServer { index, _ ->
            AgentHttpServer.Reply(
                when (index) {
                    1 -> call(
                        "ask_user",
                        jsonObject("question" to "A private planning detail?")
                    )

                    2 -> text("Alice has finished.")
                    else -> text("Bob follows Alice.")
                }
            )
        }.use { server ->
            val input = input(provider(server)).copy(
                conversation = group,
                speakerName = "Alice",
                otherSpeakerNames = listOf("Bob"),
                batchId = "batch-fixture",
                remainingSpeakers = listOf(mOtherId)
            )
            try {
                mManager.run(input)
                error("Expected waiting")
            } catch (_: AgentWaitingForUser) {
            }
            assertEquals(1, server.requests.size)
            get<GroupChatAgentRuntime>().resume(group.id, "PRIVATE_ANSWER_SENTINEL", false)
            val messages = mDatabase.getGroupChatMessageDao().getMessages(group.id)
            assertEquals(
                listOf(mCharacterId, mOtherId),
                messages.mapNotNull { it.speakerCharacterId })
            assertFalse(server.requests[2].toString().contains("PRIVATE_ANSWER_SENTINEL"))
            assertTrue(server.requests[2].toString().contains("Alice has finished."))
        }
    }

    @Test
    fun builtinToolsEnforceLoreMessageAndPrivateMemoryScopeAndReplayDice(): Unit = runBlocking {
        val books = mDatabase.getLorebookDao()
        val allowedBook = books.insertOrReplace(Lorebook(name = "Allowed"))
        val deniedBook = books.insertOrReplace(Lorebook(name = "Denied"))
        val entries = mDatabase.getLorebookEntryDao()
        val allowed = entries.insertOrReplace(lore(allowedBook, "Visible gate"))
        val denied = entries.insertOrReplace(lore(deniedBook, "SECRET lore"))
        val session = requireNotNull(mChats.getSessionById(mConversation.id))
        mChats.updateSession(session.copy(lorebookEntrySet = "[$allowed]"))
        val source = mDatabase.getChatMessageDao().insertOrReplace(
            ChatMessage(
                sessionId = mConversation.id,
                createTime = 1,
                source = ChatMessage.Source.User,
                content = "I like tea."
            )
        )
        val another = mChats.createSession(
            characterId = mOtherId,
            title = "Other",
            userNote = "",
            lorebookEntryIds = emptyList(),
            userPersonaId = 1
        )
        val foreign = mDatabase.getChatMessageDao().insertOrReplace(
            ChatMessage(
                sessionId = another,
                createTime = 1,
                source = ChatMessage.Source.User,
                content = "SECRET message"
            )
        )
        val token = mRuns.start(mConversation, mCharacterId, 1)
        val step = mRuns.appendStep(token, "Tools")
        val registry = get<ToolRegistry>()
        var ordinal = 0
        suspend fun execute(id: String, args: JsonObject): ToolResult {
            val tool = requireNotNull(registry.find(id))
            return get<ToolExecutor>().execute(
                token,
                ToolCallRequest(
                    step,
                    ordinal++,
                    id,
                    tool.definition.contractVersion,
                    tool.digest,
                    args.toString()
                )
            )
        }
        assertTrue(execute("search_lore", jsonObject("query" to "gate")) is ToolResult.Success)
        assertTrue(execute("read_lore_entry", jsonObject("id" to allowed)) is ToolResult.Success)
        assertTrue(
            execute(
                "read_lore_entry",
                jsonObject("id" to denied)
            ) is ToolResult.RecoverableError
        )
        assertTrue(execute("search_chat", jsonObject("query" to "tea")) is ToolResult.Success)
        assertTrue(
            execute(
                "read_messages",
                jsonObject("message_id" to foreign)
            ) is ToolResult.RecoverableError
        )
        assertTrue(
            execute(
                "upsert_memory",
                jsonObject(
                    "body" to "Likes tea.",
                    "kind" to "preference",
                    "sourceMessageId" to source,
                    "evidence" to "like tea"
                )
            ) is ToolResult.Success
        )
        val memories =
            get<MemoryRepository>().observeEntries(MemoryTarget.Chat(mConversation.id)).first()
        assertEquals(1, memories.size)
        assertTrue(execute("search_memory", jsonObject("query" to "tea")) is ToolResult.Success)
        val memory = memories.single()
        assertTrue(
            execute(
                "read_memory",
                jsonObject("memoryId" to memory.ref.id, "table" to "Chat")
            ) is ToolResult.Success
        )
        // 准备时版本不匹配的调用必须固定为错误，之后版本匹配也不能重放成写入。
        val correction = requireNotNull(registry.find("correct_memory"))
        val rejected = ToolCallRequest(
            step, ordinal++, "correct_memory", 1, correction.digest,
            jsonObject(
                "memoryId" to memory.ref.id,
                "table" to "Chat",
                "expectedRevision" to 2,
                "body" to "Unexpected replay.",
                "kind" to "preference",
                "sourceMessageId" to source,
                "evidence" to "like tea"
            ).toString()
        )
        assertTrue(get<ToolExecutor>().execute(token, rejected) is ToolResult.RecoverableError)
        assertTrue(
            execute(
                "correct_memory",
                jsonObject(
                    "memoryId" to memory.ref.id,
                    "table" to "Chat",
                    "expectedRevision" to 1,
                    "body" to "Prefers tea.",
                    "kind" to "preference",
                    "sourceMessageId" to source,
                    "evidence" to "like tea"
                )
            ) is ToolResult.Success
        )
        assertTrue(get<ToolExecutor>().execute(token, rejected) is ToolResult.RecoverableError)
        assertEquals(
            "Prefers tea.",
            get<MemoryRepository>().observeEntries(MemoryTarget.Chat(mConversation.id)).first()
                .single().fields.content.body
        )
        assertTrue(
            execute(
                "invalidate_memory",
                jsonObject(
                    "memoryId" to memory.ref.id,
                    "table" to "Chat",
                    "expectedRevision" to 2,
                    "sourceMessageId" to source,
                    "evidence" to "like tea"
                )
            ) is ToolResult.Success
        )
        val dice = requireNotNull(registry.find("roll_dice"))
        val call = ToolCallRequest(
            step,
            ordinal++,
            "roll_dice",
            1,
            dice.digest,
            "{\"notation\":\"4d6kh3+2\"}"
        )
        assertEquals(
            get<ToolExecutor>().execute(token, call),
            get<ToolExecutor>().execute(token, call)
        )
        assertTrue(
            execute(
                "calculate",
                jsonObject("expression" to "1/0")
            ) is ToolResult.RecoverableError
        )
        assertTrue(execute("get_scene_state", JsonObject()) is ToolResult.Success)
    }

    @Test
    fun promotionRequiresExplicitConfirmationEvenWhenAskUserIsDisabledAndReadOnlyBlocksWrites(): Unit =
        runBlocking {
            val source = mDatabase.getChatMessageDao().insertOrReplace(
                ChatMessage(
                    sessionId = mConversation.id,
                    createTime = 1,
                    source = ChatMessage.Source.User,
                    content = "I like tea."
                )
            )
            mRuns.saveSettings(
                mConversation,
                true,
                null,
                mRuns.policy(mConversation)
                    .copy(enabledToolIdsJson = "[\"upsert_memory\",\"promote_memory_scope\"]")
            )
            val memories = get<MemoryRepository>()
            AgentHttpServer { index, _ ->
                val response = when (index) {
                    1 -> call(
                        "upsert_memory",
                        jsonObject(
                            "body" to "Likes tea.",
                            "kind" to "preference",
                            "sourceMessageId" to source,
                            "evidence" to "like tea"
                        )
                    )

                    2 -> {
                        val entry = runBlocking {
                            memories.observeEntries(MemoryTarget.Chat(mConversation.id)).first()
                                .single()
                        }
                        call(
                            "promote_memory_scope",
                            jsonObject(
                                "memoryId" to entry.ref.id,
                                "table" to "Chat",
                                "expectedRevision" to entry.fields.currentRevision,
                                "sourceMessageId" to source,
                                "evidence" to "like tea"
                            )
                        )
                    }

                    else -> text("Your preference is remembered.")
                }
                AgentHttpServer.Reply(response)
            }.use { server ->
                try {
                    mManager.run(input(provider(server)))
                    error("Expected confirmation")
                } catch (_: AgentWaitingForUser) {
                }
                assertTrue(mManager.waiting(mConversation)?.kind == ToolUserRequestKind.LocalWriteConfirmation)
                assertTrue(
                    memories.observeEntries(MemoryTarget.Character(mCharacterId)).first().isEmpty()
                )
                mManager.resume(mConversation, "", true)
                assertEquals(
                    1,
                    memories.observeEntries(MemoryTarget.Character(mCharacterId)).first().size
                )
            }
            // 只读策略在执行器入口强制执行，不能用旧调用或新调用绕过。
            mRuns.saveSettings(
                mConversation,
                true,
                null,
                mRuns.policy(mConversation).copy(memoryReadOnly = true)
            )
            val token = mRuns.start(mConversation, mCharacterId, 1)
            val step = mRuns.appendStep(token, "Tools")
            val tool = requireNotNull(get<ToolRegistry>().find("upsert_memory"))
            val result = get<ToolExecutor>().execute(
                token,
                ToolCallRequest(
                    step,
                    0,
                    "upsert_memory",
                    1,
                    tool.digest,
                    jsonObject(
                        "body" to "A new fact.",
                        "kind" to "fact",
                        "sourceMessageId" to source,
                        "evidence" to "like tea"
                    ).toString()
                )
            )
            assertTrue(result is ToolResult.FatalError)
            assertEquals(
                1,
                memories.observeEntries(MemoryTarget.Chat(mConversation.id)).first().size
            )
        }

    @Test
    fun upsertMemoryResolvesCommittedUserEvidenceAndCreationDefaultsSafely(): Unit = runBlocking {
        // 先插入一条角色开场白，再插入用户消息，确保用户消息主键不等于 1。
        mDatabase.getChatMessageDao().insertOrReplace(
            ChatMessage(
                sessionId = mConversation.id,
                createTime = 1,
                source = ChatMessage.Source.Char,
                content = "你好呀。"
            )
        )
        val userMessageId = mDatabase.getChatMessageDao().insertOrReplace(
            ChatMessage(
                sessionId = mConversation.id,
                createTime = 2,
                source = ChatMessage.Source.User,
                content = "记住我们每天都要吃苹果，以后叫我小猫。"
            )
        )
        val token = mRuns.start(mConversation, mCharacterId, 1)
        val step = mRuns.appendStep(token, "Tools")
        val tool = requireNotNull(get<ToolRegistry>().find("upsert_memory"))
        var ordinal = 0
        suspend fun upsert(args: JsonObject): ToolResult = get<ToolExecutor>().execute(
            token,
            ToolCallRequest(step, ordinal++, "upsert_memory", 1, tool.digest, args.toString())
        )
        // 省略 sourceMessageId 且携带新建默认 table/expectedRevision=0 应自动绑定当前用户消息并成功提交。
        val created = upsert(
            jsonObject(
                "body" to "双方约定每天吃苹果。",
                "kind" to "promise",
                "table" to "Chat",
                "expectedRevision" to 0,
                "evidence" to "“记住我们每天都要吃苹果”"
            )
        )
        assertTrue(created is ToolResult.Success)
        // 猜测错误的 sourceMessageId=1 指向开场白时，只要摘录确属当前会话用户消息仍可绑定真实主键。
        val guessed = upsert(
            jsonObject(
                "body" to "用户希望被称为小猫。",
                "kind" to "preference",
                "sourceMessageId" to 1,
                "evidence" to "叫我小猫"
            )
        )
        assertTrue(guessed is ToolResult.Success)
        // 伪造证据与只读角色表必须继续拒绝。
        val fabricated = upsert(
            jsonObject(
                "body" to "用户喜欢香蕉。",
                "kind" to "preference",
                "sourceMessageId" to 1,
                "evidence" to "我最喜欢吃香蕉"
            )
        )
        assertTrue(fabricated is ToolResult.RecoverableError)
        val readOnlyTable = upsert(
            jsonObject(
                "body" to "跨会话越权写入。",
                "kind" to "fact",
                "table" to "Character",
                "evidence" to "叫我小猫"
            )
        )
        assertTrue(readOnlyTable is ToolResult.RecoverableError)
        val memories =
            get<MemoryRepository>().observeEntries(MemoryTarget.Chat(mConversation.id)).first()
        assertEquals(2, memories.size)
        memories.forEach { entry ->
            val sources = get<MemoryRepository>()
                .history(MemoryTarget.Chat(mConversation.id), entry.ref)
                .last().sources
            assertEquals(userMessageId, sources.single().chatMessageId)
        }
    }

    @Test
    fun rejectedScenePredecessorDoesNotApplyDependentRevisionOrLoseTheFinalReply(): Unit =
        runBlocking {
            AgentHttpServer { index, _ ->
                val response =
                    if (index <= 2) call(
                        "update_scene_state", jsonObject(
                            "expectedRevision" to index - 1,
                            "sourceMessageId" to 0,
                            "evidence" to if (index == 1) "Unsupported predecessor." else "Reaches the gate.",
                            "changes" to jsonArray(
                                listOf(
                                    jsonObject(
                                        "path" to JsonArray()
                                            .apply { add("location") }, "value_json" to "\"gate\""
                                    )
                                )
                            )
                        )
                    ) else text("Reaches the gate.")
                AgentHttpServer.Reply(response)
            }.use { server ->
                mManager.run(input(provider(server)))
                assertEquals("{}", get<SceneStateRepository>().get(mConversation).stateJson)
                assertEquals(
                    "Reaches the gate.",
                    mChats.getLatestMessageBySessionId(mConversation.id)?.content
                )
            }
        }

    private suspend fun provider(server: AgentHttpServer): LLMProvider {
        val provider = LLMProvider(
            name = "Fixture",
            providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = server.baseUrl,
            model = "fixture",
            maxTokens = 256,
            contextTokens = 16000,
            systemPrompt = "model system"
        )
        val id = get<LLMRepository>().saveProvider(provider)
        AppModel.currentLLMProvider = id
        return requireNotNull(get<LLMRepository>().getProviderById(id))
    }

    private fun input(provider: LLMProvider) = AgentTurnInput(
        mConversation,
        mCharacterId,
        1,
        provider,
        LLMGenerationRequest(
            listOf(LLMMessage(LLMMessageRole.User, "Continue the scene.")),
            options = LLMGenerationOptions(maxTokens = 256),
            isPromptFinalized = true
        ),
        emptyList(),
        PromptTemplateSnapshot.defaults(),
        emptyList(),
        mapOf("char" to "Alice", "user" to "User"),
        "{}",
        streaming = false
    )

    private fun call(name: String, args: JsonObject) = jsonObject(
        "choices" to jsonArray(
            listOf(
                jsonObject(
                    "finish_reason" to "tool_calls",
                    "message" to jsonObject(
                        "role" to "assistant",
                        "content" to "",
                        "tool_calls" to jsonArray(
                            listOf(
                                jsonObject(
                                    "id" to "same-remote-id",
                                    "type" to "function",
                                    "function" to jsonObject(
                                        "name" to name,
                                        "arguments" to args.toString()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    ).toString()

    private fun text(text: String) = jsonObject(
        "choices" to jsonArray(
            listOf(
                jsonObject(
                    "finish_reason" to "stop",
                    "message" to jsonObject("role" to "assistant", "content" to text)
                )
            )
        )
    ).toString()

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

    private fun lore(book: Long, content: String) = LorebookEntry(
        lorebookId = book,
        name = "Entry",
        keywords = "[]",
        secondaryKeywords = "[]",
        order = 0,
        depth = 0,
        category = "[]",
        content = content
    )

    private inline fun <reified T : Any> get(): T = mApplication.koin.get()
}
