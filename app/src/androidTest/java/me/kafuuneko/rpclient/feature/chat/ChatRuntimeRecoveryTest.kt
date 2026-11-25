package me.kafuuneko.rpclient.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.SummaryMode
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 通过真实数据库、Intent 队列和可暂停的回环模型响应验证异步恢复边界。 */
class ChatRuntimeRecoveryTest {
    private val mDatabase = GlobalContext.get().get<AppDatabase>()
    private val mChats = GlobalContext.get().get<ChatRepository>()
    private val mGroups = GlobalContext.get().get<GroupChatRepository>()
    private val mCharacters = GlobalContext.get().get<CharacterRepository>()
    private val mCharacterIds = mutableListOf<Long>()
    private val mGroupIds = mutableListOf<Long>()

    /** 本组仅使用没有外部文件和专属模型的合成角色。 */
    @Before
    fun setUp(): Unit = runBlocking {
        repeat(3) { index ->
            mCharacterIds += mDatabase.getCharacterDao().insertOrReplace(
                Character(
                    name = "Runtime fixture $index", avatar = "", characterTags = "[]",
                    description = "", personality = "", scenario = "", firstMessages = "",
                    examplesOfDialogue = "", postHistoryInstructions = "",
                ),
            )
        }
    }

    /** 仅删除测试拥有的会话和角色。 */
    @After
    fun tearDown(): Unit = runBlocking {
        mGroupIds.forEach { mGroups.deleteSession(it) }
        mCharacterIds.forEach { mCharacters.deleteCharacter(it) }
    }

    /** 库内仍为空占位时恢复页面，已流出的正文不能清空，结束后仍能回到空闲状态。 */
    @Test
    fun groupResumePreservesStreamingTextAndEventuallyFinishes(): Unit = runBlocking {
        PausedModelServer(streaming = true).use { server ->
            withProvider(server) {
                val id = createGroup()
                withModel(::GroupChatViewModel) { vm ->
                    val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
                    vm.emit(GroupChatUiIntent.Init(id.toString()))
                    withTimeout(15_000) { states.first() }
                    vm.emit(GroupChatUiIntent.SelectSpeaker(mCharacterIds.first()))
                    vm.emit(GroupChatUiIntent.ChangeInputDraft("Hello"))
                    vm.emit(GroupChatUiIntent.SendMessage)
                    val partial = withTimeout(15_000) {
                        states.first { it.conversationState.messages.lastOrNull()?.content == "Partial" }
                    }
                    val replyId = partial.conversationState.messages.last().id
                    // 用外部标题变化确认恢复读取已经发布，而非只检查刷新前的旧状态。
                    val session = requireNotNull(mGroups.getSessionById(id))
                    mGroups.updateSession(session.copy(title = "Refreshed during stream"))
                    vm.emit(GroupChatUiIntent.Resume)
                    val refreshed = withTimeout(15_000) {
                        states.first { it.title == "Refreshed during stream" }
                    }
                    assertEquals("Partial", refreshed.conversationState.messages.last().content)
                    assertTrue(refreshed.conversationState.generationState is GroupChatGenerationState.Generating)
                    // 恢复期间仍允许正常收尾，最终正文必须和落库结果一致。
                    server.finish()
                    val finished = withTimeout(15_000) {
                        states.first { it.conversationState.generationState == GroupChatGenerationState.Idle }
                    }
                    assertEquals(
                        "Partial reply",
                        finished.conversationState.messages.last().content
                    )
                    assertEquals("Partial reply", mGroups.getMessageById(replyId)?.content)
                    // 外部删除会话后恢复应结束页面，不能静默保留已失效的会话。
                    mGroups.deleteSession(id)
                    vm.emit(GroupChatUiIntent.Resume)
                    withTimeout(15_000) { vm.uiStateFlow.first { it is GroupChatUiState.Finished } }
                }
            }
        }
    }

    /** 群聊摘要期间排队的设置操作不能替换弹窗或写库，取消后表单继续可编辑。 */
    @Test
    fun groupSummaryBlocksQueuedDialogsAndMemberMutationsUntilCancelled(): Unit = runBlocking {
        PausedModelServer(streaming = false).use { server ->
            withProvider(server) {
                val id = createGroup()
                mGroups.createMessage(
                    id,
                    GroupChatMessage.Source.User,
                    "Summary source",
                    null,
                    "User"
                )
                withModel(::GroupChatViewModel) { vm ->
                    val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
                    vm.emit(GroupChatUiIntent.Init(id.toString()))
                    withTimeout(15_000) { states.first() }
                    vm.emit(GroupChatUiIntent.SummarizeNow)
                    withTimeout(15_000) { states.first { it.dialogState == GroupChatDialogState.Summarizing } }
                    server.awaitRequest()
                    // 复制事件作为串行队列屏障，确保断言覆盖所有排队的旧操作。
                    val drained = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(15_000) { vm.viewEventFlow.first() }
                    }
                    vm.emit(GroupChatUiIntent.ShowSessionLoreDialog)
                    vm.emit(GroupChatUiIntent.ShowAddMembersDialog)
                    vm.emit(GroupChatUiIntent.ShowAddLorebooksDialog)
                    vm.emit(GroupChatUiIntent.ToggleLorebookEntry(-1L))
                    vm.emit(GroupChatUiIntent.RemoveMember(mCharacterIds.last()))
                    vm.emit(GroupChatUiIntent.MoveMember(mCharacterIds.first(), 1))
                    vm.emit(
                        GroupChatUiIntent.ReorderMember(
                            mCharacterIds.first(),
                            mCharacterIds.last()
                        )
                    )
                    vm.emit(GroupChatUiIntent.CommitMemberOrder)
                    vm.emit(GroupChatUiIntent.CopyPromptItem("Queue barrier"))
                    drained.await()
                    val blocked = vm.uiStateFlow.value as GroupChatUiState.Normal
                    assertEquals(GroupChatDialogState.Summarizing, blocked.dialogState)
                    assertEquals(mCharacterIds, mGroups.getMembers(id).map { it.character.id })
                    assertEquals("[]", mGroups.getSessionById(id)?.lorebookEntrySet)
                    vm.emit(GroupChatUiIntent.CancelSummary)
                    withTimeout(15_000) { states.first { it.dialogState == GroupChatDialogState.None } }
                    vm.emit(GroupChatUiIntent.ChangeTitle("Editable after cancellation"))
                    withTimeout(15_000) { states.first { it.settingsState.titleDraft == "Editable after cancellation" } }
                }
            }
        }
    }

    /** 单聊摘要也必须保留专用取消入口，快速连续设置操作不能抢占模态状态。 */
    @Test
    fun singleSummaryBlocksQueuedLorebookActionsUntilCancelled(): Unit = runBlocking {
        PausedModelServer(streaming = false).use { server ->
            withProvider(server) {
                val id = mChats.createSessionWithFirstMessage(
                    characterId = mCharacterIds.first(), title = "Summary fixture", userNote = "",
                    lorebookEntryIds = emptyList(), firstMessageContent = null,
                )
                mChats.createMessage(id, ChatMessage.Source.User, "Summary source")
                withModel(::ChatViewModel) { vm ->
                    val states = vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
                    vm.emit(ChatUiIntent.Init(id.toString()))
                    withTimeout(15_000) { states.first() }
                    vm.emit(ChatUiIntent.SummarizeNow)
                    withTimeout(15_000) { states.first { it.dialogState == ChatDialogState.Summarizing } }
                    server.awaitRequest()
                    // 旧弹窗和世界书意图被消费后，摘要弹窗仍必须归原任务所有。
                    val drained = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(15_000) { vm.viewEventFlow.first() }
                    }
                    vm.emit(ChatUiIntent.ShowSessionLoreDialog)
                    vm.emit(ChatUiIntent.ShowAddLorebooksDialog)
                    vm.emit(ChatUiIntent.ToggleSessionLoreEntry(-1L))
                    vm.emit(ChatUiIntent.ToggleSessionLorebook(-1L))
                    vm.emit(ChatUiIntent.DeleteSessionClick)
                    vm.emit(ChatUiIntent.CopyPromptItem("Queue barrier"))
                    drained.await()
                    val blocked = vm.uiStateFlow.value as ChatUiState.Normal
                    assertEquals(ChatDialogState.Summarizing, blocked.dialogState)
                    assertEquals(ChatPage.Settings, blocked.page)
                    vm.emit(ChatUiIntent.CancelSummary)
                    withTimeout(15_000) { states.first { it.dialogState == ChatDialogState.None } }
                    vm.emit(ChatUiIntent.ChangeTitle("Editable after cancellation"))
                    withTimeout(15_000) { states.first { it.settingsState.titleDraft == "Editable after cancellation" } }
                }
            }
        }
    }

    /** 创建手动发言且没有自动循环的独立群聊。 */
    private suspend fun createGroup(): Long = mGroups.createSession(
        title = "Runtime fixture", characterIds = mCharacterIds,
        activationStrategy = GroupChatSession.ActivationStrategy.Manual, allowSelfResponses = false,
    ).also { mGroupIds += it }

    /** 使用回环服务替代外部模型，退出时恢复全局模型选择与生成偏好。 */
    private suspend fun withProvider(server: PausedModelServer, block: suspend () -> Unit) {
        val originalProvider = AppModel.currentLLMProvider
        val originalSummaryProvider = AppModel.summaryLLMProvider
        val originalStream = AppModel.streamEnabled
        val originalAutoSummary = AppModel.autoSummaryEnabled
        val originalSummaryMode = AppModel.summaryMode
        val id = mDatabase.getLLMProviderDao().insertOrReplace(
            LLMProvider(
                name = "Runtime fixture", providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible, baseUrl = server.baseUrl,
                model = "fixture", apiKey = "fixture-key", contextTokens = 32_768,
            ),
        )
        // 测试请求只访问本机，禁止自动摘要追加未预期的第二次网络请求。
        AppModel.currentLLMProvider = id
        AppModel.summaryLLMProvider = id
        AppModel.streamEnabled = true
        AppModel.autoSummaryEnabled = false
        AppModel.summaryMode = SummaryMode.Raw.persistedValue
        try {
            block()
        } finally {
            AppModel.currentLLMProvider = originalProvider
            AppModel.summaryLLMProvider = originalSummaryProvider
            AppModel.streamEnabled = originalStream
            AppModel.autoSummaryEnabled = originalAutoSummary
            AppModel.summaryMode = originalSummaryMode
            mDatabase.getLLMProviderDao().deleteProviderById(id)
        }
    }

    /** 在主线程绑定独立 Store，测试退出时等待页面任务释放。 */
    private suspend fun <T : ViewModel> withModel(create: () -> T, block: suspend (T) -> Unit) {
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) { create().also { store.put("fixture", it) } }
        try {
            block(model)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    /** 在收到请求或首个 SSE 增量后暂停，测试显式允许时才提交最终响应。 */
    private class PausedModelServer(private val streaming: Boolean) : AutoCloseable {
        private val mServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val mReceived = CountDownLatch(1)
        private val mRelease = CountDownLatch(1)
        private val mCompleted = CompletableFuture<Unit>()
        val baseUrl = "http://127.0.0.1:${mServer.localPort}/v1"

        init {
            thread(isDaemon = true, name = "chat-runtime-fixture") {
                try {
                    serve()
                    mCompleted.complete(Unit)
                } catch (error: Throwable) {
                    mCompleted.completeExceptionally(error)
                }
            }
        }

        /** 真实请求已经到达，避免把准备阶段误当作正在等待模型。 */
        fun awaitRequest() {
            assertTrue("Model request did not arrive", mReceived.await(15, TimeUnit.SECONDS))
        }

        /** 放行最后增量并检查回环服务没有发生协议写入错误。 */
        fun finish() {
            mRelease.countDown()
            mCompleted.get(15, TimeUnit.SECONDS)
        }

        /** 释放等待中的响应和监听端口，取消测试不再等待服务端正文。 */
        override fun close() {
            mRelease.countDown()
            mServer.close()
        }

        /** 消费完整请求后保持连接开放，区分未落库增量与最终可持久化回复。 */
        private fun serve() {
            mServer.accept().use { socket ->
                socket.soTimeout = 15_000
                val input = socket.getInputStream().source().buffer()
                assertEquals("POST /v1/chat/completions HTTP/1.1", input.readUtf8LineStrict())
                val headers =
                    generateSequence { input.readUtf8LineStrict().takeIf(String::isNotEmpty) }
                        .associate {
                            it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                        }
                input.skip(headers.getValue("content-length").toLong())
                val output = socket.getOutputStream()
                if (streaming) {
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write("data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\n".toByteArray())
                    output.flush()
                }
                mReceived.countDown()
                // 摘要用例在等待期间取消；流式用例显式放行完整的结束标记。
                check(mRelease.await(15, TimeUnit.SECONDS)) { "Fixture response was not released" }
                if (streaming) {
                    output.write("data: {\"choices\":[{\"delta\":{\"content\":\" reply\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toByteArray())
                }
            }
        }
    }
}
