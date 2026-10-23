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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionOverviewRepositoryTest {
    private lateinit var mDatabase: AppDatabase
    private lateinit var mChatRepository: ChatRepository
    private lateinit var mGroupChatRepository: GroupChatRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mChatRepository = ChatRepository(
            mAppDatabase = mDatabase,
            mGson = Gson(),
            mImages = MessageImageRepository(
                mDatabase = mDatabase,
                mFiles = FileRepository(
                    mContext = context,
                    mAppDatabase = mDatabase,
                ),
            ),
        )
        mGroupChatRepository = GroupChatRepository(
            mAppDatabase = mDatabase,
            mGson = Gson(),
            mImages = MessageImageRepository(
                mDatabase = mDatabase,
                mFiles = FileRepository(
                    mContext = context,
                    mAppDatabase = mDatabase,
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        mDatabase.close()
    }

    @Test
    fun chatOverviewReturnsLatestOrdinaryMessageAndCount() = runBlocking {
        val characterId = insertCharacter("Solo")
        val sessionId = mDatabase.getChatSessionDao().insertOrReplace(
            data = ChatSession(
                characterId = characterId,
                createTime = 1L,
                latestTime = 10L,
                lorebookEntrySet = "[]",
                title = "Solo chat",
                userNote = "",
            ),
        )
        mDatabase.getChatMessageDao().insertOrReplaceAll(
            data = listOf(
                ChatMessage(
                    sessionId = sessionId,
                    createTime = 2L,
                    source = ChatMessage.Source.User,
                    content = "first",
                ),
                ChatMessage(
                    sessionId = sessionId,
                    createTime = 2L,
                    source = ChatMessage.Source.Char,
                    content = "latest",
                ),
                ChatMessage(
                    sessionId = sessionId,
                    createTime = 3L,
                    source = ChatMessage.Source.Summary,
                    content = "summary",
                ),
            ),
        )

        val overview = mChatRepository.getSessionOverviews().single()

        assertEquals(sessionId, overview.id)
        assertEquals("latest", overview.latestMessageContent)
        assertEquals(2, overview.messageCount)
    }

    @Test
    fun groupChatOverviewAggregatesMembersWithoutLoadingMessageHistory() = runBlocking {
        val firstCharacterId = insertCharacter("First")
        val secondCharacterId = insertCharacter("Second")
        val sessionId = mGroupChatRepository.createSession(
            title = "Group chat",
            userName = "User",
            userDescription = "",
            characterIds = listOf(secondCharacterId, firstCharacterId),
            activationStrategy = GroupChatSession.ActivationStrategy.Natural,
            allowSelfResponses = false,
            createTime = 1L,
        )
        mDatabase.getGroupChatMessageDao().insertOrReplaceAll(
            data = listOf(
                GroupChatMessage(
                    sessionId = sessionId,
                    createTime = 2L,
                    source = GroupChatMessage.Source.User,
                    content = "first",
                    speakerNameSnapshot = "User",
                ),
                GroupChatMessage(
                    sessionId = sessionId,
                    createTime = 2L,
                    source = GroupChatMessage.Source.Character,
                    content = "latest",
                    speakerCharacterId = firstCharacterId,
                    speakerNameSnapshot = "First",
                ),
            ),
        )

        val overview = mGroupChatRepository.getSessionOverviews().single()

        assertEquals(sessionId, overview.id)
        assertEquals("Second, First", overview.memberNames)
        assertEquals("latest", overview.latestMessageContent)
        assertEquals(2, overview.messageCount)
    }

    private suspend fun insertCharacter(name: String): Long {
        return mDatabase.getCharacterDao().insertOrReplace(
            data = Character(
                name = name,
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
    }
}
