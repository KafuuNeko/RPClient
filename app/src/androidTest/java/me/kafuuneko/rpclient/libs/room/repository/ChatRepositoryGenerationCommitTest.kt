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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRepositoryGenerationCommitTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ChatRepository
    private var sessionId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(
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
        sessionId = database.getChatSessionDao().insertOrReplace(
            data = ChatSession(
                characterId = characterId,
                createTime = 1L,
                latestTime = 10L,
                lorebookEntrySet = "[]",
                title = "Test",
                userNote = "",
                userName = "User",
                userDescription = "",
                worldInfoStateJson = "{\"turn\":0}",
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun acceptedPartial_updatesMessageSummaryTimeAndWorldInfoTogether() = runBlocking {
        val messageId = repository.createMessage(
            sessionId = sessionId,
            source = ChatMessage.Source.Char,
            content = "old",
            createTime = 20L,
        )
        repository.saveSummary(
            sessionId = sessionId,
            content = "stale summary",
            coveredMessageId = messageId,
            createTime = 21L,
        )

        repository.commitGenerationResult(
            sessionId = sessionId,
            messageId = messageId,
            source = ChatMessage.Source.Char,
            content = "accepted partial",
            deleteEmptyPlaceholder = false,
            worldInfoStateJson = "{\"turn\":1}",
            commitTime = 30L,
        )

        assertEquals("accepted partial", repository.getMessageById(messageId)?.content)
        assertNull(repository.getLatestSummary(sessionId))
        val session = repository.getSessionById(sessionId)
        assertEquals(30L, session?.latestTime)
        assertEquals("{\"turn\":1}", session?.worldInfoStateJson)
    }

    @Test
    fun emptyPlaceholder_isDeletedWithoutAdvancingSessionMetadata() = runBlocking {
        val placeholderId = repository.createGenerationPlaceholder(
            sessionId = sessionId,
            source = ChatMessage.Source.Char,
            createTime = 20L,
        )
        assertEquals(10L, repository.getSessionById(sessionId)?.latestTime)

        val committedId = repository.commitGenerationResult(
            sessionId = sessionId,
            messageId = placeholderId,
            source = ChatMessage.Source.Char,
            content = "",
            deleteEmptyPlaceholder = true,
            worldInfoStateJson = "{\"turn\":1}",
            commitTime = 30L,
        )

        assertNull(committedId)
        assertNull(repository.getMessageById(placeholderId))
        val session = repository.getSessionById(sessionId)
        assertEquals(10L, session?.latestTime)
        assertEquals("{\"turn\":0}", session?.worldInfoStateJson)
    }
}
