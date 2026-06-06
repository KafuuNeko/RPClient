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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRepositorySummaryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ChatRepository
    private var sessionId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database, Gson())

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
        sessionId = database.getChatSessionDao().insertOrReplace(
            ChatSession(
                characterId = characterId,
                createTime = 1L,
                latestTime = 1L,
                lorebookEntrySet = "[]",
                title = "Test",
                userNote = ""
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun summarySnapshotsFollowCoveredMessageBoundary() = runBlocking {
        val firstMessages = (1..6).map { index ->
            repository.createMessage(
                sessionId = sessionId,
                source = ChatMessage.Source.User,
                content = "message-$index",
                createTime = index.toLong()
            )
        }
        repository.saveSummary(sessionId, "summary-through-5", firstMessages[4])

        val laterMessages = (7..10).map { index ->
            repository.createMessage(
                sessionId = sessionId,
                source = ChatMessage.Source.Char,
                content = "message-$index",
                createTime = index.toLong()
            )
        }
        repository.saveSummary(sessionId, "summary-through-10", laterMessages.last())

        val summaryAtSix = repository.getLatestSummaryAtOrBefore(sessionId, firstMessages[5])
        assertEquals("summary-through-5", summaryAtSix?.content)
        assertEquals(10, repository.getMessageCountBySessionId(sessionId))
        assertEquals("message-10", repository.getLatestMessageBySessionId(sessionId)?.content)

        val branchId = repository.createBranchSession(
            sourceSessionId = sessionId,
            throughMessageId = firstMessages[5],
            title = "Branch",
            createTime = 100L
        )
        val branchContext = repository.getSummaryContext(branchId)
        assertEquals("summary-through-5", branchContext.summary?.content)
        assertEquals(listOf("message-6"), branchContext.messagesAfterSummary.map { it.content })
        assertEquals(6, repository.getMessageCountBySessionId(branchId))

        repository.updateMessageContent(firstMessages[5], "edited-message-6")

        val contextAfterEdit = repository.getSummaryContext(sessionId)
        assertEquals("summary-through-5", contextAfterEdit.summary?.content)
        assertEquals(
            listOf("edited-message-6", "message-7", "message-8", "message-9", "message-10"),
            contextAfterEdit.messagesAfterSummary.map { it.content }
        )
        assertTrue(contextAfterEdit.messagesAfterSummary.none { it.source == ChatMessage.Source.Summary })

        repository.updateCurrentSummary(sessionId, "")
        val contextAfterClear = repository.getSummaryContext(sessionId)
        assertEquals("", contextAfterClear.summary?.content)
        assertEquals(10, contextAfterClear.messagesAfterSummary.size)

        repository.updateCurrentSummary(sessionId, "manual-summary")
        val contextAfterManualSummary = repository.getSummaryContext(sessionId)
        assertEquals("manual-summary", contextAfterManualSummary.summary?.content)
        assertTrue(contextAfterManualSummary.messagesAfterSummary.isEmpty())
    }
}
