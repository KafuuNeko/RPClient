package me.kafuuneko.rpclient.libs.llm.adapter

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.TimeoutCancellationException
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
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.media.ImageSendSettings
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
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
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** 在真实页面、MVI 和持久化链路上回归发送与失败重试，模型服务仅使用设备本机合成响应。 */
@RunWith(AndroidJUnit4::class)
class ImageConversationFlowTest {
    @Test
    fun chatPureImageRetryHistoryAndSummaryUseOnePersistedUserMessage() = runBlocking {
        withFixture { fixture ->
            fixture.server.failNext = true
            val id = fixture.db.getChatSessionDao().insertOrReplace(
                data = ChatSession(
                    characterId = fixture.characters.first(),
                    createTime = 1,
                    latestTime = 1,
                    lorebookEntrySet = "[]",
                    title = "Image flow test",
                    userNote = "",
                    userName = "User",
                    userDescription = "",
                ),
            )
            val repository = GlobalContext.get().get<ChatRepository>()
            ActivityScenario.launch<ChatActivity>(
                Intent(fixture.context, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_SESSION_ID, id.toString()),
            ).use { scenario ->
                lateinit var vm: ChatViewModel
                scenario.onActivity { chatActivity ->
                    vm = ViewModelProvider(chatActivity)[ChatViewModel::class.java]
                }
                withTimeout(timeMillis = 30_000) {
                    vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first()
                }
                vm.emit(ChatUiIntent.ImageAction(MessageImageAction.Choose(editing = false)))
                withTimeout(timeMillis = 30_000) {
                    vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
                        .first { normal -> normal.imageState.draft.size == 1 && !normal.imageState.loadState.isBusy }
                }
                // Activity 配置重建复用 ViewModel，内存中的文字和图片草稿仍然保留。
                vm.emit(ChatUiIntent.ChangeInputDraft("unsent draft"))
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.inputDraft == "unsent draft" }
                scenario.recreate()
                scenario.onActivity { chatActivity ->
                    assertTrue(
                        vm === ViewModelProvider(
                            chatActivity
                        )[ChatViewModel::class.java]
                    )
                }
                val draftState = vm.uiStateFlow.value as ChatUiState.Normal
                assertEquals("unsent draft", draftState.conversationState.inputDraft)
                assertEquals(1, draftState.imageState.draft.size)
                vm.emit(ChatUiIntent.ChangeInputDraft(""))
                val provider =
                    fixture.db.getLLMProviderDao().getProviderById(AppModel.currentLLMProvider)!!
                fixture.db.getLLMProviderDao()
                    .update(provider.copy(imageInputSetting = ImageInputSetting.Unsupported))
                vm.emit(ChatUiIntent.SendMessage)
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState is ChatGenerationState.Failed }
                val rejected = vm.uiStateFlow.value as ChatUiState.Normal
                assertFalse((rejected.conversationState.generationState as ChatGenerationState.Failed).canRetryReply)
                assertEquals(draftState.imageState.draft, rejected.imageState.draft)
                assertTrue(fixture.server.requests.isEmpty())
                fixture.db.getLLMProviderDao().update(provider)
                vm.emit(ChatUiIntent.SendMessage)
                await { ((vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState as? ChatGenerationState.Failed)?.canRetryReply == true }
                val committed = repository.getMessagesWithImages(
                    repository.getAllChatMessagesBySessionId(id).map { message -> message.id })
                vm.emit(ChatUiIntent.DismissDialog)
                vm.emit(ChatUiIntent.RetryImageReply)
                await {
                    repository.getAllChatMessagesBySessionId(id)
                        .any { message -> message.source == ChatMessage.Source.Char }
                }
                assertEquals(
                    1,
                    repository.getAllChatMessagesBySessionId(id)
                        .count { message -> message.source == ChatMessage.Source.User },
                )
                val retried =
                    repository.getMessagesWithImages(committed.map { messageWithImages -> messageWithImages.key.messageId })
                assertEquals(committed, retried)
                assertTrue(fixture.server.requests.take(2).all { text -> "data:image/" in text })
                // 后续文字轮次仍然携带历史图片；手动摘要读取图片，成功后不再永久携带旧图。
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState == ChatGenerationState.Idle }
                vm.emit(ChatUiIntent.ChangeInputDraft("Continue"))
                vm.emit(ChatUiIntent.SendMessage)
                await {
                    repository.getAllChatMessagesBySessionId(id)
                        .count { message -> message.source == ChatMessage.Source.Char } == 2
                }
                assertTrue("data:image/" in fixture.server.requests.last())
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState == ChatGenerationState.Idle }
                vm.emit(ChatUiIntent.SummarizeNow)
                await { repository.getLatestSummary(id) != null }
                assertTrue("data:image/" in fixture.server.requests.last())
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.dialogState == ChatDialogState.None }
                delay(timeMillis = 400)
                screenshot("chat-images.png")
                vm.emit(ChatUiIntent.ChangeInputDraft("After summary"))
                vm.emit(ChatUiIntent.SendMessage)
                await {
                    repository.getAllChatMessagesBySessionId(id)
                        .count { message -> message.source == ChatMessage.Source.Char } == 3
                }
                assertFalse("data:image/" in fixture.server.requests.last())
                assertEquals(
                    1,
                    repository.getMessagesWithImages(
                        repository.getAllChatMessagesBySessionId(id).map { message -> message.id })
                        .sumOf { messageWithImages -> messageWithImages.images.size },
                )
                scenario.recreate()
            }
        }
    }

    @Test
    fun groupPureImageRunsTwoSpeakersWithTheSameAttachmentInSmallHistoryWindow() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<GroupChatRepository>()
            val id = fixture.db.getGroupChatSessionDao().insertOrReplace(
                data = GroupChatSession(
                    title = "Group image test",
                    createTime = 1,
                    latestTime = 1,
                    userName = "User",
                    userDescription = "",
                    activationStrategy = GroupChatSession.ActivationStrategy.List,
                ),
            )
            fixture.characters.forEachIndexed { index, character ->
                fixture.db.getGroupChatMemberDao()
                    .insertOrReplace(
                        GroupChatMember(
                            sessionId = id,
                            characterId = character,
                            sortOrder = index
                        )
                    )
            }
            // 单条历史窗口也必须为第二位角色保留触发本轮的图片消息。
            AppModel.maxPromptHistoryMessages = 1
            ActivityScenario.launch<GroupChatActivity>(
                Intent(fixture.context, GroupChatActivity::class.java)
                    .putExtra(GroupChatActivity.EXTRA_SESSION_ID, id.toString()),
            ).use { scenario ->
                lateinit var vm: GroupChatViewModel
                scenario.onActivity { groupChatActivity ->
                    vm = ViewModelProvider(groupChatActivity)[GroupChatViewModel::class.java]
                }
                withTimeout(timeMillis = 30_000) {
                    vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>().first()
                }
                vm.emit(GroupChatUiIntent.ImageAction(MessageImageAction.Choose(editing = false)))
                withTimeout(timeMillis = 30_000) {
                    vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
                        .first { normal -> normal.imageState.draft.size == 1 && !normal.imageState.loadState.isBusy }
                }
                // Activity 配置重建复用 ViewModel，内存中的文字和图片草稿仍然保留。
                vm.emit(GroupChatUiIntent.ChangeInputDraft("unsent draft"))
                await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.inputDraft == "unsent draft" }
                scenario.recreate()
                scenario.onActivity { groupChatActivity ->
                    assertTrue(
                        vm === ViewModelProvider(
                            groupChatActivity
                        )[GroupChatViewModel::class.java]
                    )
                }
                val draftState = vm.uiStateFlow.value as GroupChatUiState.Normal
                assertEquals("unsent draft", draftState.conversationState.inputDraft)
                assertEquals(1, draftState.imageState.draft.size)
                vm.emit(GroupChatUiIntent.ChangeInputDraft(""))
                fixture.server.failAtRequest = 2
                vm.emit(GroupChatUiIntent.SendMessage)
                await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState is GroupChatGenerationState.Failed }
                val failed =
                    (vm.uiStateFlow.value as GroupChatUiState.Normal).conversationState.generationState as GroupChatGenerationState.Failed
                assertTrue(failed.canRetryReply)
                assertEquals(
                    1,
                    repository.getGroupChatData(id)!!.messages.count { message -> message.source == GroupChatMessage.Source.Character },
                )
                val session = repository.getSessionById(id)!!
                fixture.db.getGroupChatSessionDao().update(session.copy(autoModeEnabled = true))
                fixture.server.stallAtRequest = 4
                vm.emit(GroupChatUiIntent.RetryImageReply)
                vm.emit(GroupChatUiIntent.RetryImageReply)
                // 同一 Job 中 B 恢复成功后进入 AutoMode，第四个请求必须已释放旧图保护。
                await { fixture.server.requests.size == 4 }
                assertEquals(
                    1,
                    repository.getGroupChatData(id)!!.messages.count { message -> message.source == GroupChatMessage.Source.User },
                )
                assertTrue(fixture.server.requests.take(3).all { text -> "data:image/" in text })
                assertFalse("data:image/" in fixture.server.requests[3])
                vm.emit(GroupChatUiIntent.StopGeneration)
                await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState == GroupChatGenerationState.Idle }
                assertEquals(
                    2,
                    repository.getGroupChatData(id)!!.messages.count { message -> message.source == GroupChatMessage.Source.Character },
                )
                // 停止后的新批次使用不支持图片的模型，已退出历史窗口的旧图不能阻断请求。
                fixture.db.getGroupChatSessionDao().update(session.copy(autoModeEnabled = false))
                val provider =
                    fixture.db.getLLMProviderDao().getProviderById(AppModel.currentLLMProvider)!!
                fixture.db.getLLMProviderDao()
                    .update(provider.copy(imageInputSetting = ImageInputSetting.Unsupported))
                vm.emit(GroupChatUiIntent.SendMessage)
                await { fixture.server.requests.size >= 5 }
                await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState == GroupChatGenerationState.Idle }
                assertTrue(fixture.server.requests.drop(3).all { text -> "data:image/" !in text })
                // Continue / Regenerate 失败恢复保留各自模式；重生成的删除只在原入口执行一次。
                for (regenerate in listOf(false, true)) {
                    val before = repository.getGroupChatData(id)!!.messages
                    val replies =
                        before.count { message -> message.source == GroupChatMessage.Source.Character }
                    fixture.server.failNext = true
                    if (regenerate) vm.emit(GroupChatUiIntent.RegenerateMessage(before.last().id))
                    else vm.emit(GroupChatUiIntent.ContinueLast)
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState is GroupChatGenerationState.Failed }
                    val failedRequest =
                        JSONObject(fixture.server.requests.last()).getJSONArray("messages")
                            .toString()
                    vm.emit(GroupChatUiIntent.RetryImageReply)
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState == GroupChatGenerationState.Idle }
                    assertEquals(
                        failedRequest,
                        JSONObject(fixture.server.requests.last()).getJSONArray("messages")
                            .toString(),
                    )
                    assertEquals(
                        replies + if (regenerate) 0 else 1,
                        repository.getGroupChatData(id)!!.messages.count { message -> message.source == GroupChatMessage.Source.Character },
                    )
                }
                screenshot("group-images.png")
            }
            repository.deleteSession(id)
        }
    }

    /** 真实群聊编辑保存只处理新图，取消编辑不删除旧图，查看器读取入库版本。 */
    @Test
    fun groupEditingSavesNewImagesAndKeepsExistingAttachments() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<GroupChatRepository>()
            val files = GlobalContext.get().get<FileRepository>()
            val runtime = GlobalContext.get().get<MessageImageRuntime>()
            val id = repository.createSession(
                title = "Image edit test",
                userName = "User",
                userDescription = "",
                characterIds = fixture.characters,
                activationStrategy = GroupChatSession.ActivationStrategy.List,
                allowSelfResponses = false,
            )
            try {
                ActivityScenario.launch<GroupChatActivity>(
                    Intent(fixture.context, GroupChatActivity::class.java)
                        .putExtra(GroupChatActivity.EXTRA_SESSION_ID, id.toString()),
                ).use { scenario ->
                    lateinit var vm: GroupChatViewModel
                    scenario.onActivity { groupChatActivity ->
                        vm = ViewModelProvider(groupChatActivity)[GroupChatViewModel::class.java]
                    }
                    await { vm.uiStateFlow.value is GroupChatUiState.Normal }
                    vm.emit(GroupChatUiIntent.ImageAction(MessageImageAction.Choose()))
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.imageState?.let { messageImageState -> messageImageState.draft.size == 1 && !messageImageState.loadState.isBusy } == true }
                    vm.emit(GroupChatUiIntent.SendMessage)
                    await { repository.getGroupChatData(id)!!.messages.count { message -> message.source == GroupChatMessage.Source.Character } == 2 }
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState == GroupChatGenerationState.Idle }
                    val message =
                        repository.getGroupChatData(id)!!.messages.single { message -> message.source == GroupChatMessage.Source.User }
                    val original = repository
                        .getMessagesWithImages(listOf(message.id))
                        .single()
                        .images
                        .single()
                        .image
                        .imageUuid
                    val originalBytes = requireNotNull(files.getFile(original)).readBytes()
                    // 已有图片不能因修改设置而重新压缩；新图在编辑保存时才固定尺寸。
                    AppModel.imageSendSettings = ImageSendSettings(
                        mode = ImageSendMode.Custom,
                        maxFileKiB = 64,
                        maxWidth = 128,
                        maxHeight = 128,
                    ).encode()
                    vm.emit(GroupChatUiIntent.StartEditMessage(message.id))
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.editingMessageId == message.id }
                    vm.emit(GroupChatUiIntent.ChangeEditingMessageDraft("edited"))
                    vm.emit(GroupChatUiIntent.ImageAction(MessageImageAction.Choose(editing = true)))
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.imageState?.let { messageImageState -> messageImageState.editing.size == 2 && !messageImageState.loadState.isBusy } == true }
                    assertEquals(
                        1,
                        repository.getMessagesWithImages(listOf(message.id)).single().images.size
                    )
                    vm.emit(GroupChatUiIntent.SaveEditingMessage)
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.editingMessageId == null }
                    val edited = repository.getMessagesWithImages(listOf(message.id)).single()
                    assertEquals("edited", edited.content)
                    assertEquals(2, edited.images.size)
                    assertEquals(original, edited.images.first().image.imageUuid)
                    assertTrue(originalBytes.contentEquals(requireNotNull(files.getFile(original)).readBytes()))
                    val refs = runtime.references(listOf(edited)).getValue(message.id)
                    assertTrue(refs.last().width <= 128 && refs.last().height <= 128)
                    vm.emit(
                        uiIntent = GroupChatUiIntent.ImageAction(
                            action = MessageImageAction.Preview(
                                ids = edited.images.map { messageImageWithFile ->
                                    messageImageWithFile.image.imageUuid
                                },
                                index = 1,
                            ),
                        ),
                    )
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.imageState?.preview?.bitmap != null }
                    delay(timeMillis = 200)
                    screenshot("group-image-viewer.png")
                    vm.emit(GroupChatUiIntent.ImageAction(MessageImageAction.ClosePreview))
                    vm.emit(GroupChatUiIntent.StartEditMessage(message.id))
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.editingMessageId == message.id }
                    vm.emit(
                        GroupChatUiIntent.ImageAction(
                            MessageImageAction.Remove(
                                uuid = original,
                                editing = true
                            )
                        )
                    )
                    vm.emit(GroupChatUiIntent.CancelEditingMessage)
                    await { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.editingMessageId == null }
                    assertEquals(
                        edited,
                        repository.getMessagesWithImages(listOf(message.id)).single()
                    )
                }
            } finally {
                repository.deleteSession(id)
            }
        }
    }

    /** 直接触发 MVI 验证预留的角色图片能力；界面不开放新增入口，模拟服务端接收 assistant 图片。 */
    @Test
    fun chatCharacterImageEditingFlowsIntoPromptAndCancelPreservesAttachment() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<ChatRepository>()
            val id = fixture.db.getChatSessionDao().insertOrReplace(
                data = ChatSession(
                    characterId = fixture.characters.first(),
                    createTime = 1,
                    latestTime = 1,
                    lorebookEntrySet = "[]",
                    title = "Character image",
                    userNote = "",
                    userName = "User",
                    userDescription = "",
                ),
            )
            val messageId = repository.createMessage(id, ChatMessage.Source.Char, "reply")
            ActivityScenario.launch<ChatActivity>(
                Intent(fixture.context, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_SESSION_ID, id.toString()),
            ).use { scenario ->
                lateinit var vm: ChatViewModel
                scenario.onActivity { chatActivity ->
                    vm = ViewModelProvider(chatActivity)[ChatViewModel::class.java]
                }
                await { vm.uiStateFlow.value is ChatUiState.Normal }
                // 角色消息从无图变为纯图，写回时不得改成 User 或丢弃新附件。
                vm.emit(ChatUiIntent.StartEditMessage(messageId.toString()))
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.editingMessageId == messageId.toString() }
                vm.emit(ChatUiIntent.ChangeEditingMessageDraft(""))
                vm.emit(ChatUiIntent.ImageAction(MessageImageAction.Choose(editing = true)))
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.imageState?.let { messageImageState -> messageImageState.editing.size == 1 && !messageImageState.loadState.isBusy } == true }
                screenshot("chat-character-image-edit.png")
                vm.emit(ChatUiIntent.SaveEditingMessage)
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.editingMessageId == null }
                val saved = repository.getMessagesWithImages(listOf(messageId)).single()
                assertEquals(ChatMessage.Source.Char.name, saved.source)
                assertEquals("", saved.content)
                val uuid = saved.images.single().image.imageUuid
                // 放弃删除草稿不影响持久化图片，下一轮请求仍读取角色的图片。
                vm.emit(ChatUiIntent.StartEditMessage(messageId.toString()))
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.editingMessageId != null }
                vm.emit(
                    ChatUiIntent.ImageAction(
                        MessageImageAction.Remove(
                            uuid = uuid,
                            editing = true
                        )
                    )
                )
                vm.emit(ChatUiIntent.CancelEditingMessage)
                await { (vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.editingMessageId == null }
                assertEquals(saved, repository.getMessagesWithImages(listOf(messageId)).single())
                vm.emit(ChatUiIntent.ChangeInputDraft("Continue"))
                vm.emit(ChatUiIntent.SendMessage)
                await {
                    repository.getAllChatMessagesBySessionId(id)
                        .count { message -> message.source == ChatMessage.Source.Char } == 2
                }
                assertAssistantImage(fixture.server.requests.last())
            }
        }
    }

    /** 绕过界面入口验证群聊预留能力，保留发言者快照并向模拟服务端发送角色图片。 */
    @Test
    fun groupCharacterImageEditingPreservesSpeakerAndFlowsIntoPrompt() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<GroupChatRepository>()
            val id = repository.createSession(
                title = "Character image",
                userName = "User",
                userDescription = "",
                characterIds = fixture.characters,
                activationStrategy = GroupChatSession.ActivationStrategy.List,
                allowSelfResponses = false,
            )
            try {
                val original = GroupChatMessage(
                    sessionId = id,
                    createTime = 1,
                    source = GroupChatMessage.Source.Character,
                    content = "reply",
                    speakerCharacterId = fixture.characters.first(),
                    speakerNameSnapshot = "Fixture 0",
                    generationBatchId = "original-batch",
                )
                val messageId = fixture.db.getGroupChatMessageDao().insertOrReplace(original)
                ActivityScenario.launch<GroupChatActivity>(
                    Intent(fixture.context, GroupChatActivity::class.java)
                        .putExtra(GroupChatActivity.EXTRA_SESSION_ID, id.toString()),
                ).use { scenario ->
                    lateinit var vm: GroupChatViewModel
                    scenario.onActivity { groupChatActivity ->
                        vm = ViewModelProvider(groupChatActivity)[GroupChatViewModel::class.java]
                    }
                    await("group ready") { vm.uiStateFlow.value is GroupChatUiState.Normal }
                    // 从已有角色回复添加图片，附件与空正文必须共同提交。
                    vm.emit(GroupChatUiIntent.StartEditMessage(messageId))
                    await("group editing target") { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.editingMessageId == messageId }
                    vm.emit(GroupChatUiIntent.ChangeEditingMessageDraft(""))
                    vm.emit(GroupChatUiIntent.ImageAction(MessageImageAction.Choose(editing = true)))
                    await("group image picked") { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.imageState?.let { messageImageState -> messageImageState.editing.size == 1 && !messageImageState.loadState.isBusy } == true }
                    screenshot("group-character-image-edit.png")
                    vm.emit(GroupChatUiIntent.SaveEditingMessage)
                    await("group edit saved") { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.editingMessageId == null }
                    assertEquals(
                        original.copy(id = messageId, content = ""),
                        repository.getMessageById(messageId)
                    )
                    assertEquals(
                        1,
                        repository.getMessagesWithImages(listOf(messageId)).single().images.size
                    )
                    // 正常发言读取同一份角色附件，不用 User 来源伪装历史。
                    vm.emit(GroupChatUiIntent.ChangeInputDraft("Continue"))
                    vm.emit(GroupChatUiIntent.SendMessage)
                    await("group generated reply") { repository.getGroupChatData(id)!!.messages.count { message -> message.source == GroupChatMessage.Source.Character } >= 2 }
                    await("group idle") { (vm.uiStateFlow.value as? GroupChatUiState.Normal)?.conversationState?.generationState == GroupChatGenerationState.Idle }
                    assertAssistantImage(fixture.server.requests.first())
                }
            } finally {
                repository.deleteSession(id)
            }
        }
    }

    /** 核对最终 HTTP 请求中的角色与图片块，避免只验证数据库中有附件。 */
    private fun assertAssistantImage(payload: String) {
        val messages = JSONObject(payload).getJSONArray("messages")
        assertTrue(
            (0 until messages.length()).any { index ->
                val message = messages.getJSONObject(index)
                val blocks = message.optJSONArray("content")
                message.optString("role") == "assistant" && blocks != null
                    && (0 until blocks.length()).any { blockIndex ->
                    blocks.getJSONObject(blockIndex).optJSONObject("image_url")
                        ?.optString("url")?.startsWith("data:image/") == true
                }
            },
        )
    }

    @Test
    fun stoppingAnImageStreamClosesTheRequestAndPreservesTheUserImage() = runBlocking {
        withFixture { fixture ->
            val repository = GlobalContext.get().get<ChatRepository>()
            val id = fixture.db.getChatSessionDao().insertOrReplace(
                data = ChatSession(
                    characterId = fixture.characters.first(),
                    createTime = 1,
                    latestTime = 1,
                    lorebookEntrySet = "[]",
                    title = "Cancel image test",
                    userNote = "",
                    userName = "User",
                    userDescription = "",
                ),
            )
            AppModel.streamEnabled = true
            fixture.server.stallNext = true
            ActivityScenario.launch<ChatActivity>(
                Intent(fixture.context, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_SESSION_ID, id.toString()),
            ).use { scenario ->
                lateinit var vm: ChatViewModel
                scenario.onActivity { chatActivity ->
                    vm = ViewModelProvider(chatActivity)[ChatViewModel::class.java]
                }
                withTimeout(timeMillis = 30_000) {
                    vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>().first()
                }
                vm.emit(ChatUiIntent.ImageAction(MessageImageAction.Choose(editing = false)))
                withTimeout(timeMillis = 30_000) {
                    vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
                        .first { normal -> normal.imageState.draft.size == 1 && !normal.imageState.loadState.isBusy }
                }
                // 服务端保持 SSE 开启却不返回下一行，停止必须主动关闭 socket。
                vm.emit(ChatUiIntent.SendMessage)
                await { fixture.server.requests.isNotEmpty() }
                vm.emit(ChatUiIntent.StopGeneration)
                withTimeout(timeMillis = 5_000) {
                    while ((vm.uiStateFlow.value as? ChatUiState.Normal)?.conversationState?.generationState != ChatGenerationState.Idle) delay(
                        timeMillis = 50
                    )
                    while (File(fixture.context.cacheDir, "image-requests").listFiles()
                            .orEmpty()
                            .isNotEmpty()
                    ) delay(timeMillis = 50)
                }
                val messages = repository.getAllChatMessagesBySessionId(id)
                assertEquals(
                    1,
                    messages.count { message -> message.source == ChatMessage.Source.User })
                assertEquals(
                    1,
                    repository.getMessagesWithImages(messages.map { message -> message.id })
                        .sumOf { messageWithImages -> messageWithImages.images.size },
                )
            }
        }
    }

    /** 等待异步状态并保留失败阶段，避免超时只能定位到协程调度器。 */
    private suspend fun await(
        description: String = "state update",
        condition: suspend () -> Boolean
    ) {
        try {
            withTimeout(timeMillis = 30_000) {
                while (!condition()) delay(timeMillis = 50)
            }
        } catch (error: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for $description", error)
        }
    }

    /** 只截取本测试创建的合成对话，供布局验收。 */
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            target.outputStream()
                .use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
            bitmap.recycle()
        }
    }

    /** 测试仅创建并清理自己拥有的实体，所有偏好在 finally 中恢复。 */
    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val koin = GlobalContext.get()
        val db = koin.get<AppDatabase>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val server = LocalModelServer()
        val llm = koin.get<LLMRepository>()
        val providerId = llm.saveProvider(
            provider = LLMProvider(
                name = "Image flow fixture",
                providerType = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "http://127.0.0.1:${server.port}",
                model = "fixture",
                contextTokens = 100_000,
            ),
        )
        val characters = mutableListOf<Long>()
        val image = File(context.cacheDir, "image-flow-fixture.png")
        // 经由真实 Choose → Activity 回调登记选择归属，仅替换外部系统选择器的结果。
        val pickerIntent =
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4).createIntent(
                context = context,
                input = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        val pickerMonitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != pickerIntent.action) return null
                return Instrumentation.ActivityResult(
                    Activity.RESULT_OK,
                    Intent().setData(Uri.fromFile(image))
                )
            }
        }
        instrumentation.addMonitor(pickerMonitor)
        val old = listOf(
            AppModel.currentLLMProvider,
            AppModel.summaryLLMProvider,
            AppModel.streamEnabled,
            AppModel.autoSummaryEnabled,
            AppModel.maxPromptHistoryMessages,
            AppModel.imageSendSettings,
        )
        try {
            repeat(times = 2) { index ->
                val id = db.getCharacterDao().insertOrReplace(
                    data = Character(
                        name = "Fixture $index",
                        avatar = "",
                        characterTags = "[]",
                        description = "Test character",
                        personality = "",
                        scenario = "",
                        firstMessages = "",
                        examplesOfDialogue = "",
                        postHistoryInstructions = "",
                    ),
                )
                characters += id
                db.getCharacterLLMProviderAssociationDao().insertOrReplace(
                    data = CharacterLLMProviderAssociation(
                        characterId = id,
                        llmProviderId = providerId,
                    ),
                )
            }
            Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.BLUE)
                image.outputStream()
                    .use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                bitmap.recycle()
            }
            AppModel.currentLLMProvider = providerId
            AppModel.summaryLLMProvider = providerId
            AppModel.streamEnabled = false
            AppModel.autoSummaryEnabled = false
            AppModel.maxPromptHistoryMessages = 100
            AppModel.imageSendSettings = ImageSendSettings().encode()
            block(Fixture(context = context, db = db, characters = characters, server = server))
        } finally {
            instrumentation.removeMonitor(pickerMonitor)
            AppModel.currentLLMProvider = old[0] as Long
            AppModel.summaryLLMProvider = old[1] as Long
            AppModel.streamEnabled = old[2] as Boolean
            AppModel.autoSummaryEnabled = old[3] as Boolean
            AppModel.maxPromptHistoryMessages = old[4] as Int
            AppModel.imageSendSettings = old[5] as String
            characters.forEach { koin.get<CharacterRepository>().deleteCharacter(it) }
            llm.deleteProvider(providerId)
            image.delete()
            server.close()
        }
    }

    private data class Fixture(
        val context: Context, val db: AppDatabase,
        val characters: List<Long>, val server: LocalModelServer
    )

    /** 只监听设备回环地址，返回固定文本并记录合成测试请求。 */
    private class LocalModelServer : AutoCloseable {
        private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val requests = CopyOnWriteArrayList<String>()

        @Volatile
        var failNext = false

        @Volatile
        var failAtRequest = -1

        @Volatile
        var stallNext = false

        @Volatile
        var stallAtRequest = -1
        private val worker = thread(isDaemon = true, name = "image-model-fixture") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val input = DataInputStream(client.getInputStream())
                        var length = 0
                        while (true) {
                            val line = ByteArrayOutputStream()
                            while (true) {
                                val byte = input.read()
                                if (byte < 0 || byte == 10) break
                                line.write(byte)
                            }
                            val header = line.toString("UTF-8").trim()
                            if (header.isEmpty()) break
                            if (header.startsWith(
                                    prefix = "Content-Length:",
                                    ignoreCase = true
                                )
                            ) length = header
                                .substringAfter(':')
                                .trim()
                                .toInt()
                        }
                        val body = ByteArray(length)
                        input.readFully(body)
                        requests += body.toString(Charsets.UTF_8)
                        if (stallNext || requests.size == stallAtRequest) {
                            stallNext = false
                            client.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                                flush()
                            }
                            client.soTimeout = 10_000
                            input.read()
                            return@use
                        }
                        val failure =
                            failNext.also { failNext = false } || requests.size == failAtRequest
                        val response =
                            if (failure) """{"error":{"message":"fixture failure"}}""" else """{"choices":[{"message":{"content":"A blue square is visible."},"finish_reason":"stop"}]}"""
                        val bytes = response.toByteArray()
                        client.getOutputStream().apply {
                            val status = if (failure) "400 Bad Request" else "200 OK"
                            write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    if (socket.isClosed) break
                }
            }
        }

        override fun close() {
            socket.close()
            worker.join(1000)
        }
    }
}
