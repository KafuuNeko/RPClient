package me.kafuuneko.rpclient.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.agent.model.AgentUiAction
import me.kafuuneko.rpclient.feature.chat.ChatViewModel
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.agent.AgentHttpServer
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.runtime.AgentRunManager
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext

/** 真实 Intent、Room 和 HTTP 验证两个页面入口，覆盖离页重进及等待后的任务卡关联。 */
class AgentConversationFlowTest {
    private val mKoin = GlobalContext.get()
    private val mChats = mKoin.get<ChatRepository>()
    private val mGroups = mKoin.get<GroupChatRepository>()
    private val mRuns = mKoin.get<AgentRunRepository>()
    private var mCharacter = 0L
    private var mOtherCharacter = 0L
    private var mGroup = 0L
    private var mProvider = 0L
    private var mOldProvider = 0L
    private var mOldStreaming = false
    private var mOldSummary = false

    /** 仅创建本测试持有的角色，关闭自动摘要以便精确核对请求与用户消息次数。 */
    @Before
    fun setup(): Unit = runBlocking {
        mOldProvider = AppModel.currentLLMProvider
        mOldStreaming = AppModel.streamEnabled
        mOldSummary = AppModel.autoSummaryEnabled
        AppModel.streamEnabled = false
        AppModel.autoSummaryEnabled = false
        mCharacter = mKoin.get<AppDatabase>().getCharacterDao().insertOrReplace(
            Character(
                name = "Agent fixture",
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
    }

    /** 不清理全局库，仅删除测试自己的实体并恢复模型与生成偏好。 */
    @After
    fun close(): Unit = runBlocking {
        if (mGroup > 0) mGroups.deleteSession(mGroup)
        if (mOtherCharacter > 0) mKoin.get<CharacterRepository>().deleteCharacter(mOtherCharacter)
        mKoin.get<CharacterRepository>().deleteCharacter(mCharacter)
        AppModel.currentLLMProvider = mOldProvider
        AppModel.streamEnabled = mOldStreaming
        AppModel.autoSummaryEnabled = mOldSummary
        if (mProvider > 0) mKoin.get<LLMRepository>().deleteProvider(mProvider)
    }

    @Test
    fun singleChatReentryResumesQuestionWithoutResendingTheUserMessage(): Unit = runBlocking {
        server().use { server ->
            configure(server)
            val persona = mKoin.get<UserPersonaRepository>().active().id
            val id = mChats.createSession(
                characterId = mCharacter,
                title = "Agent flow",
                userNote = "",
                lorebookEntryIds = emptyList(),
                userPersonaId = persona
            )
            val conversation = ConversationRef.Single(id)
            mRuns.saveSettings(conversation, true, null)
            withModel(::ChatViewModel) { vm ->
                val states = vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
                vm.emit(ChatUiIntent.Init(id.toString()))
                withTimeout(15_000) { states.first() }
                vm.emit(ChatUiIntent.ChangeInputDraft("Continue the story."))
                vm.emit(ChatUiIntent.SendMessage)
                withTimeout(15_000) { states.first { it.agentState.question != null && it.conversationState.generationState == ChatGenerationState.Idle } }
            }
            // 等待不持有页面协程，重新进入后使用原调用，不创建第二条用户输入。
            withModel(::ChatViewModel) { vm ->
                val states = vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
                vm.emit(ChatUiIntent.Init(id.toString()))
                withTimeout(15_000) { states.first { it.agentState.question != null && it.conversationState.generationState == ChatGenerationState.Idle } }
                val pending =
                    withTimeout(15_000) { states.first { it.agentState.question != null } }.agentState.question!!.runId
                vm.emit(ChatUiIntent.AgentAction(AgentUiAction.ToggleTask(pending)))
                vm.emit(ChatUiIntent.AgentAction(AgentUiAction.ChangeAnswer("North")))
                vm.emit(ChatUiIntent.AgentAction(AgentUiAction.SubmitAnswer(false)))
                val completed =
                    withTimeout(15_000) { states.first { state -> state.agentState.answerDraft.isBlank() && state.agentState.tasks.any { it.messageId != null && !it.active } } }
                assertTrue(pending in completed.agentState.expandedRunIds)
            }
            val messages = mKoin.get<AppDatabase>().getChatMessageDao().getMessagesBySessionId(id)
            assertEquals(1, messages.count { it.source == ChatMessage.Source.User })
            assertEquals("The party heads north.", messages.last().content)
            assertEquals(2, server.requests.size)
            assertTrue(server.requests.last().toString().contains("North"))
        }
    }

    @Test
    fun groupChatRoutesQuestionAndAnswerThroughTheSharedPanel(): Unit = runBlocking {
        server().use { server ->
            configure(server)
            val characters = mKoin.get<AppDatabase>().getCharacterDao()
            mOtherCharacter = characters.insertOrReplace(
                requireNotNull(characters.getCharacterById(mCharacter)).copy(
                    id = 0,
                    name = "Other Agent fixture"
                )
            )
            mGroup = mGroups.createSession(
                title = "Agent group flow",
                characterIds = listOf(mCharacter, mOtherCharacter),
                activationStrategy = GroupChatSession.ActivationStrategy.Manual,
                allowSelfResponses = false,
                userPersonaId = mKoin.get<UserPersonaRepository>().active().id
            )
            val conversation = ConversationRef.Group(mGroup)
            mRuns.saveSettings(conversation, true, null)
            withModel(::GroupChatViewModel) { vm ->
                val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
                vm.emit(GroupChatUiIntent.Init(mGroup.toString()))
                withTimeout(15_000) { states.first() }
                vm.emit(GroupChatUiIntent.SelectSpeaker(mCharacter))
                vm.emit(GroupChatUiIntent.ChangeInputDraft("Continue the story."))
                vm.emit(GroupChatUiIntent.SendMessage)
                val waiting =
                    withTimeout(15_000) { states.first { it.agentState.question != null && it.conversationState.generationState == GroupChatGenerationState.Idle } }
                assertFalse(waiting.agentState.question!!.confirmation)
                vm.emit(GroupChatUiIntent.AgentAction(AgentUiAction.ChangeAnswer("North")))
                vm.emit(GroupChatUiIntent.AgentAction(AgentUiAction.SubmitAnswer(false)))
                withTimeout(15_000) { states.first { state -> state.agentState.answerDraft.isBlank() && state.agentState.tasks.any { it.messageId != null && !it.active } } }
            }
            assertEquals(
                "The party heads north.",
                mKoin.get<AppDatabase>().getGroupChatMessageDao().getMessages(mGroup).last().content
            )
            assertEquals(2, server.requests.size)
            mKoin.get<AgentRunManager>().stop(conversation)
        }
    }

    private suspend fun configure(server: AgentHttpServer) {
        mProvider = mKoin.get<LLMRepository>().saveProvider(
            LLMProvider(
                name = "Agent fixture",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = server.baseUrl,
                model = "fixture",
                maxTokens = 256,
                contextTokens = 16000
            )
        )
        AppModel.currentLLMProvider = mProvider
    }

    private fun server() = AgentHttpServer { index, _ ->
        val message = if (index == 1) jsonObject(
            "role" to "assistant",
            "tool_calls" to jsonArray(
                listOf(
                    jsonObject(
                        "id" to "question",
                        "type" to "function",
                        "function" to jsonObject(
                            "name" to "ask_user",
                            "arguments" to "{\"question\":\"Which road?\"}"
                        )
                    )
                )
            )
        ) else jsonObject("role" to "assistant", "content" to "The party heads north.")
        AgentHttpServer.Reply(
            jsonObject(
                "choices" to jsonArray(
                    listOf(
                        jsonObject(
                            "finish_reason" to if (index == 1) "tool_calls" else "stop",
                            "message" to message
                        )
                    )
                )
            ).toString()
        )
    }

    private suspend fun <T : ViewModel> withModel(create: () -> T, execute: suspend (T) -> Unit) {
        val store = ViewModelStore()
        val model =
            withContext(Dispatchers.Main) { create().also { store.put("agent-fixture", it) } }
        try {
            execute(model)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
