package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationPageRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var chatRepository: ChatRepository
    private lateinit var groupChatRepository: GroupChatRepository
    private var chatSessionId: Long = 0L
    private var groupChatSessionId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        // 使用同一内存数据库验证页面聚合查询的事务语义
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatRepository = ChatRepository(database, Gson())
        groupChatRepository = GroupChatRepository(database, Gson())

        // 创建单聊和群聊页面查询需要的最小会话数据
        val characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            )
        )
        chatSessionId = database.getChatSessionDao().insertOrReplace(
            ChatSession(
                characterId = characterId,
                createTime = 1L,
                latestTime = 1L,
                lorebookEntrySet = "[]",
                title = "Test chat",
                userNote = "",
                userName = "Alice",
                userDescription = ""
            )
        )
        groupChatSessionId = database.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(
                title = "Test group",
                createTime = 1L,
                latestTime = 1L,
                userName = "Alice",
                userDescription = ""
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun chatPagePreservesCharacterCapabilityOutsideLatestWindow() = runBlocking {
        // 角色消息只存在于分页窗口之外，最近窗口全部为用户消息
        val characterMessageId = chatRepository.createMessage(
            sessionId = chatSessionId,
            source = ChatMessage.Source.Char,
            content = "character-message",
            createTime = 1L
        )
        (2L..4L).forEach { createTime ->
            chatRepository.createMessage(
                sessionId = chatSessionId,
                source = ChatMessage.Source.User,
                content = "user-message-$createTime",
                createTime = createTime
            )
        }

        val pageData = chatRepository.getChatPageData(chatSessionId, pageSize = 2)

        // 页面能力来自完整会话，而不是当前返回的两条消息
        assertEquals(
            listOf("user-message-3", "user-message-4"),
            pageData.page.messages.map { it.content }
        )
        assertFalse(pageData.page.messages.any { it.source == ChatMessage.Source.Char })
        assertTrue(pageData.hasCharacterMessage)

        // 删除唯一角色消息后，会话级能力随下一次页面快照失效
        chatRepository.deleteMessage(characterMessageId)
        assertFalse(chatRepository.getChatPageData(chatSessionId, pageSize = 2).hasCharacterMessage)
    }

    @Test
    fun groupChatPagePreservesCharacterCapabilityOutsideLatestWindow() = runBlocking {
        // 角色消息只存在于分页窗口之外，最近窗口全部为用户消息
        val characterMessageId = groupChatRepository.createMessage(
            sessionId = groupChatSessionId,
            source = GroupChatMessage.Source.Character,
            content = "character-message",
            speakerCharacterId = 1L,
            speakerNameSnapshot = "Character",
            createTime = 1L
        )
        (2L..4L).forEach { createTime ->
            groupChatRepository.createMessage(
                sessionId = groupChatSessionId,
                source = GroupChatMessage.Source.User,
                content = "user-message-$createTime",
                speakerCharacterId = null,
                speakerNameSnapshot = "Alice",
                createTime = createTime
            )
        }

        val pageData = groupChatRepository.getGroupChatPageData(
            sessionId = groupChatSessionId,
            pageSize = 2
        ) ?: error("Group chat should exist")

        // 页面能力来自完整群聊，而不是当前返回的两条消息
        assertEquals(
            listOf("user-message-3", "user-message-4"),
            pageData.data.messages.map { it.content }
        )
        assertFalse(pageData.data.messages.any { it.source == GroupChatMessage.Source.Character })
        assertTrue(pageData.hasCharacterMessage)

        // 删除唯一角色消息后，群聊会话级能力随下一次页面快照失效
        groupChatRepository.deleteMessage(characterMessageId)
        val refreshed = groupChatRepository.getGroupChatPageData(
            sessionId = groupChatSessionId,
            pageSize = 2
        ) ?: error("Group chat should exist")
        assertFalse(refreshed.hasCharacterMessage)
    }
}
