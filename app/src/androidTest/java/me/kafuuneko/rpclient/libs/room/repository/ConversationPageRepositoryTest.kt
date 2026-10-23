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
        chatRepository = ChatRepository(
            mAppDatabase = database,
            mGson = Gson(),
            mImages = MessageImageRepository(
                mDatabase = database,
                mFiles = FileRepository(
                    mContext = context,
                    mAppDatabase = database,
                ),
            ),
        )
        groupChatRepository = GroupChatRepository(
            mAppDatabase = database,
            mGson = Gson(),
            mImages = MessageImageRepository(
                mDatabase = database,
                mFiles = FileRepository(
                    mContext = context,
                    mAppDatabase = database,
                ),
            ),
        )

        // 创建单聊和群聊页面查询需要的最小会话数据
        val characterId = database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
            ),
        )
        chatSessionId = database.getChatSessionDao().insertOrReplace(
            data = ChatSession(
                characterId = characterId,
                createTime = 1L,
                latestTime = 1L,
                lorebookEntrySet = "[]",
                title = "Test chat",
                userNote = "",
                userName = "Alice",
                userDescription = "",
            ),
        )
        groupChatSessionId = database.getGroupChatSessionDao().insertOrReplace(
            data = GroupChatSession(
                title = "Test group",
                createTime = 1L,
                latestTime = 1L,
                userName = "Alice",
                userDescription = "",
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchAnchorLoadsBothDirectionsWithGlobalDepth() = runBlocking {
        val ids = (0..100).map { index ->
            chatRepository.createMessage(
                sessionId = chatSessionId,
                source = ChatMessage.Source.User,
                content = "message-$index",
                createTime = index.toLong(),
            )
        }
        val page = chatRepository.getChatPageData(
            sessionId = chatSessionId,
            pageSize = 10,
            anchorMessageId = ids[40]
        ).page
        assertEquals(
            ids.subList(fromIndex = 35, toIndex = 46),
            page.messages.map { message -> message.id })
        assertTrue(page.canLoadOlderMessages)
        assertTrue(page.canLoadNewerMessages)
        assertEquals(55, page.newerMessageCount)
        val newer = chatRepository.getMessagePageAfter(
            sessionId = chatSessionId,
            afterMessageId = page.messages.last().id,
            pageSize = 10,
        )!!
        assertEquals(
            ids.subList(fromIndex = 46, toIndex = 56),
            newer.messages.map { message -> message.id })
        assertEquals(45, newer.newerMessageCount)
        // 页面恢复保持已加载窗口的非对称两侧，不能重新居中后丢失较新消息。
        val restored = chatRepository.getChatPageData(
            sessionId = chatSessionId,
            pageSize = 21,
            anchorMessageId = ids[40],
            olderWindowSize = 5,
        ).page
        assertEquals(
            ids.subList(fromIndex = 35, toIndex = 56),
            restored.messages.map { message -> message.id })
        val older = chatRepository.getMessagePageBefore(
            sessionId = chatSessionId,
            beforeCreateTime = 35,
            beforeMessageId = ids[35],
            pageSize = 10,
        )
        assertEquals(
            ids.subList(fromIndex = 25, toIndex = 35),
            older.messages.map { message -> message.id })
    }

    @Test
    fun groupSearchWindowRetainsBothSidesAndRejectsWrongSession() = runBlocking {
        val ids = (0..60).map { index ->
            groupChatRepository.createMessage(
                sessionId = groupChatSessionId,
                source = GroupChatMessage.Source.User,
                content = "group-$index",
                speakerCharacterId = null,
                speakerNameSnapshot = "Alice",
                createTime = index.toLong(),
            )
        }
        val page = groupChatRepository.getGroupChatPageData(
            sessionId = groupChatSessionId,
            pageSize = 10,
            anchorMessageId = ids[20],
        )!!
        assertEquals(
            ids.subList(fromIndex = 15, toIndex = 26),
            page.data.messages.map { message -> message.id })
        assertEquals(35, page.newerMessageCount)
        val newer = groupChatRepository.getMessagePageAfter(
            sessionId = groupChatSessionId,
            afterMessageId = ids[25],
            pageSize = 10,
        )!!
        assertEquals(
            ids.subList(fromIndex = 26, toIndex = 36),
            newer.messages.map { message -> message.id })
        assertEquals(25, newer.newerMessageCount)
        // 同名或其它会话的锚点不能改变导航归属。
        assertEquals(
            null,
            groupChatRepository.getMessagePageAfter(
                sessionId = groupChatSessionId + 100,
                afterMessageId = ids[25],
                pageSize = 10,
            ),
        )
        val restored = groupChatRepository.getGroupChatPageData(
            sessionId = groupChatSessionId,
            pageSize = 21,
            anchorMessageId = ids[20],
            olderWindowSize = 5,
        )!!
        assertEquals(
            ids.subList(fromIndex = 15, toIndex = 36),
            restored.data.messages.map { message -> message.id })
    }

    @Test
    fun chatPagePreservesCharacterCapabilityOutsideLatestWindow() = runBlocking {
        // 角色消息只存在于分页窗口之外，最近窗口全部为用户消息
        val characterMessageId = chatRepository.createMessage(
            sessionId = chatSessionId,
            source = ChatMessage.Source.Char,
            content = "character-message",
            createTime = 1L,
        )
        (2L..4L).forEach { createTime ->
            chatRepository.createMessage(
                sessionId = chatSessionId,
                source = ChatMessage.Source.User,
                content = "user-message-$createTime",
                createTime = createTime,
            )
        }

        val pageData = chatRepository.getChatPageData(sessionId = chatSessionId, pageSize = 2)

        // 页面能力来自完整会话，而不是当前返回的两条消息
        assertEquals(
            listOf("user-message-3", "user-message-4"),
            pageData.page.messages.map { message -> message.content },
        )
        assertFalse(pageData.page.messages.any { message -> message.source == ChatMessage.Source.Char })
        assertTrue(pageData.hasCharacterMessage)

        // 删除唯一角色消息后，会话级能力随下一次页面快照失效
        chatRepository.deleteMessage(characterMessageId)
        assertFalse(
            chatRepository.getChatPageData(
                sessionId = chatSessionId,
                pageSize = 2
            ).hasCharacterMessage
        )
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
            createTime = 1L,
        )
        (2L..4L).forEach { createTime ->
            groupChatRepository.createMessage(
                sessionId = groupChatSessionId,
                source = GroupChatMessage.Source.User,
                content = "user-message-$createTime",
                speakerCharacterId = null,
                speakerNameSnapshot = "Alice",
                createTime = createTime,
            )
        }

        val pageData = groupChatRepository.getGroupChatPageData(
            sessionId = groupChatSessionId,
            pageSize = 2,
        ) ?: error("Group chat should exist")

        // 页面能力来自完整群聊，而不是当前返回的两条消息
        assertEquals(
            listOf("user-message-3", "user-message-4"),
            pageData.data.messages.map { message -> message.content },
        )
        assertFalse(pageData.data.messages.any { message -> message.source == GroupChatMessage.Source.Character })
        assertTrue(pageData.hasCharacterMessage)

        // 删除唯一角色消息后，群聊会话级能力随下一次页面快照失效
        groupChatRepository.deleteMessage(characterMessageId)
        val refreshed = groupChatRepository.getGroupChatPageData(
            sessionId = groupChatSessionId,
            pageSize = 2,
        ) ?: error("Group chat should exist")
        assertFalse(refreshed.hasCharacterMessage)
    }
}
