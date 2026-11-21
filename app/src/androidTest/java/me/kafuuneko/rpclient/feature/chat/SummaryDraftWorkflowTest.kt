package me.kafuuneko.rpclient.feature.chat

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
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
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.model.SummaryVersion
import me.kafuuneko.rpclient.libs.room.model.toSummaryVersion
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.model.SummaryEditorState
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext

/** 通过真实页面、ViewModel、Room 与回环模型服务验证两种聊天的摘要草稿生命周期。 */
class SummaryDraftWorkflowTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mDatabase = GlobalContext.get().get<AppDatabase>()
    private val mChats = GlobalContext.get().get<ChatRepository>()
    private val mGroups = GlobalContext.get().get<GroupChatRepository>()
    private var mCharacterId = 0L
    private var mChatId = 0L
    private var mGroupId = 0L
    private var mProviderId = 0L
    private lateinit var mServer: SummaryServer
    private var mPreferences: List<Long> = emptyList()

    /** 隔离全局摘要偏好与测试模型，只发送合成消息到设备本机的 HTTP 服务。 */
    @Before
    fun setUp(): Unit = runBlocking {
        // 备份设备偏好，再建立仅用于本测试的模型端点。
        mPreferences = listOf(
            AppModel.currentLLMProvider, AppModel.summaryLLMProvider,
            AppModel.summaryMode.toLong(), AppModel.summaryMaxMessagesPerRequest.toLong()
        )
        mServer = SummaryServer()
        mProviderId = mDatabase.getLLMProviderDao().insertOrReplace(
            LLMProvider(
                name = "Summary draft fixture", providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "http://127.0.0.1:${mServer.port}/v1", model = "gpt-4o-mini",
                apiKey = "fixture", contextTokens = 8192,
            )
        )
        AppModel.currentLLMProvider = mProviderId
        AppModel.summaryLLMProvider = mProviderId
        AppModel.summaryMode = SummaryMode.Raw.persistedValue
        AppModel.summaryMaxMessagesPerRequest = 0
    }

    /** 清理独立数据并恢复偏好，取消服务端等待避免遗留后台线程。 */
    @After
    fun tearDown(): Unit = runBlocking {
        // 先解除网络等待，再恢复偏好和删除独立测试数据。
        if (::mServer.isInitialized) mServer.close()
        if (mPreferences.isNotEmpty()) {
            AppModel.currentLLMProvider = mPreferences[0]
            AppModel.summaryLLMProvider = mPreferences[1]
            AppModel.summaryMode = mPreferences[2].toInt()
            AppModel.summaryMaxMessagesPerRequest = mPreferences[3].toInt()
        }
        if (mGroupId != 0L) mGroups.deleteSession(mGroupId)
        if (mCharacterId != 0L) GlobalContext.get().get<CharacterRepository>()
            .deleteCharacter(mCharacterId)
        if (mProviderId != 0L) mDatabase.getLLMProviderDao().deleteProviderById(mProviderId)
    }

    /** 单聊手动生成与取消不入库，回退精确经过 C、B、A，保存和放弃按当前选中稿生效。 */
    @Test
    fun singleChatUsesDraftHistoryUntilExplicitSave(): Unit = runBlocking {
        mCharacterId = mDatabase.getCharacterDao().insertOrReplace(
            Character(
                name = "Draft character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
            )
        )
        mChatId = mChats.createSessionWithFirstMessage(
            characterId = mCharacterId, title = "Draft fixture", userNote = "",
            lorebookEntryIds = emptyList(), firstMessageContent = null,
        )
        val first =
            mChats.createMessage(mChatId, ChatMessage.Source.User, "First synthetic message", 1L)
        val last =
            mChats.createMessage(mChatId, ChatMessage.Source.User, "Last synthetic message", 2L)
        mChats.saveSummary(mChatId, "A", first)
        // 实际 Activity 承担生命周期，所有业务操作都走公开的 Intent 入口。
        ActivityScenario.launch<ChatActivity>(
            Intent(mContext, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_SESSION_ID, mChatId.toString())
        ).use { scenario ->
            lateinit var vm: ChatViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java] }
            val states = vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
            val workflow = Workflow(
                states = states.map {
                    Snapshot(
                        summary = it.settingsState.summary,
                        settings = it.page == ChatPage.Settings,
                        summarizing = it.dialogState == ChatDialogState.Summarizing,
                        confirmDiscard = it.dialogState == ChatDialogState.UnsavedSettingsChanges,
                    )
                },
                emit = { action ->
                    vm.emit(
                        when (action) {
                            Action.Open -> ChatUiIntent.OpenChatSettings
                            Action.Generate -> ChatUiIntent.SummarizeNow
                            Action.Edit -> ChatUiIntent.ChangeSummary("D")
                            Action.Name -> ChatUiIntent.ChangeUserName("Draft persona name")
                            Action.Cancel -> ChatUiIntent.CancelSummary
                            Action.Restore -> ChatUiIntent.RestorePreviousSummary
                            Action.Save -> ChatUiIntent.SaveSettings
                            Action.Back -> ChatUiIntent.Back
                            Action.Discard -> ChatUiIntent.DiscardSettingsChanges
                        }
                    )
                },
                persisted = { mChats.getLatestSummary(mChatId).toSummaryVersion() },
                resume = {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                },
            )
            exercise(workflow, last)
        }
    }

    /** 群聊复用同一验收流程，后台恢复必须同时保留版本栈和未保存基线。 */
    @Test
    fun groupChatUsesTheSameDraftHistoryAndSaveBoundary(): Unit = runBlocking {
        mGroupId = mDatabase.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(
                title = "Draft fixture", createTime = 1L, latestTime = 1L,
            )
        )
        mCharacterId = mDatabase.getCharacterDao().insertOrReplace(
            Character(
                name = "Draft group member",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
            )
        )
        mDatabase.getGroupChatMemberDao().insertOrReplace(
            GroupChatMember(
                sessionId = mGroupId, characterId = mCharacterId, sortOrder = 0,
            )
        )
        // 空名称快照按旧数据规则使用当前人设名称，验证表单预览确实参与请求。
        val first = mGroups.createMessage(
            mGroupId,
            GroupChatMessage.Source.User,
            "First synthetic message",
            null,
            "",
            createTime = 1L
        )
        val last = mGroups.createMessage(
            mGroupId,
            GroupChatMessage.Source.User,
            "Last synthetic message",
            null,
            "",
            createTime = 2L
        )
        mGroups.saveSummary(mGroupId, "A", first)
        // 保留一个有效成员，同时验证后续切换 Classic 模式的真实 Prompt 构建。
        ActivityScenario.launch<GroupChatActivity>(
            Intent(mContext, GroupChatActivity::class.java)
                .putExtra(GroupChatActivity.EXTRA_SESSION_ID, mGroupId.toString())
        ).use { scenario ->
            lateinit var vm: GroupChatViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[GroupChatViewModel::class.java] }
            val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
            val workflow = Workflow(
                states = states.map {
                    Snapshot(
                        summary = it.settingsState.summary,
                        settings = it.page == GroupChatPage.Settings,
                        summarizing = it.dialogState == GroupChatDialogState.Summarizing,
                        confirmDiscard = it.dialogState == GroupChatDialogState.UnsavedSettingsChanges,
                    )
                },
                emit = { action ->
                    vm.emit(
                        when (action) {
                            Action.Open -> GroupChatUiIntent.OpenSettings
                            Action.Generate -> GroupChatUiIntent.SummarizeNow
                            Action.Edit -> GroupChatUiIntent.ChangeSummary("D")
                            Action.Name -> GroupChatUiIntent.ChangeUserName("Draft persona name")
                            Action.Cancel -> GroupChatUiIntent.CancelSummary
                            Action.Restore -> GroupChatUiIntent.RestorePreviousSummary
                            Action.Save -> GroupChatUiIntent.SaveSettings
                            Action.Back -> GroupChatUiIntent.Back
                            Action.Discard -> GroupChatUiIntent.DiscardSettingsChanges
                        }
                    )
                },
                persisted = { mGroups.getGroupChatData(mGroupId)?.summary.toSummaryVersion() },
                resume = {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                },
            )
            exercise(workflow, last)
        }
    }

    /** 同一合成模型序列覆盖生成、取消、逐版恢复、保存及放弃恢复稿。 */
    private suspend fun exercise(workflow: Workflow, latestMessageId: Long) = withTimeout(30_000) {
        workflow.await { !it.settings }
        workflow.emit(Action.Open)
        workflow.await { it.settings }
        workflow.emit(Action.Name)
        for (content in listOf("B", "C")) {
            workflow.emit(Action.Generate)
            workflow.await { it.summary.text == content && !it.summarizing }
            assertEquals("A", workflow.persisted().content)
        }
        // 连续输入与后台恢复之后，取消第三次请求仍应完整保留 D、C、B、A。
        workflow.emit(Action.Edit)
        workflow.await { it.summary.text == "D" }
        workflow.resume()
        assertEquals("D", workflow.await { it.settings }.summary.text)
        workflow.emit(Action.Generate)
        assertTrue(mServer.cancelRequest.await(5, TimeUnit.SECONDS))
        workflow.emit(Action.Cancel)
        val canceled = workflow.await { !it.summarizing }
        assertEquals("D", canceled.summary.text)
        for (expected in listOf("C", "B", "A")) {
            workflow.emit(Action.Restore)
            workflow.await { it.summary.text == expected }
        }
        assertFalse(workflow.await { it.summary.text == "A" }.summary.hasChanges)
        assertEquals("A", workflow.persisted().content)
        // 取消的请求完成后释放测试服务；下一次完整结果才允许成为保存目标。
        mServer.releaseCanceled.countDown()
        AppModel.summaryMode = SummaryMode.Classic.persistedValue
        workflow.emit(Action.Generate)
        workflow.await { it.summary.text == "E" && !it.summarizing }
        workflow.emit(Action.Save)
        workflow.await { !it.settings }
        val saved = workflow.persisted()
        assertEquals("E", saved.content)
        assertEquals(latestMessageId, saved.coveredMessageId)
        workflow.emit(Action.Open)
        workflow.await { it.settings }
        workflow.emit(Action.Restore)
        workflow.await { it.summary.text == "A" }
        assertEquals(saved, workflow.persisted())
        workflow.emit(Action.Back)
        workflow.await { it.confirmDiscard }
        workflow.emit(Action.Discard)
        workflow.await { !it.settings }
        assertEquals(saved, workflow.persisted())
        // 请求必须采用表单里的未保存名称，且确实完成了所有真实 HTTP 调用。
        mServer.finished.get(5, TimeUnit.SECONDS)
        assertTrue(mServer.requests.all { it.contains("Draft persona name") })
    }

    private enum class Action { Open, Generate, Edit, Name, Cancel, Restore, Save, Back, Discard }

    private data class Snapshot(
        val summary: SummaryEditorState, val settings: Boolean,
        val summarizing: Boolean, val confirmDiscard: Boolean
    )

    private data class Workflow(
        val states: Flow<Snapshot>, val emit: (Action) -> Unit,
        val persisted: suspend () -> SummaryVersion, val resume: () -> Unit
    ) {
        /** 等待业务状态而非固定延时，避免网络与 UI 调度导致偶发失败。 */
        suspend fun await(predicate: (Snapshot) -> Boolean): Snapshot = states.first(predicate)
    }

    /** 本机模型按序返回 B、C、可取消请求和 E，不访问任何外部服务。 */
    private class SummaryServer : AutoCloseable {
        private val mSocket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = mSocket.localPort
        val cancelRequest = CountDownLatch(1)
        val releaseCanceled = CountDownLatch(1)
        val requests = mutableListOf<String>()
        val finished = CompletableFuture<Unit>()

        init {
            thread(isDaemon = true, name = "summary-draft-fixture") {
                try {
                    // 第三次请求在模型返回前等待取消，其余请求返回完整普通响应。
                    for ((index, content) in listOf("B", "C", "Ignored", "E").withIndex()) {
                        mSocket.accept().use { socket ->
                            socket.soTimeout = 5_000
                            val input = socket.getInputStream().source().buffer()
                            assertTrue(input.readUtf8LineStrict().startsWith("POST "))
                            val headers = generateSequence {
                                input.readUtf8LineStrict().takeIf(String::isNotEmpty)
                            }
                                .associate {
                                    it.substringBefore(':').lowercase() to it.substringAfter(':')
                                        .trim()
                                }
                            requests.add(
                                input.readUtf8(
                                    headers.getValue("content-length").toLong()
                                )
                            )
                            if (index == 2) {
                                cancelRequest.countDown()
                                check(releaseCanceled.await(10, TimeUnit.SECONDS))
                            } else {
                                val body =
                                    """{"choices":[{"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}]}""".toByteArray()
                                socket.getOutputStream().apply {
                                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    write(body)
                                }
                            }
                        }
                    }
                    finished.complete(Unit)
                } catch (error: Throwable) {
                    finished.completeExceptionally(error)
                }
            }
        }

        /** 解除服务端等待并关闭监听口，测试失败时也不会留下工作线程。 */
        override fun close() {
            releaseCanceled.countDown()
            mSocket.close()
        }
    }
}
