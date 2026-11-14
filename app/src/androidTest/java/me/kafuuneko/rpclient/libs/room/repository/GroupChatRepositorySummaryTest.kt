package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.prompt.model.SummaryMode
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.withTestPersonas
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withTestPersonas()
            .allowMainThreadQueries()
            .build()
        repository = GroupChatRepository(
            mAppDatabase = database,
            mPersonas = UserPersonaRepository(database, FileRepository(context, database)),
            mGson = Gson(),
            mImages = MessageImageRepository(
                mDatabase = database,
                mFiles = FileRepository(
                    mContext = context,
                    mAppDatabase = database,
                ),
            ),
        )
        sessionId = runBlocking {
            database.getGroupChatSessionDao().insertOrReplace(
                data = GroupChatSession(
                    title = "Test group",
                    createTime = 1L,
                    latestTime = 1L,
                    userName = "Alice",
                    userDescription = "",
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** 重生成使用替换前的历史，失败或旧请求迟到时不能覆盖用户已编辑的回复。 */
    @Test
    fun regenerateLatestKeepsOriginalUntilCommitAndRejectsStaleResults() = runBlocking {
        val user = repository.createMessage(
            sessionId,
            GroupChatMessage.Source.User,
            "question",
            null,
            "User"
        )
        val reply = repository.createMessage(
            sessionId,
            GroupChatMessage.Source.Character,
            "original",
            1L,
            "Alice"
        )
        val original = requireNotNull(repository.getMessageById(reply))
        repository.saveSummary(sessionId, "summary of original reply", reply)

        // 准备模型请求时排除原回复及其摘要，原正文依然可读。
        val prompt = requireNotNull(
            repository.getGroupChatPromptData(
                sessionId = sessionId, maxHistoryMessages = 1, replacingReply = original,
            )
        )
        assertEquals(listOf(user), prompt.data.messages.map { it.id })
        assertEquals(1, prompt.totalMessageCount)
        assertEquals(null, prompt.data.summary)
        // 清空过的摘要应继续保持空白，不能因旧快照覆盖了更多消息而重新生效。
        repository.saveSummary(sessionId, "old memory", user)
        repository.updateCurrentSummary(sessionId, "")
        val cleared = requireNotNull(
            repository.getGroupChatPromptData(
                sessionId = sessionId, maxHistoryMessages = 1, replacingReply = original,
            )
        )
        assertEquals("", cleared.data.summary?.content)
        assertFalse(repository.replaceLatestReply(original, ""))
        assertEquals("original", repository.getMessageById(reply)?.content)

        // 只允许完整结果写回到同一条消息，迟到的旧结果不能覆盖新正文或后续消息。
        assertTrue(repository.replaceLatestReply(original, "new reply"))
        assertFalse(repository.replaceLatestReply(original, "late result"))
        val updated = requireNotNull(repository.getMessageById(reply))
        repository.createMessage(
            sessionId,
            GroupChatMessage.Source.User,
            "next question",
            null,
            "User"
        )
        assertFalse(repository.replaceLatestReply(updated, "another late result"))
        assertEquals("new reply", repository.getMessageById(reply)?.content)
        assertEquals("", repository.getGroupChatData(sessionId)?.summary?.content)
    }

    @Test
    fun rawAndClassicKeepIndependentWindowRulesAndSummaryRollback() = runBlocking {
        val ids = (1..6).map { index ->
            repository.createMessage(
                sessionId = sessionId, source = GroupChatMessage.Source.User,
                content = "message-$index", speakerCharacterId = null,
                speakerNameSnapshot = "Alice", createTime = index.toLong(),
            )
        }
        repository.saveSummary(sessionId, "existing memory", ids[1])
        // 模式只影响读取的窗口，尚未生成正文时原有覆盖位置必须保持不变。
        val raw = repository.getGroupChatSummaryData(
            sessionId = sessionId, maxCandidateMessages = 2, mode = SummaryMode.Raw,
        )!!.data
        val classic = repository.getGroupChatSummaryData(
            sessionId = sessionId, maxCandidateMessages = 2, mode = SummaryMode.Classic,
        )!!.data
        assertEquals(listOf(ids[2], ids[3], ids[5]), raw.messages.map { it.id })
        assertEquals(ids.takeLast(3), classic.messages.map { it.id })
        assertEquals(ids[1], repository.getGroupChatData(sessionId)?.summary?.coveredMessageId)
        val snapshot = repository.getSummaryInputSnapshot(sessionId, classic.messages.map { it.id })
        // Classic 成功后推进近期边界，同时保留旧摘要快照用于回滚。
        repository.saveSummary(
            sessionId = sessionId, content = "recent context memory",
            coveredMessageId = classic.messages.dropLast(1).last().id,
            expectedSnapshot = snapshot,
        )
        assertEquals(ids[4], repository.getGroupChatData(sessionId)?.summary?.coveredMessageId)
        assertEquals(1, repository.getUnsummarizedMessageCount(sessionId))
        assertTrue(repository.restorePreviousSummary(sessionId))
        assertEquals(ids[1], repository.getGroupChatData(sessionId)?.summary?.coveredMessageId)
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
                createTime = index.toLong(),
            )
        }
        repository.saveSummary(
            sessionId = sessionId,
            content = "summary-through-3",
            coveredMessageId = messageIds.last(),
        )
        assertTrue(repository.getMessagesAfterLatestSummary(sessionId).isEmpty())

        repository.updateCurrentSummary(sessionId = sessionId, content = "", createTime = 100L)

        val dataAfterClear = repository.getGroupChatData(sessionId)
            ?: error("Group chat should exist")
        assertEquals("", dataAfterClear.summary?.content)
        assertEquals(0L, dataAfterClear.summary?.coveredMessageId)
        assertEquals(
            listOf("message-1", "message-2", "message-3"),
            repository.getMessagesAfterLatestSummary(sessionId).map { message -> message.content },
        )

        repository.updateCurrentSummary(
            sessionId = sessionId,
            content = "manual-summary",
            createTime = 101L
        )

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
                createTime = index.toLong(),
            )
        }

        val limited = repository.getGroupChatPromptData(
            sessionId = sessionId,
            maxHistoryMessages = 2,
        ) ?: error("Group chat should exist")
        assertEquals(
            listOf("message-4", "message-5"),
            limited.data.messages.map { message -> message.content },
        )
        assertEquals(5, limited.totalMessageCount)

        val unlimited = repository.getGroupChatPromptData(
            sessionId = sessionId,
            maxHistoryMessages = 0,
        ) ?: error("Group chat should exist")
        assertEquals(5, unlimited.data.messages.size)
        assertEquals(5, unlimited.totalMessageCount)
    }

    @Test
    fun speakerSelectionDataMatchesFullHistorySemantics() = runBlocking {
        val session = repository.getSessionById(sessionId)
            ?: error("Group chat should exist")
        repository.updateSession(
            session = session.copy(activationStrategy = GroupChatSession.ActivationStrategy.Pooled),
        )
        val messages = listOf(
            Triple(first = GroupChatMessage.Source.Character, second = 1L, third = "Character 1"),
            Triple(first = GroupChatMessage.Source.User, second = null, third = "Alice"),
            Triple(first = GroupChatMessage.Source.Character, second = 2L, third = "Character 2"),
            Triple(first = GroupChatMessage.Source.System, second = null, third = "System"),
            Triple(first = GroupChatMessage.Source.Character, second = 1L, third = "Character 1"),
        )
        messages.forEachIndexed { index, (source, speakerId, speakerName) ->
            val messageNumber = index + 1
            repository.createMessage(
                sessionId = sessionId,
                source = source,
                content = "message-$messageNumber",
                speakerCharacterId = speakerId,
                speakerNameSnapshot = speakerName,
                createTime = (index + 1).toLong(),
            )
        }

        val selectionData = repository.getSpeakerSelectionData(sessionId)
            ?: error("Group chat should exist")

        assertEquals(setOf(1L, 2L), selectionData.spokenCharacterIdsSinceLastUserMessage)
        assertEquals(1L, selectionData.lastCharacterSpeakerId)
        assertEquals("message-5", selectionData.latestNonSystemContent)
    }

    @Test
    fun summaryGenerationDataKeepsOldestCandidatesAndLatestExclusionSentinel() = runBlocking {
        (1..5).forEach { index ->
            repository.createMessage(
                sessionId = sessionId,
                source = GroupChatMessage.Source.User,
                content = "message-$index",
                speakerCharacterId = null,
                speakerNameSnapshot = "Alice",
                createTime = index.toLong(),
            )
        }

        val limited = repository.getGroupChatSummaryData(
            sessionId = sessionId,
            maxCandidateMessages = 2,
        ) ?: error("Group chat should exist")
        assertEquals(
            listOf("message-1", "message-2", "message-5"),
            limited.data.messages.map { message -> message.content },
        )
        assertTrue(limited.hasMoreCandidateMessages)
        assertEquals(5, repository.getUnsummarizedMessageCount(sessionId))

        val expanded = repository.getGroupChatSummaryData(
            sessionId = sessionId,
            maxCandidateMessages = 10,
        ) ?: error("Group chat should exist")
        assertEquals(
            listOf("message-1", "message-2", "message-3", "message-4", "message-5"),
            expanded.data.messages.map { message -> message.content },
        )
        assertFalse(expanded.hasMoreCandidateMessages)
    }
}
