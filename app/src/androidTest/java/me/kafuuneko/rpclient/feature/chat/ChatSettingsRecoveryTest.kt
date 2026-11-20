package me.kafuuneko.rpclient.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatViewModel
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext

/**
 * 用真实会话及串行 Intent 流验证设置页的外部修改和恢复边界。
 * - 删除人设后保留其他草稿，并接受新的合法绑定。
 * - 数据库拒绝成员写入时保留选择，导航暂时没有宿主时保留分支通知。
 */
class ChatSettingsRecoveryTest {
    private val mKoin = GlobalContext.get()
    private val mDatabase = mKoin.get<AppDatabase>()
    private val mChats = mKoin.get<ChatRepository>()
    private val mGroups = mKoin.get<GroupChatRepository>()
    private val mCharacters = mKoin.get<CharacterRepository>()
    private val mPersonas = mKoin.get<UserPersonaRepository>()
    private val mCharacterIds = mutableListOf<Long>()
    private val mPersonaIds = mutableListOf<Long>()
    private val mChatIds = mutableListOf<Long>()
    private val mGroupIds = mutableListOf<Long>()
    private var mOriginalDefault = 0L

    /** 只创建本测试拥有的人设和角色，恢复原默认指针后再执行用例。 */
    @Before
    fun setUp() = runBlocking {
        mOriginalDefault = mPersonas.active().id
        for (name in listOf("Original persona", "Draft persona")) {
            val id = mPersonas.create(name)
            mPersonaIds += id
            mPersonas.describe(id, "$name description")
        }
        mPersonas.select(mOriginalDefault)
        repeat(3) { index ->
            mCharacterIds += mDatabase.getCharacterDao().insertOrReplace(
                Character(
                    name = "Recovery fixture $index", avatar = "", characterTags = "[]",
                    description = "", personality = "", scenario = "", firstMessages = "",
                    examplesOfDialogue = "", postHistoryInstructions = "",
                ),
            )
        }
    }

    /** 清理独占会话、人设和角色，不触碰测试启动前的数据。 */
    @After
    fun tearDown() = runBlocking {
        mPersonas.select(mOriginalDefault)
        mChatIds.forEach { mChats.deleteSession(it) }
        mGroupIds.forEach { mGroups.deleteSession(it) }
        mCharacterIds.forEach { mCharacters.deleteCharacter(it) }
        mPersonaIds.forEach { mPersonas.delete(it) }
    }

    /** 人设被外部删除后，旧菜单意图不会崩溃，解绑姓名和其他草稿都能正确保存。 */
    @Test
    fun singleChatRecoversDeletedPersonaAndKeepsTitleDraft() = runBlocking {
        val id = mChats.createSessionWithFirstMessage(
            characterId = mCharacterIds.first(), title = "Original title", userNote = "",
            lorebookEntryIds = emptyList(), firstMessageContent = null,
            userPersonaId = mPersonaIds.first(),
        )
        mChatIds += id
        mChats.updateSession(requireNotNull(mChats.getSessionById(id)).copy(userName = null))
        withModel(::ChatViewModel) { vm ->
            val states = vm.uiStateFlow.filterIsInstance<ChatUiState.Normal>()
            vm.emit(ChatUiIntent.Init(id.toString()))
            withTimeout(15_000) { states.first() }
            vm.emit(ChatUiIntent.OpenChatSettings)
            withTimeout(15_000) { states.first { it.page == ChatPage.Settings } }
            vm.emit(ChatUiIntent.ChangeTitle("Unsaved title"))
            withTimeout(15_000) { states.first { it.settingsState.titleDraft == "Unsaved title" } }

            // 模拟停留在全局设置时删除绑定身份，以及返回后到达的过期菜单选择。
            mPersonas.delete(mPersonaIds.first())
            vm.emit(ChatUiIntent.SelectPersona(mPersonaIds.first()))
            val recovered = withTimeout(15_000) {
                states.first { it.settingsState.persona.selectedId == UserPersona.FOLLOW_DEFAULT }
            }.settingsState
            assertEquals("Original persona", recovered.userNameDraft)
            assertEquals(mPersonas.active().description, recovered.inheritedDescription)
            assertEquals("Unsaved title", recovered.titleDraft)
            assertFalse(recovered.persona.options.any { it.id == mPersonaIds.first() })
            assertEquals(UserPersona.FOLLOW_DEFAULT, recovered.savedSnapshot?.userPersonaId)
            assertEquals("Original title", mChats.getSessionById(id)?.title)

            vm.emit(ChatUiIntent.SaveSettings)
            withTimeout(15_000) { states.first { it.page == ChatPage.Conversation } }
            assertEquals("Unsaved title", mChats.getSessionById(id)?.title)
            assertEquals(UserPersona.FOLLOW_DEFAULT, mChats.getSessionById(id)?.userPersonaId)
        }
    }

    /** 群聊刷新既保留合法改选，也刷新继承描述；选择失效后回退到数据库的解绑结果。 */
    @Test
    fun groupRefreshKeepsValidSelectionAndDropsDeletedSelection() = runBlocking {
        val id = createGroup()
        withModel(::GroupChatViewModel) { vm ->
            val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
            vm.emit(GroupChatUiIntent.Init(id.toString()))
            withTimeout(15_000) { states.first() }
            vm.emit(GroupChatUiIntent.OpenSettings)
            withTimeout(15_000) { states.first { it.page == GroupChatPage.Settings } }
            vm.emit(GroupChatUiIntent.ChangeTitle("Unsaved group"))
            vm.emit(GroupChatUiIntent.SelectPersona(mPersonaIds.last()))
            withTimeout(15_000) {
                states.first { it.settingsState.persona.selectedId == mPersonaIds.last() }
            }
            // 默认身份和选中草稿可以不同，后台编辑描述后仍应读取草稿选择的最新值。
            mPersonas.describe(mPersonaIds.last(), "Changed description")
            vm.emit(GroupChatUiIntent.Resume)
            val refreshed = withTimeout(15_000) {
                states.first { it.settingsState.inheritedDescription == "Changed description" }
            }.settingsState
            assertEquals(mPersonaIds.last(), refreshed.persona.selectedId)
            assertEquals("Unsaved group", refreshed.titleDraft)

            mPersonas.delete(mPersonaIds.last())
            vm.emit(GroupChatUiIntent.SelectPersona(mPersonaIds.last()))
            withTimeout(15_000) {
                states.first { it.settingsState.persona.selectedId == mPersonaIds.first() }
            }
            mPersonas.delete(mPersonaIds.first())
            vm.emit(GroupChatUiIntent.Resume)
            val recovered = withTimeout(15_000) {
                states.first { it.settingsState.persona.selectedId == UserPersona.FOLLOW_DEFAULT }
            }.settingsState
            assertEquals("Original persona", recovered.userNameDraft)
            assertEquals("Unsaved group", recovered.titleDraft)
            assertTrue(recovered.persona.options.none { it.id in mPersonaIds })
            vm.emit(GroupChatUiIntent.SaveSettings)
            withTimeout(15_000) { states.first { it.page == GroupChatPage.Conversation } }
            assertEquals("Unsaved group", mGroups.getSessionById(id)?.title)
        }
    }

    /** 候选角色在弹窗打开后被删除时，真实外键失败应转成提示，选择和 Intent 流继续可用。 */
    @Test
    fun deletedMemberCandidateKeepsDialogAndDoesNotStopIntentProcessing() = runBlocking {
        val id = createGroup()
        withModel(::GroupChatViewModel) { vm ->
            val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
            vm.emit(GroupChatUiIntent.Init(id.toString()))
            withTimeout(15_000) { states.first() }
            vm.emit(GroupChatUiIntent.OpenSettings)
            withTimeout(15_000) { states.first { it.page == GroupChatPage.Settings } }
            vm.emit(GroupChatUiIntent.ShowAddMembersDialog)
            withTimeout(15_000) { states.first { it.dialogState is GroupChatDialogState.AddMembers } }
            val candidateId = mCharacterIds.last()
            vm.emit(GroupChatUiIntent.ToggleAddMemberSelection(candidateId))
            withTimeout(15_000) {
                states.first { candidateId in (it.dialogState as? GroupChatDialogState.AddMembers)?.selectedCharacterIds.orEmpty() }
            }
            mCharacters.deleteCharacter(candidateId)
            val notification = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(15_000) { vm.viewEventFlow.first() }
            }
            vm.emit(GroupChatUiIntent.ConfirmAddMembers)
            notification.await().consumeIfNotHandled {
                assertEquals(
                    AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed_short),
                    it
                )
            }
            vm.emit(GroupChatUiIntent.ChangeTitle("Editable after failure"))
            val state = withTimeout(15_000) {
                states.first { it.settingsState.titleDraft == "Editable after failure" }
            }
            assertTrue(candidateId in (state.dialogState as GroupChatDialogState.AddMembers).selectedCharacterIds)
            assertEquals(2, mGroups.getMembers(id).size)
        }
    }

    /** 成员写入被数据库拒绝时提示保存失败，排序回滚保留草稿且 Intent 队列继续工作。 */
    @Test
    fun memberWriteFailuresKeepTheFormAndIntentQueueUsable() = runBlocking {
        val id = createGroup()
        mGroups.addMembers(id, listOf(mCharacterIds.last()))
        val database = mDatabase.openHelper.writableDatabase
        // 触发器只拦截本测试群聊，真实事务失败路径无需替换 Repository。
        for (operation in listOf("DELETE", "UPDATE")) {
            database.execSQL(
                "CREATE TRIGGER audit_reject_member_$operation BEFORE $operation ON group_chat_members " +
                    "WHEN OLD.sessionId = $id BEGIN SELECT RAISE(ABORT, 'Fixture write rejected'); END",
            )
        }
        try {
            withModel(::GroupChatViewModel) { vm ->
                val states = vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>()
                vm.emit(GroupChatUiIntent.Init(id.toString()))
                withTimeout(15_000) { states.first() }
                vm.emit(GroupChatUiIntent.OpenSettings)
                withTimeout(15_000) { states.first { it.page == GroupChatPage.Settings } }
                vm.emit(GroupChatUiIntent.ChangeTitle("Keep this draft"))
                withTimeout(15_000) { states.first { it.settingsState.titleDraft == "Keep this draft" } }
                // 每次失败都应给出写入提示，不能误报成员数量限制或结束意图收集。
                for (intent in listOf(
                    GroupChatUiIntent.RemoveMember(mCharacterIds.last()),
                    GroupChatUiIntent.MoveMember(mCharacterIds.first(), 1),
                    GroupChatUiIntent.CommitMemberOrder,
                )) {
                    if (intent == GroupChatUiIntent.CommitMemberOrder) {
                        vm.emit(
                            GroupChatUiIntent.ReorderMember(
                                mCharacterIds.first(),
                                mCharacterIds.last()
                            )
                        )
                        withTimeout(15_000) { states.first { it.members.first().id != mCharacterIds.first() } }
                    }
                    // 删除成员先确认私有记忆清理范围，再触发真实数据库失败。
                    if (intent is GroupChatUiIntent.RemoveMember) {
                        vm.emit(intent)
                        withTimeout(15_000) {
                            states.first { it.dialogState is GroupChatDialogState.RemoveMember }
                        }
                    }
                    val notification = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(15_000) { vm.viewEventFlow.first() }
                    }
                    vm.emit(
                        if (intent is GroupChatUiIntent.RemoveMember) {
                            GroupChatUiIntent.ConfirmRemoveMember
                        } else {
                            intent
                        }
                    )
                    notification.await().consumeIfNotHandled {
                        assertEquals(
                            AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed_short),
                            it
                        )
                    }
                }
                val restored =
                    withTimeout(15_000) { states.first { it.members.map { member -> member.id } == mCharacterIds } }
                assertEquals("Keep this draft", restored.settingsState.titleDraft)
                vm.emit(GroupChatUiIntent.ChangeTitle("Editable after failures"))
                withTimeout(15_000) { states.first { it.settingsState.titleDraft == "Editable after failures" } }
            }
        } finally {
            database.execSQL("DROP TRIGGER IF EXISTS audit_reject_member_DELETE")
            database.execSQL("DROP TRIGGER IF EXISTS audit_reject_member_UPDATE")
        }
    }

    /** 创建分支时宿主暂未订阅，恢复收集后仍能打开同一个分支并结束源页面。 */
    @Test
    fun branchNavigationSurvivesMissingActivityCollector() = runBlocking {
        val id = createGroup()
        val messageId = mGroups.createMessage(
            sessionId = id, source = GroupChatMessage.Source.User, content = "Branch source",
            speakerCharacterId = null, speakerNameSnapshot = "Original persona",
        )
        withModel(::GroupChatViewModel) { vm ->
            vm.emit(GroupChatUiIntent.Init(id.toString()))
            withTimeout(15_000) {
                vm.uiStateFlow.filterIsInstance<GroupChatUiState.Normal>().first()
            }
            vm.emit(GroupChatUiIntent.BranchFromMessage(messageId))
            // 直到通知已经产生才安装消费者，覆盖旋转屏幕期间没有 Activity 的窗口。
            withTimeout(15_000) {
                while (vm.branchNavigationFlow.replayCache.isEmpty()) delay(10)
            }
            val wrapper = vm.branchNavigationFlow.first()
            wrapper.consumeIfNotHandled { event ->
                val navigation = event as AppViewEvent.StartActivity
                assertEquals(GroupChatActivity::class.java, navigation.activity)
                val branchId = requireNotNull(navigation.extras)
                    .getString(GroupChatActivity.EXTRA_SESSION_ID)!!.toLong()
                mGroupIds += branchId
                assertEquals(1, mGroups.getMessageCount(branchId))
                assertEquals("Branch source", mGroups.getLatestMessage(branchId)?.content)
            }
            withTimeout(15_000) { vm.uiStateFlow.first { it is GroupChatUiState.Finished } }
            assertFalse(
                vm.branchNavigationFlow.first()
                    .consumeIfNotHandled { error("Repeated navigation") })
        }
    }

    /** 创建没有名称覆盖的群聊，保证删除人设时会触发保留原姓名的解绑行为。 */
    private suspend fun createGroup(): Long {
        val id = mGroups.createSession(
            title = "Original group", characterIds = mCharacterIds.take(2),
            activationStrategy = GroupChatSession.ActivationStrategy.Manual,
            allowSelfResponses = false, userPersonaId = mPersonaIds.first(),
        )
        mGroupIds += id
        mGroups.updateSession(requireNotNull(mGroups.getSessionById(id)).copy(userName = null))
        return id
    }

    /** 在主线程创建真实 ViewModel，使用独立 Store 保证测试结束时执行资源释放。 */
    private suspend fun <T : ViewModel> withModel(create: () -> T, block: suspend (T) -> Unit) {
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) { create().also { store.put("fixture", it) } }
        try {
            block(model)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
