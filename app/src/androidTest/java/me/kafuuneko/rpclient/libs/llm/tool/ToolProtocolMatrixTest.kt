package me.kafuuneko.rpclient.libs.llm.tool

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.agent.AgentHttpServer
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
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
import me.kafuuneko.rpclient.libs.agent.tool.ToolResultCodec
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMToolExchange
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolStreamEvent
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.migration.AgentDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.migration.MemoryDatabaseConstraints
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository
import me.kafuuneko.rpclient.libs.room.withTestPersonas
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 五个真实客户端经本地 HTTP 完成同一矩阵，不用模拟返回值替代协议传输。 */
@RunWith(Parameterized::class)
class ToolProtocolMatrixTest(private val mProtocol: LLMProviderProtocol) {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var mDatabase: AppDatabase
    private lateinit var mLogs: RequestLogDatabase
    private lateinit var mRuns: AgentRunRepository
    private lateinit var mScenes: SceneStateRepository
    private lateinit var mRegistry: ToolRegistry
    private lateinit var mExecutor: ToolExecutor
    private lateinit var mRoot: File
    private lateinit var mConversation: ConversationRef.Single
    private var mOldDebug = false
    private val mHttp = OkHttpClient()
    private val mDefinition = AgentToolDefinition(
        "lookup",
        "lookup",
        1,
        "Fixture lookup",
        ToolSchema(
            ToolSchema.Type.Object,
            properties = mapOf("query" to ToolSchema(ToolSchema.Type.String)),
            required = setOf("query")
        ),
        ToolEffect.LocalWrite
    )

    /** 使用真实 Room 验证受控写入幂等，与每个协议轮次绑定相同客户端身份。 */
    @Before
    fun setup(): Unit = runBlocking {
        mOldDebug = AppModel.debugModeEnabled
        AppModel.debugModeEnabled = false
        mDatabase =
            Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java).withTestPersonas()
                .addCallback(MemoryDatabaseConstraints).addCallback(AgentDatabaseConstraints)
                .build()
        mLogs = Room.inMemoryDatabaseBuilder(mContext, RequestLogDatabase::class.java).build()
        mRuns = AgentRunRepository(mDatabase)
        mScenes = SceneStateRepository(mDatabase, mRuns)
        val character = mDatabase.getCharacterDao().insertOrReplace(
            Character(
                name = "Fixture",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "[]",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            )
        )
        val chat = mDatabase.getChatSessionDao().insertOrReplace(
            ChatSession(
                characterId = character,
                createTime = 0,
                latestTime = 0,
                lorebookEntrySet = "[]",
                title = "Fixture",
                userNote = "",
                userPersonaId = 1
            )
        )
        mConversation = ConversationRef.Single(chat)
        val tool = object : AgentTool {
            override val definition = mDefinition
            override suspend fun prepare(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolAction {
                val current = mScenes.get(mConversation)
                return ToolAction.LocalWrite(
                    ToolResult.Success(
                        jsonObject("value" to arguments["query"].asString),
                        ToolWriteDisposition.Committed
                    )
                ) {
                    mScenes.applyAgentProposal(
                        mConversation,
                        current.revision,
                        listOf(
                            SceneStateChange(
                                listOf("location"),
                                JsonPrimitive(arguments["query"].asString)
                            )
                        ),
                        0
                    )
                }
            }
        }
        mRegistry = ToolRegistry(listOf(tool))
        mRoot = File(mContext.cacheDir, "agent-matrix-${System.nanoTime()}")
        mExecutor = ToolExecutor(mRegistry, mRuns, FileAgentCheckpointStore(mRoot))
        mRuns.saveSettings(
            mConversation,
            true,
            null,
            mRuns.policy(mConversation).copy(enabledToolIdsJson = "[\"lookup\"]")
        )
    }

    @After
    fun close() {
        mHttp.dispatcher.cancelAll()
        mHttp.connectionPool.evictAll()
        mDatabase.close()
        mLogs.close()
        mRoot.deleteRecursively()
        AppModel.debugModeEnabled =
            mOldDebug
    }

    @Test
    fun nonStreamingRoundTripPreservesNativeContinuationAndWriteReplay(): Unit =
        runBlocking { roundTrip(false) }

    @Test
    fun streamingFragmentsAndMultipleCallsPreserveNativeContinuation(): Unit =
        runBlocking { roundTrip(true) }

    private suspend fun roundTrip(streaming: Boolean) {
        AgentHttpServer { index, _ ->
            AgentHttpServer.Reply(
                if (index == 1) if (streaming) streamingTools() else tools().toString() else finalResponse().toString(),
                stream = streaming && index == 1
            )
        }.use { server ->
            val client = client(server)
            val request = request()
            val turn = if (streaming) client.streamToolTurn(request).toList()
                .filterIsInstance<LLMToolStreamEvent.Finished>()
                .single().turn else client.generateToolTurn(request)
            assertTrue(turn.content.isEmpty())
            assertEquals(
                listOf("one", "two"),
                turn.calls.map { parseObject(it.arguments)["query"].asString })
            val character = requireNotNull(
                mDatabase.getChatSessionDao().getSessionById(mConversation.id)
            ).characterId
            val token = mRuns.start(mConversation, character, 1)
            val step = mRuns.appendStep(token, "Tools")
            val registered = requireNotNull(mRegistry.find("lookup"))
            val calls = turn.calls.mapIndexed { index, call ->
                ToolCallRequest(
                    step,
                    index,
                    "lookup",
                    1,
                    registered.digest,
                    call.arguments,
                    call.remoteId
                )
            }
            val results = calls.map { call -> mExecutor.execute(token, call) }
            assertTrue(results.all { it is ToolResult.Success })
            calls.forEach { assertTrue(mExecutor.execute(token, it) is ToolResult.Success) }
            assertEquals(2L, mScenes.get(mConversation).revision)
            val final = client.generateToolTurn(
                request.copy(
                    exchanges = listOf(
                        LLMToolExchange(
                            turn,
                            results.map(ToolResultCodec::encode)
                        )
                    )
                )
            )
            assertEquals("done", final.content)
            val body = server.requests[1].toString()
            assertTrue(body.contains("one") && body.contains("two"))
            assertTrue(body.contains(signature()))
            assertFalse(body.contains("previous_response_id") || body.contains("previous_interaction_id"))
            assertEquals(1, Regex("model system").findAll(server.requests[1].toString()).count())
            assertTrue(server.requests.first().toString().contains("lookup"))
            client.generate(request.base)
            val ordinary = server.requests.last()
            assertFalse(ordinary.has("tools") || ordinary.has("tool_choice") || ordinary.has("toolConfig"))
            assertFalse(ordinary.getAsJsonObject("generation_config")?.has("tool_choice") == true)
            val disabled = mRuns.policy(mConversation).copy(enabledToolIdsJson = "[]")
            mRuns.saveSettings(mConversation, true, null, disabled)
            assertTrue(mExecutor.execute(token, calls.first()) is ToolResult.FatalError)
            assertTrue(
                mExecutor.execute(
                    token,
                    calls.first().copy(toolId = "missing")
                ) is ToolResult.RecoverableError
            )
        }
    }

    @Test
    fun truncatedToolOnlyResponseNeverExecutes(): Unit = runBlocking {
        AgentHttpServer { _, _ ->
            AgentHttpServer.Reply(tools().apply {
                when (mProtocol) {
                    LLMProviderProtocol.OpenAICompatible -> getAsJsonArray("choices")[0].asJsonObject.addProperty(
                        "finish_reason",
                        "length"
                    )

                    LLMProviderProtocol.OpenAIResponses, LLMProviderProtocol.GeminiInteractions -> addProperty(
                        "status",
                        "incomplete"
                    )

                    LLMProviderProtocol.AnthropicMessages -> addProperty(
                        "stop_reason",
                        "max_tokens"
                    )

                    LLMProviderProtocol.Gemini -> getAsJsonArray("candidates")[0].asJsonObject.addProperty(
                        "finishReason",
                        "MAX_TOKENS"
                    )
                }
            }.toString())
        }.use { server ->
            try {
                client(server).generateToolTurn(request())
                error("Expected truncation failure")
            } catch (_: LLMToolProtocolException) {
            }
            assertEquals(0L, mScenes.get(mConversation).revision)
        }
    }

    @Test
    fun cancellationClosesAnOpenStreamWithoutDispatchingCalls(): Unit = runBlocking {
        AgentHttpServer { _, _ ->
            AgentHttpServer.Reply(
                streamingTools().substringBeforeLast("data:"),
                stream = true,
                hold = true
            )
        }.use { server ->
            val running = async { client(server).streamToolTurn(request()).toList() }
            withTimeout(4000) { server.requested.receive() }
            running.cancelAndJoin()
            withTimeout(4000) { server.disconnected.await() }
            assertEquals(0L, mScenes.get(mConversation).revision)
        }
    }

    @Test
    fun debugModeRecordsStreamingAndNonStreamingToolRequestLogs(): Unit = runBlocking {
        AppModel.debugModeEnabled = true
        val logs = LLMRequestLogRepository(mLogs)
        AgentHttpServer { index, _ ->
            when (index) {
                1 -> AgentHttpServer.Reply(finalResponse().toString(), stream = false)
                2 -> AgentHttpServer.Reply(streamingTools(), stream = true)
                else -> AgentHttpServer.Reply("{\"error\":{\"code\":\"fixture\"}}", status = 429)
            }
        }.use { server ->
            val client = client(server)
            client.generateToolTurn(request())
            client.streamToolTurn(request()).toList()
            assertTrue(runCatching { client.streamToolTurn(request()).toList() }.isFailure)
            val overviews = logs.getLogOverviews(previewLength = 200, limit = 10, offset = 0)
            assertEquals(3, overviews.size)
            assertEquals(1, overviews.count { !it.isStreaming })
            assertEquals(2, overviews.count { it.isStreaming })
            overviews.forEach { overview ->
                val req = requireNotNull(logs.getRequestJson(overview.id))
                val resp = requireNotNull(logs.getResponseJson(overview.id))
                assertTrue(req.contains("lookup"))
                assertTrue(resp.isNotBlank())
            }
        }
    }

    @Test
    fun transportErrorAndEarlyEofDoNotReportFinished(): Unit = runBlocking {
        AgentHttpServer { index, _ ->
            if (index == 1) AgentHttpServer.Reply(
                "{\"error\":{\"code\":\"fixture\"}}",
                status = 429
            ) else AgentHttpServer.Reply("", stream = true)
        }.use { server ->
            assertTrue(runCatching { client(server).generateToolTurn(request()) }.isFailure)
            assertTrue(runCatching { client(server).streamToolTurn(request()).toList() }.isFailure)
            assertEquals(0L, mScenes.get(mConversation).revision)
        }
    }

    private fun client(server: AgentHttpServer) =
        LLMClientFactory(mHttp, LLMRequestLogRepository(mLogs)).create(
            LLMProviderConfig(
                "Fixture",
                LLMProviderType.Custom,
                mProtocol,
                server.baseUrl,
                model = "fixture",
                systemPrompt = "model system"
            )
        )

    private fun request() = LLMToolGenerationRequest(
        LLMGenerationRequest(
            listOf(
                LLMMessage(
                    LLMMessageRole.User,
                    "frozen prefix"
                )
            ), isPromptFinalized = true
        ), listOf(mDefinition)
    )

    private fun signature() = when (mProtocol) {
        LLMProviderProtocol.OpenAICompatible -> "reasoning-sentinel"
        LLMProviderProtocol.OpenAIResponses -> "encrypted-sentinel"
        else -> "signature-sentinel"
    }

    private fun tools(): JsonObject = when (mProtocol) {
        LLMProviderProtocol.OpenAICompatible -> jsonObject(
            "choices" to jsonArray(
                listOf(
                    jsonObject(
                        "finish_reason" to "tool_calls",
                        "message" to jsonObject(
                            "role" to "assistant",
                            "content" to "",
                            "reasoning_content" to signature(),
                            "tool_calls" to jsonArray(
                                listOf(
                                    "one",
                                    "two"
                                ).mapIndexed { index, query ->
                                    jsonObject(
                                        "id" to "c$index",
                                        "type" to "function",
                                        "function" to jsonObject(
                                            "name" to "lookup",
                                            "arguments" to jsonObject("query" to query).toString()
                                        )
                                    )
                                })
                        )
                    )
                )
            )
        )

        LLMProviderProtocol.OpenAIResponses -> jsonObject(
            "status" to "completed",
            "output" to jsonArray(
                listOf(
                    jsonObject(
                        "id" to "r",
                        "type" to "reasoning",
                        "encrypted_content" to signature(),
                        "summary" to jsonArray(emptyList())
                    )
                ) + listOf(
                    "one",
                    "two"
                ).mapIndexed { index, query ->
                    jsonObject(
                        "type" to "function_call",
                        "id" to "fc$index",
                        "call_id" to "c$index",
                        "status" to "completed",
                        "name" to "lookup",
                        "arguments" to jsonObject("query" to query).toString()
                    )
                })
        )

        LLMProviderProtocol.AnthropicMessages -> jsonObject(
            "stop_reason" to "tool_use",
            "content" to jsonArray(
                listOf(
                    jsonObject(
                        "type" to "thinking",
                        "thinking" to "analysis",
                        "signature" to signature()
                    )
                ) + listOf(
                    "one",
                    "two"
                ).mapIndexed { index, query ->
                    jsonObject(
                        "type" to "tool_use",
                        "id" to "c$index",
                        "name" to "lookup",
                        "input" to jsonObject("query" to query)
                    )
                })
        )

        LLMProviderProtocol.Gemini -> jsonObject(
            "candidates" to jsonArray(
                listOf(
                    jsonObject(
                        "finishReason" to "STOP",
                        "content" to jsonObject(
                            "role" to "model",
                            "parts" to jsonArray(
                                listOf(
                                    "one",
                                    "two"
                                ).map { query ->
                                    jsonObject(
                                        "functionCall" to jsonObject(
                                            "name" to "lookup",
                                            "args" to jsonObject("query" to query)
                                        ), "thoughtSignature" to signature()
                                    )
                                })
                        )
                    )
                )
            )
        )

        LLMProviderProtocol.GeminiInteractions -> jsonObject(
            "status" to "requires_action",
            "steps" to jsonArray(
                listOf(
                    jsonObject(
                        "type" to "thought",
                        "summary" to jsonArray(emptyList()),
                        "signature" to signature()
                    )
                ) + listOf(
                    "one",
                    "two"
                ).mapIndexed { index, query ->
                    jsonObject(
                        "type" to "function_call",
                        "id" to "c$index",
                        "name" to "lookup",
                        "arguments" to jsonObject("query" to query),
                        "signature" to signature()
                    )
                })
        )
    }

    private fun finalResponse(): JsonObject = when (mProtocol) {
        LLMProviderProtocol.OpenAICompatible -> jsonObject(
            "choices" to jsonArray(
                listOf(
                    jsonObject(
                        "finish_reason" to "stop",
                        "message" to jsonObject("role" to "assistant", "content" to "done")
                    )
                )
            )
        )

        LLMProviderProtocol.OpenAIResponses -> jsonObject(
            "status" to "completed",
            "output" to jsonArray(
                listOf(
                    jsonObject(
                        "type" to "message",
                        "role" to "assistant",
                        "content" to jsonArray(
                            listOf(
                                jsonObject(
                                    "type" to "output_text",
                                    "text" to "done"
                                )
                            )
                        )
                    )
                )
            )
        )

        LLMProviderProtocol.AnthropicMessages -> jsonObject(
            "stop_reason" to "end_turn",
            "content" to jsonArray(listOf(jsonObject("type" to "text", "text" to "done")))
        )

        LLMProviderProtocol.Gemini -> jsonObject(
            "candidates" to jsonArray(
                listOf(
                    jsonObject(
                        "finishReason" to "STOP",
                        "content" to jsonObject(
                            "role" to "model",
                            "parts" to jsonArray(listOf(jsonObject("text" to "done")))
                        )
                    )
                )
            )
        )

        LLMProviderProtocol.GeminiInteractions -> jsonObject(
            "status" to "completed",
            "steps" to jsonArray(
                listOf(
                    jsonObject(
                        "type" to "model_output",
                        "content" to jsonArray(
                            listOf(
                                jsonObject(
                                    "type" to "text",
                                    "text" to "done"
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun streamingTools(): String {
        fun frame(type: String, value: JsonObject) = "event: $type\ndata: $value\n\n"
        return when (mProtocol) {
            LLMProviderProtocol.OpenAICompatible -> frame(
                "",
                jsonObject(
                    "choices" to jsonArray(
                        listOf(
                            jsonObject(
                                "delta" to jsonObject(
                                    "reasoning_content" to signature(),
                                    "tool_calls" to jsonArray(
                                        listOf(
                                            0,
                                            1
                                        ).map { index ->
                                            jsonObject(
                                                "index" to index,
                                                "id" to "c$index",
                                                "type" to "function",
                                                "function" to jsonObject(
                                                    "name" to "lookup",
                                                    "arguments" to "{\"query\":"
                                                )
                                            )
                                        })
                                )
                            )
                        )
                    )
                )
            ) +
                frame(
                    "",
                    jsonObject(
                        "choices" to jsonArray(
                            listOf(
                                jsonObject(
                                    "delta" to jsonObject(
                                        "tool_calls" to jsonArray(
                                            listOf(
                                                1 to "two",
                                                0 to "one"
                                            ).map { (index, query) ->
                                                jsonObject(
                                                    "index" to index,
                                                    "id" to "c$index",
                                                    "function" to jsonObject("arguments" to "\"$query\"}")
                                                )
                                            })
                                    ), "finish_reason" to "tool_calls"
                                )
                            )
                        )
                    )
                )

            LLMProviderProtocol.OpenAIResponses -> frame(
                "response.output_item.added",
                jsonObject("output_index" to 1, "item" to tools().getAsJsonArray("output")[1])
            ) + frame(
                "response.function_call_arguments.delta",
                jsonObject("item_id" to "fc0", "delta" to "{\"query\":")
            ) + frame(
                "response.function_call_arguments.delta",
                jsonObject("item_id" to "fc0", "delta" to "\"one\"}")
            ) + frame("response.completed", jsonObject("response" to tools()))

            LLMProviderProtocol.AnthropicMessages -> buildString {
                append(
                    frame(
                        "message_start",
                        jsonObject(
                            "message" to jsonObject(
                                "role" to "assistant",
                                "content" to jsonArray(emptyList())
                            )
                        )
                    )
                )
                val blocks = tools().getAsJsonArray("content")
                blocks.forEachIndexed { index, block ->
                    append(
                        frame(
                            "content_block_start",
                            jsonObject(
                                "index" to index,
                                "content_block" to if (index == 0) block else block.asJsonObject.deepCopy()
                                    .apply { add("input", JsonObject()) })
                        )
                    )
                    if (index > 0) {
                        append(
                            frame(
                                "content_block_delta",
                                jsonObject(
                                    "index" to index,
                                    "delta" to jsonObject(
                                        "type" to "input_json_delta",
                                        "partial_json" to "{\"query\":"
                                    )
                                )
                            )
                        )
                        append(
                            frame(
                                "content_block_delta",
                                jsonObject(
                                    "index" to index,
                                    "delta" to jsonObject(
                                        "type" to "input_json_delta",
                                        "partial_json" to "\"${if (index == 1) "one" else "two"}\"}"
                                    )
                                )
                            )
                        )
                    }
                    append(frame("content_block_stop", jsonObject("index" to index)))
                }
                append(
                    frame(
                        "message_delta",
                        jsonObject("delta" to jsonObject("stop_reason" to "tool_use"))
                    )
                )
                append(frame("message_stop", JsonObject()))
            }

            LLMProviderProtocol.Gemini -> tools().getAsJsonArray("candidates")[0].asJsonObject.getAsJsonObject(
                "content"
            ).getAsJsonArray("parts").joinToString("") { part ->
                frame(
                    "",
                    jsonObject(
                        "candidates" to jsonArray(
                            listOf(
                                jsonObject(
                                    "content" to jsonObject(
                                        "role" to "model",
                                        "parts" to jsonArray(listOf(part))
                                    )
                                )
                            )
                        )
                    )
                )
            } + frame(
                "",
                jsonObject("candidates" to jsonArray(listOf(jsonObject("finishReason" to "STOP"))))
            )

            LLMProviderProtocol.GeminiInteractions -> buildString {
                tools().getAsJsonArray("steps").forEachIndexed { index, value ->
                    val step = value.asJsonObject.deepCopy()
                    val arguments = step.remove("arguments")
                    append(frame("step.start", jsonObject("index" to index, "step" to step)))
                    if (arguments != null) {
                        val text = arguments.toString()
                        append(
                            frame(
                                "step.delta",
                                jsonObject(
                                    "index" to index,
                                    "delta" to jsonObject(
                                        "type" to "arguments_delta",
                                        "arguments" to text.take(5)
                                    )
                                )
                            )
                        )
                        append(
                            frame(
                                "step.delta",
                                jsonObject(
                                    "index" to index,
                                    "delta" to jsonObject(
                                        "type" to "arguments_delta",
                                        "arguments" to text.drop(5)
                                    )
                                )
                            )
                        )
                    }
                    append(frame("step.stop", jsonObject("index" to index)))
                }
                append(
                    frame(
                        "interaction.completed",
                        jsonObject("interaction" to jsonObject("status" to "requires_action"))
                    )
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun protocols() = LLMProviderProtocol.entries.map { arrayOf(it) }
    }
}
