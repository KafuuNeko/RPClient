package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupChatRepositorySummaryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: GroupChatRepository
    private var sessionId: Long = 0L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GroupChatRepository(database, Gson())
        sessionId = runBlocking {
            database.getGroupChatSessionDao().insertOrReplace(
                GroupChatSession(
                    title = "Test group",
                    createTime = 1L,
                    latestTime = 1L,
                    userName = "Alice",
                    userDescription = ""
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearingSummaryRestoresAllMessagesToUnsummarizedHistory() = runBlocking {
        val messageIds = (1..3).map { index ->
            repository.createMessage(
                sessionId = sessionId,
                source = if (index % 2 == 0) {
                    GroupChatMessage.Source.Character
                } else {
                    GroupChatMessage.Source.User
                },
                content = "message-$index",
                speakerCharacterId = null,
                speakerNameSnapshot = if (index % 2 == 0) "Character" else "Alice",
                createTime = index.toLong()
            )
        }
        repository.saveSummary(
            sessionId = sessionId,
            content = "summary-through-3",
            coveredMessageId = messageIds.last()
        )
        assertTrue(repository.getMessagesAfterLatestSummary(sessionId).isEmpty())

        repository.updateCurrentSummary(sessionId, "", createTime = 100L)

        val dataAfterClear = repository.getGroupChatData(sessionId)
            ?: error("Group chat should exist")
        assertEquals("", dataAfterClear.summary?.content)
        assertEquals(0L, dataAfterClear.summary?.coveredMessageId)
        assertEquals(
            listOf("message-1", "message-2", "message-3"),
            repository.getMessagesAfterLatestSummary(sessionId).map { it.content }
        )

        repository.updateCurrentSummary(sessionId, "manual-summary", createTime = 101L)

        val dataAfterManualSummary = repository.getGroupChatData(sessionId)
            ?: error("Group chat should exist")
        assertEquals("manual-summary", dataAfterManualSummary.summary?.content)
        assertEquals(messageIds.last(), dataAfterManualSummary.summary?.coveredMessageId)
        assertTrue(repository.getMessagesAfterLatestSummary(sessionId).isEmpty())

        assertTrue(repository.restorePreviousSummary(sessionId))
        val dataAfterRestore = repository.getGroupChatData(sessionId)
            ?: error("Group chat should exist")
        assertEquals("", dataAfterRestore.summary?.content)
        assertEquals(0L, dataAfterRestore.summary?.coveredMessageId)
    }

    @Test
    fun promptDataUsesLatestWindowAndKeepsFullMessageCount() = runBlocking {
        (1..5).forEach { index ->
            repository.createMessage(
                sessionId = sessionId,
                source = GroupChatMessage.Source.User,
                content = "message-$index",
                speakerCharacterId = null,
                speakerNameSnapshot = "Alice",
                createTime = index.toLong()
            )
        }

        val limited = repository.getGroupChatPromptData(
            sessionId = sessionId,
            maxHistoryMessages = 2
        ) ?: error("Group chat should exist")
        assertEquals(
            listOf("message-4", "message-5"),
            limited.data.messages.map { it.content }
        )
        assertEquals(5, limited.totalMessageCount)

        val unlimited = repository.getGroupChatPromptData(
            sessionId = sessionId,
            maxHistoryMessages = 0
        ) ?: error("Group chat should exist")
        assertEquals(5, unlimited.data.messages.size)
        assertEquals(5, unlimited.totalMessageCount)
    }

    @Test
    fun speakerSelectionDataMatchesFullHistorySemantics() = runBlocking {
        val session = repository.getSessionById(sessionId)
            ?: error("Group chat should exist")
        repository.updateSession(
            session.copy(activationStrategy = GroupChatSession.ActivationStrategy.Pooled)
        )
        val messages = listOf(
            Triple(GroupChatMessage.Source.Character, 1L, "Character 1"),
            Triple(GroupChatMessage.Source.User, null, "Alice"),
            Triple(GroupChatMessage.Source.Character, 2L, "Character 2"),
            Triple(GroupChatMessage.Source.System, null, "System"),
            Triple(GroupChatMessage.Source.Character, 1L, "Character 1")
        )
        messages.forEachIndexed { index, (source, speakerId, speakerName) ->
            repository.createMessage(
                sessionId = sessionId,
                source = source,
                content = "message-${index + 1}",
                speakerCharacterId = speakerId,
                speakerNameSnapshot = speakerName,
                createTime = (index + 1).toLong()
            )
        }

        val selectionData = repository.getSpeakerSelectionData(sessionId)
            ?: error("Group chat should exist")

        assertEquals(setOf(1L, 2L), selectionData.spokenCharacterIdsSinceLastUserMessage)
        assertEquals(1L, selectionData.lastCharacterSpeakerId)
        assertEquals("message-5", selectionData.latestNonSystemContent)
    }
}
