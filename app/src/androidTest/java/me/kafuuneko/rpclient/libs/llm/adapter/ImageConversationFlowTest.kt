package me.kafuuneko.rpclient.libs.llm.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chat.ChatViewModel
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.common.media.MessageImageAction
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.CharacterLLMProviderAssociation
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** 在真实页面、MVI 和持久化链路上回归发送与失败重试，模型服务仅使用设备本机合成响应。 */
@RunWith(AndroidJUnit4::class)
class ImageConversationFlowTest {
    @Test
    fun chatPureImageRetryHistoryAndSummaryUseOnePersistedUserMessage() = runBlocking {
        withFixture { fixture ->
            fixture.server.failNext = true
            val id = fixture.db.getChatSessionDao().insertOrReplace(ChatSession(characterId = fixture.characters.first(),
                createTime = 1, latestTime = 1, lorebookEntrySet = "[]", title = "Image flow test", userNote = "", userName = "User", userDescription = ""))
            val repository = GlobalContext.get().get<ChatRepository>()
            ActivityScenario.launch<ChatActivity>(Intent(fixture.context, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_SESSION_ID, id.toString())).use { scenario ->
                lateinit var vm: ChatViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java] }
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first() }
                vm.emit(ChatUiIntent.ImageAction(MessageImageAction.Picked(listOf(Uri.fromFile(fixture.image)))))
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first { it.imageState.draft.size == 1 && !it.imageState.processing } }
                // Activity 配置重建复用 ViewModel，内存中的文字和图片草稿仍然保留。
                vm.emit(ChatUiIntent.ChangeInputDraft("unsent draft"))
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.inputDraft == "unsent draft" }
                scenario.recreate()
                scenario.onActivity { assertTrue(vm === ViewModelProvider(it)[ChatViewModel::class.java]) }
                val draftState = vm.uiStateFlow.value as ChatUiState.Normal
                assertEquals("unsent draft", draftState.conversationState.inputDraft)
                assertEquals(1, draftState.imageState.draft.size)
                vm.emit(ChatUiIntent.ChangeInputDraft(""))
                vm.emit(ChatUiIntent.SendMessage)
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first { it.conversationState.generationState is ChatGenerationState.Failed } }
                vm.emit(ChatUiIntent.DismissDialog)
                vm.emit(ChatUiIntent.RetryImageReply)
                await { repository.getAllChatMessagesBySessionId(id).any { it.source == ChatMessage.Source.Char } }
                assertEquals(1, repository.getAllChatMessagesBySessionId(id).count { it.source == ChatMessage.Source.User })
                assertTrue(fixture.server.requests.take(2).all { "data:image/" in it })
                // 后续文字轮次仍然携带历史图片；手动摘要读取图片，成功后不再永久携带旧图。
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState == ChatGenerationState.Idle }
                vm.emit(ChatUiIntent.ChangeInputDraft("Continue"))
                vm.emit(ChatUiIntent.SendMessage)
                await { repository.getAllChatMessagesBySessionId(id).count { it.source == ChatMessage.Source.Char } == 2 }
                assertTrue("data:image/" in fixture.server.requests.last())
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState == ChatGenerationState.Idle }
                vm.emit(ChatUiIntent.SummarizeNow)
                await { repository.getLatestSummary(id) != null }
                assertTrue("data:image/" in fixture.server.requests.last())
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.dialogState == ChatDialogState.None }
                delay(400)
                screenshot("chat-images.png")
                vm.emit(ChatUiIntent.ChangeInputDraft("After summary"))
                vm.emit(ChatUiIntent.SendMessage)
                await { repository.getAllChatMessagesBySessionId(id).count { it.source == ChatMessage.Source.Char } == 3 }
                assertFalse("data:image/" in fixture.server.requests.last())
                assertEquals(1, repository.getMessagesWithImages(repository.getAllChatMessagesBySessionId(id).map { it.id }).sumOf { it.images.size })
                scenario.recreate()
            }
        }
    }

    @Test
    fun groupPureImageRunsTwoSpeakersWithTheSameAttachmentInSmallHistoryWindow() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<GroupChatRepository>()
            val id = fixture.db.getGroupChatSessionDao().insertOrReplace(GroupChatSession(title = "Group image test", createTime = 1,
                latestTime = 1, userName = "User", userDescription = "", activationStrategy = GroupChatSession.ActivationStrategy.List))
            fixture.characters.forEachIndexed { index, character -> fixture.db.getGroupChatMemberDao()
                .insertOrReplace(GroupChatMember(id, character, index)) }
            // 单条历史窗口也必须为第二位角色保留触发本轮的图片消息。
            AppModel.maxPromptHistoryMessages = 1
            ActivityScenario.launch<GroupChatActivity>(Intent(fixture.context, GroupChatActivity::class.java)
                .putExtra(GroupChatActivity.EXTRA_SESSION_ID, id.toString())).use { scenario ->
                lateinit var vm: GroupChatViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[GroupChatViewModel::class.java] }
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>().first() }
                vm.emit(GroupChatUiIntent.ImageAction(MessageImageAction.Picked(listOf(Uri.fromFile(fixture.image)))))
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>().first { it.imageState.draft.size == 1 && !it.imageState.processing } }
                // Activity 配置重建复用 ViewModel，内存中的文字和图片草稿仍然保留。
                vm.emit(GroupChatUiIntent.ChangeInputDraft("unsent draft"))
                await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.inputDraft == "unsent draft" }
                scenario.recreate()
                scenario.onActivity { assertTrue(vm === ViewModelProvider(it)[GroupChatViewModel::class.java]) }
                val draftState = vm.uiStateFlow.value as GroupChatUiState.Normal
                assertEquals("unsent draft", draftState.conversationState.inputDraft)
                assertEquals(1, draftState.imageState.draft.size)
                vm.emit(GroupChatUiIntent.ChangeInputDraft(""))
                vm.emit(GroupChatUiIntent.SendMessage)
                await { repository.getGroupChatData(id)!!.messages.count { it.source == GroupChatMessage.Source.Character } == 2 }
                await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState == GroupChatGenerationState.Idle }
                assertEquals(1, repository.getGroupChatData(id)!!.messages.count { it.source == GroupChatMessage.Source.User })
                assertEquals(2, fixture.server.requests.size)
                assertTrue(fixture.server.requests.all { "data:image/" in it })
                screenshot("group-images.png")
            }
            repository.deleteSession(id)
        }
    }

    @Test
    fun stoppingAnImageStreamClosesTheRequestAndPreservesTheUserImage() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<ChatRepository>()
            val id = fixture.db.getChatSessionDao().insertOrReplace(ChatSession(characterId = fixture.characters.first(),
                createTime = 1, latestTime = 1, lorebookEntrySet = "[]", title = "Cancel image test", userNote = "", userName = "User", userDescription = ""))
            AppModel.streamEnabled = true
            fixture.server.stallNext = true
            ActivityScenario.launch<ChatActivity>(Intent(fixture.context, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_SESSION_ID, id.toString())).use { scenario ->
                lateinit var vm: ChatViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[ChatViewModel::class.java] }
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first() }
                vm.emit(ChatUiIntent.ImageAction(MessageImageAction.Picked(listOf(Uri.fromFile(fixture.image)))))
                withTimeout(30_000) { vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first { it.imageState.draft.size == 1 && !it.imageState.processing } }
                // 服务端保持 SSE 开启却不返回下一行，停止必须主动关闭 socket。
                vm.emit(ChatUiIntent.SendMessage)
                await { fixture.server.requests.isNotEmpty() }
                vm.emit(ChatUiIntent.StopGeneration)
                withTimeout(5_000) {
                    while ((vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState != ChatGenerationState.Idle) delay(50)
                    while (File(fixture.context.cacheDir, "image-requests").listFiles().orEmpty().isNotEmpty()) delay(50)
                }
                val messages = repository.getAllChatMessagesBySessionId(id)
                assertEquals(1, messages.count { it.source == ChatMessage.Source.User })
                assertEquals(1, repository.getMessagesWithImages(messages.map { it.id }).sumOf { it.images.size })
            }
        }
    }

    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(30_000) {
        while (!condition()) delay(100)
    }

    /** 只截取本测试创建的合成对话，供布局验收。 */
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    /** 测试仅创建并清理自己拥有的实体，所有偏好在 finally 中恢复。 */
    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val koin = GlobalContext.get()
        val db = koin.get<AppDatabase>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = LocalModelServer()
        val llm = koin.get<LLMRepository>()
        val providerId = llm.saveProvider(LLMProvider(name = "Image flow fixture", providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible, baseUrl = "http://127.0.0.1:${server.port}", model = "fixture", contextTokens = 100_000))
        val characters = mutableListOf<Long>()
        val image = File(context.cacheDir, "image-flow-fixture.png")
        val old = listOf(AppModel.currentLLMProvider, AppModel.summaryLLMProvider, AppModel.streamEnabled,
            AppModel.autoSummaryEnabled, AppModel.maxPromptHistoryMessages)
        try {
            repeat(2) { index ->
                val id = db.getCharacterDao().insertOrReplace(Character(name = "Fixture $index", avatar = "", characterTags = "[]",
                    description = "Test character", personality = "", scenario = "", firstMessages = "", examplesOfDialogue = "", postHistoryInstructions = ""))
                characters += id
                db.getCharacterLLMProviderAssociationDao().insertOrReplace(CharacterLLMProviderAssociation(id, providerId))
            }
            Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.BLUE)
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            AppModel.currentLLMProvider = providerId
            AppModel.summaryLLMProvider = providerId
            AppModel.streamEnabled = false
            AppModel.autoSummaryEnabled = false
            AppModel.maxPromptHistoryMessages = 100
            block(Fixture(context, db, characters, server, image))
        } finally {
            AppModel.currentLLMProvider = old[0] as Long
            AppModel.summaryLLMProvider = old[1] as Long
            AppModel.streamEnabled = old[2] as Boolean
            AppModel.autoSummaryEnabled = old[3] as Boolean
            AppModel.maxPromptHistoryMessages = old[4] as Int
            characters.forEach { koin.get<CharacterRepository>().deleteCharacter(it) }
            llm.deleteProvider(providerId)
            image.delete()
            server.close()
        }
    }

    private data class Fixture(val context: Context, val db: AppDatabase,
        val characters: List<Long>, val server: LocalModelServer, val image: File)

    /** 只监听设备回环地址，返回固定文本并记录合成测试请求。 */
    private class LocalModelServer : AutoCloseable {
        private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val requests = CopyOnWriteArrayList<String>()
        @Volatile var failNext = false
        @Volatile var stallNext = false
        private val worker = thread(isDaemon = true, name = "image-model-fixture") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val input = DataInputStream(client.getInputStream())
                        var length = 0
                        while (true) {
                            val line = ByteArrayOutputStream()
                            while (true) { val byte = input.read(); if (byte < 0 || byte == 10) break; line.write(byte) }
                            val header = line.toString("UTF-8").trim()
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                        }
                        val body = ByteArray(length)
                        input.readFully(body)
                        requests += body.toString(Charsets.UTF_8)
                        if (stallNext) {
                            stallNext = false
                            client.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                                flush()
                            }
                            client.soTimeout = 10_000
                            input.read()
                            return@use
                        }
                        val failure = failNext.also { failNext = false }
                        val response = if (failure) """{"error":{"message":"fixture failure"}}""" else """{"choices":[{"message":{"content":"A blue square is visible."},"finish_reason":"stop"}]}"""
                        val bytes = response.toByteArray()
                        client.getOutputStream().apply {
                            write("HTTP/1.1 ${if (failure) "400 Bad Request" else "200 OK"}\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes); flush()
                        }
                    }
                } catch (_: IOException) { if (socket.isClosed) break }
            }
        }
        override fun close() { socket.close(); worker.join(1000) }
    }
}
