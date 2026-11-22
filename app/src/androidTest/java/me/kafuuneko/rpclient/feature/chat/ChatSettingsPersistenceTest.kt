package me.kafuuneko.rpclient.feature.chat

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

/** 在真实单聊中验证后台恢复、摘要草稿、显式保存和放弃操作之间的数据边界。 */
class ChatSettingsPersistenceTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mChats = GlobalContext.get().get<ChatRepository>()
    private val mCharacters = GlobalContext.get().get<CharacterRepository>()
    private var mCharacterId = 0L
    private var mSessionId = 0L

    /** 创建继承角色备注并已有两版摘要的独立会话。 */
    @Before
    fun setUp(): Unit = runBlocking {
        val database = GlobalContext.get().get<AppDatabase>()
        mCharacterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Settings fixture",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
                creatorNotes = "Inherited notes",
            ),
        )
        mSessionId = mChats.createSessionWithFirstMessage(
            characterId = mCharacterId, title = "Original title", userNote = "",
            lorebookEntryIds = emptyList(), firstMessageContent = null,
        )
        // 通过真实摘要快照验证恢复操作不会覆盖其他表单草稿。
        val first =
            mChats.createMessage(mSessionId, ChatMessage.Source.User, "first", createTime = 1L)
        mChats.saveSummary(mSessionId, "Previous memory", first)
        val last =
            mChats.createMessage(mSessionId, ChatMessage.Source.Char, "last", createTime = 2L)
        mChats.saveSummary(mSessionId, "Current memory", last)
    }

    /** 删除本测试创建的角色及关联会话。 */
    @After
    fun tearDown() = runBlocking {
        if (mCharacterId != 0L) mCharacters.deleteCharacter(mCharacterId)
    }

    /** 返回确认、取消与放弃不丢草稿，保存标题不应固化角色备注继承。 */
    @Test
    fun resumeAndSummaryRestoreKeepDraftUntilSaveOrDiscard() {
        val intent = Intent(mContext, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_SESSION_ID, mSessionId.toString())
        ActivityScenario.launch<ChatActivity>(intent).use { scenario ->
            openSettings()
            field(R.string.title).performTextReplacement("Draft title")
            // 后台恢复与摘要回滚都必须保留标题草稿和原保存基线。
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            field(R.string.title).assertTextContains("Draft title")
            val restore = hasText(mContext.getString(R.string.restore_previous_summary))
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(restore)
            rule.onNode(restore).performScrollTo().assertIsDisplayed().performClick()
            field(R.string.current_summary)
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasSetTextAction() and hasText("Previous memory"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            field(R.string.current_summary).assertTextContains("Previous memory")
            field(R.string.title).assertTextContains("Draft title")
            assertEquals("Original title", runBlocking { mChats.getSessionById(mSessionId)?.title })
            assertEquals(
                "Current memory",
                runBlocking { mChats.getLatestSummary(mSessionId)?.content })

            // 取消离开继续编辑，随后保存才写入标题并回到对话。
            requestLeave()
            rule.onNodeWithText(mContext.getString(R.string.cancel)).performClick()
            field(R.string.title).assertTextContains("Draft title")
            requestLeave()
            rule.onNode(
                hasText(mContext.getString(R.string.save)) and hasAnyAncestor(isDialog()),
            ).performClick()
            awaitConversation()
            val saved = runBlocking { mChats.getSessionById(mSessionId) }
            assertEquals("Draft title", saved?.title)
            assertNull(saved?.creatorNotes)
            assertEquals(
                "Previous memory",
                runBlocking { mChats.getLatestSummary(mSessionId)?.content })

            // 放弃后重新进入设置，仍展示上次保存的标题和已经恢复的摘要。
            openSettings()
            field(R.string.title).performTextReplacement("Discarded title")
            requestLeave()
            rule.onNodeWithText(mContext.getString(R.string.discard_changes)).performClick()
            awaitConversation()
            openSettings()
            field(R.string.title).assertTextContains("Draft title")
            field(R.string.current_summary).assertTextContains("Previous memory")
        }
    }

    /** 清空与角色默认正文相同的会话覆盖后，后续角色编辑仍能被会话继承。 */
    @Test
    fun clearingMatchingCreatorNotesRestoresInheritance() {
        runBlocking { mChats.updateSessionCreatorNotes(mSessionId, "Inherited notes") }
        val intent = Intent(mContext, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_SESSION_ID, mSessionId.toString())
        ActivityScenario.launch<ChatActivity>(intent).use {
            // 通过真实输入框清空覆盖，离开确认必须识别继承状态的变化。
            openSettings()
            field(R.string.creator_notes).performTextReplacement("")
            requestLeave()
            rule.onNode(
                hasText(mContext.getString(R.string.save)) and hasAnyAncestor(isDialog()),
            ).performClick()
            awaitConversation()
            // 保存后更新角色默认备注，确认会话没有留下与旧默认相同的覆盖。
            runBlocking {
                assertNull(mChats.getSessionById(mSessionId)?.creatorNotes)
                val character = requireNotNull(mCharacters.getCharacterById(mCharacterId))
                mCharacters.updateCharacter(character.copy(creatorNotes = "Updated character notes"))
                assertEquals("Updated character notes", mChats.getSessionCreatorNotes(mSessionId))
            }
        }
    }

    /** 等待真实会话渲染完成后打开设置。 */
    private fun openSettings() {
        awaitConversation()
        rule.onNode(hasContentDescription(mContext.getString(R.string.generation_params)))
            .performClick()
    }

    /** 等待保存或放弃操作完成并回到对话页。 */
    private fun awaitConversation() {
        val settings = hasContentDescription(mContext.getString(R.string.generation_params))
        rule.waitUntil(10_000) { rule.onAllNodes(settings).fetchSemanticsNodes().isNotEmpty() }
    }

    /** 通过顶栏返回请求离开，等待未保存确认出现。 */
    private fun requestLeave() {
        rule.onNode(hasContentDescription(mContext.getString(R.string.back))).performClick()
        rule.onNodeWithText(mContext.getString(R.string.chat_save_settings_message)).assertExists()
    }

    /** 滚动到设置表单字段后返回其语义节点。 */
    private fun field(@StringRes label: Int): SemanticsNodeInteraction {
        val matcher = hasSetTextAction() and hasText(mContext.getString(label))
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return rule.onNode(matcher).performScrollTo().assertIsDisplayed()
    }
}
