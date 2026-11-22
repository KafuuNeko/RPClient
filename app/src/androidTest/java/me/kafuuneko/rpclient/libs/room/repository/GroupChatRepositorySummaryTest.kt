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
import me.kafuuneko.rpclient.libs.room.model.toSummaryVersion
import me.kafuuneko.rpclient.libs.room.model.withDependency
import me.kafuuneko.rpclient.model.SummaryEditorState
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

    /** 群聊使用相同草稿栈，增量与重做不入库，恢复和清空在设置事务内提交正确边界。 */
    @Test
    fun draftHistoryAndRestoreOnlyPersistTheSelectedMemoryOnSave() = runBlocking {
        val ids = (1..6).map { index ->
            repository.createMessage(
                sessionId,
                GroupChatMessage.Source.User,
                "message-$index",
                null,
                "User",
                createTime = index.toLong()
            )
        }
        repository.saveSummary(sessionId, "A", ids[1])
        val baseline = repository.getGroupChatData(sessionId)?.summary.toSummaryVersion()
        var editor = SummaryEditorState(saved = baseline, latestMessageId = ids.last())
        // 当前表单的配置与草稿摘要共同参与请求，不提前改变数据库。
        val first = requireNotNull(
            repository.getGroupChatSummaryData(
                sessionId,
                maxCandidateMessages = 2,
                manual = true,
                draft = editor.toGenerationContext(),
                editSession = { it.copy(userName = "Draft user", scenario = "Draft scene") },
            )
        )
        assertEquals("Draft user", first.data.persona.name)
        assertEquals("Draft scene", first.data.session.scenario)
        assertEquals("A", first.data.summary?.content)
        assertEquals(ids.subList(2, 4), first.data.messages.map { it.id })
        val firstInput =
            repository.getSummaryInputSnapshot(sessionId, first.data.messages.map { it.id })
        editor = editor.generated("B", ids[3], firstInput, first.draftBase?.sources.orEmpty())
        val second = requireNotNull(
            repository.getGroupChatSummaryData(
                sessionId,
                manual = true,
                draft = editor.toGenerationContext()
            )
        )
        assertEquals("B", second.data.summary?.content)
        assertEquals(ids.takeLast(2), second.data.messages.map { it.id })
        val secondInput =
            repository.getSummaryInputSnapshot(sessionId, second.data.messages.map { it.id })
                .withDependency(second.draftBase?.inputSnapshot)
        editor = editor.generated("C", ids.last(), secondInput, second.draftBase?.sources.orEmpty())
        for (mode in SummaryMode.entries) {
            val rerun = requireNotNull(
                repository.getGroupChatSummaryData(
                    sessionId,
                    manual = true,
                    mode = mode,
                    draft = editor.toGenerationContext()
                )
            )
            assertEquals("B", rerun.data.summary?.content)
            assertEquals(ids.last(), rerun.summaryToUpdate?.coveredMessageId)
            assertEquals(ids.last(), rerun.data.messages.last().id)
        }
        assertEquals(baseline, repository.getGroupChatData(sessionId)?.summary.toSummaryVersion())
        assertEquals("Alice", repository.getSessionById(sessionId)?.userName)
        editor = requireNotNull(editor.copy(text = "D").restoreLocal())
        repository.saveSettings(sessionId, editor.toCommit()) { it.copy(title = "Saved group") }
        val saved = repository.getGroupChatData(sessionId)?.summary.toSummaryVersion()
        assertEquals("C", saved.content)
        assertEquals(ids.last(), saved.coveredMessageId)
        val savedMiddle =
            requireNotNull(repository.getPreviousSummary(sessionId, requireNotNull(saved.id)))
        assertEquals("B", savedMiddle.content)
        assertEquals(ids[3], savedMiddle.coveredMessageId)
        assertEquals(
            baseline,
            repository.getPreviousSummary(sessionId, requireNotNull(savedMiddle.id))
        )
        val replay = requireNotNull(
            repository.getGroupChatSummaryData(
                sessionId = sessionId,
                maxCandidateMessages = 2,
                manual = true,
                draft = SummaryEditorState(
                    saved = saved,
                    latestMessageId = ids.last()
                ).toGenerationContext(),
            )
        )
        assertEquals("B", replay.data.summary?.content)
        assertEquals(ids.takeLast(2), replay.data.messages.map { it.id })
        assertEquals(false, replay.hasMoreCandidateMessages)
        // 恢复先只读；提交后旧稿仍保留，清空后全部消息重新进入上下文。
        val restored = SummaryEditorState(saved = saved).restorePersisted(baseline)
        assertEquals(saved, repository.getGroupChatData(sessionId)?.summary.toSummaryVersion())
        repository.saveSettings(sessionId, restored.toCommit()) { it }
        val restoredSaved = repository.getGroupChatData(sessionId)?.summary.toSummaryVersion()
        assertEquals(ids[1], restoredSaved.coveredMessageId)
        assertEquals(
            "C",
            repository.getPreviousSummary(sessionId, requireNotNull(restoredSaved.id))?.content
        )
        repository.saveSettings(
            sessionId,
            SummaryEditorState(saved = restoredSaved).copy(text = "").toCommit()
        ) { it }
        assertEquals(ids, repository.getMessagesAfterLatestSummary(sessionId).map { it.id })
    }

    /** 群聊后续草稿仍依赖前轮消息，外部修改使保存失败并回滚所有设置。 */
    @Test
    fun settingsSaveRejectsChangedEarlierInputAndExternalSummary() = runBlocking {
        val ids = (1..4).map { index ->
            repository.createMessage(
                sessionId,
                GroupChatMessage.Source.User,
                "message-$index",
                null,
                "User",
                createTime = index.toLong()
            )
        }
        repository.saveSummary(sessionId, "A", ids.first())
        val saved = repository.getGroupChatData(sessionId)?.summary.toSummaryVersion()
        val firstInput = repository.getSummaryInputSnapshot(sessionId, listOf(ids[1]))
        val secondInput = repository.getSummaryInputSnapshot(sessionId, listOf(ids[2]))
        val editor =
            SummaryEditorState(saved = saved)
                .generated("B", ids[1], firstInput, listOf(saved))
                .generated("C", ids[2], secondInput, listOf(saved))
        // 修改未被已保存摘要覆盖的原文，保留同一摘要基线以验证跨轮依赖校验。
        repository.updateMessageContent(ids[1], "edited earlier input")
        val result = runCatching {
            repository.saveSettings(
                sessionId,
                editor.toCommit()
            ) { it.copy(title = "Must roll back") }
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Test group", repository.getSessionById(sessionId)?.title)
        assertEquals(saved, repository.getGroupChatData(sessionId)?.summary.toSummaryVersion())
        repository.saveSummary(sessionId, "External", ids.last())
        val stale = SummaryEditorState(saved = saved).copy(text = "Stale edit")
        assertTrue(runCatching {
            repository.getGroupChatSummaryData(
                sessionId,
                manual = true,
                draft = stale.toGenerationContext()
            )
        }.isFailure)
        assertTrue(runCatching {
            repository.saveSettings(
                sessionId,
                stale.toCommit()
            ) { it }
        }.isFailure)
        assertEquals("External", repository.getGroupChatData(sessionId)?.summary?.content)
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
        assertEquals(ids.subList(2, 4), raw.messages.map { it.id })
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
        val messageIds = (1..5).map { index ->
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

        repository.saveSummary(sessionId, "memory through 5", messageIds.last())
        val continuation = requireNotNull(
            repository.getGroupChatPromptData(
                sessionId = sessionId, maxHistoryMessages = 2, keepLatestMessage = true,
            )
        )
        assertEquals("memory through 5", continuation.data.summary?.content)
        assertEquals(listOf(messageIds.last()), continuation.data.messages.map { it.id })
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
    fun rawAutomaticSummaryKeepsOnlyTheOldestEligibleGroupMessages() = runBlocking {
        val ids = (1..5).map { index ->
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
            listOf("message-1", "message-2"),
            limited.data.messages.map { message -> message.content },
        )
        assertTrue(limited.hasMoreCandidateMessages)
        assertEquals(5, repository.getUnsummarizedMessageCount(sessionId))

        val expanded = repository.getGroupChatSummaryData(
            sessionId = sessionId,
            maxCandidateMessages = 10,
        ) ?: error("Group chat should exist")
        assertEquals(
            listOf("message-1", "message-2", "message-3", "message-4"),
            expanded.data.messages.map { message -> message.content },
        )
        assertFalse(expanded.hasMoreCandidateMessages)

        // 只剩末条时手动摘要仍有素材，自动摘要无需启动。
        repository.saveSummary(sessionId, "memory through 4", ids[3])
        val automatic = requireNotNull(repository.getGroupChatSummaryData(sessionId))
        assertTrue(automatic.data.messages.isEmpty())
        val manual = requireNotNull(repository.getGroupChatSummaryData(sessionId, manual = true))
        assertEquals(listOf(ids.last()), manual.data.messages.map { it.id })
        assertEquals(null, manual.summaryToUpdate)
    }

    /** 群聊重做复用原素材，保存未修改摘要的设置表单时保留生成结果。 */
    @Test
    fun resummarizingReusesPreviousMemoryAndPreservesTheNewSnapshot() = runBlocking {
        val ids = (1..20).map { index ->
            repository.createMessage(
                sessionId = sessionId,
                source = GroupChatMessage.Source.User,
                content = "message-$index",
                speakerCharacterId = null,
                speakerNameSnapshot = "Alice",
                createTime = index.toLong(),
            )
        }
        val firstId = repository.saveSummary(sessionId, "memory through 10", ids[9])
        for (mode in SummaryMode.entries) {
            // 读取推进到末尾前后的按钮状态，历史分页不能改变动作。
            assertFalse(
                requireNotNull(
                    repository.getGroupChatPageData(
                        sessionId,
                        pageSize = 2
                    )
                ).canResummarize
            )
            val initial = requireNotNull(
                repository.getGroupChatSummaryData(
                    sessionId = sessionId, maxCandidateMessages = 10, mode = mode, manual = true,
                )
            )
            assertEquals(firstId, initial.data.summary?.id)
            assertEquals(ids.last(), initial.data.messages.last().id)
            val currentId = repository.saveSummary(sessionId, "memory through 20", ids.last())
            assertTrue(
                requireNotNull(
                    repository.getGroupChatPageData(
                        sessionId = sessionId, pageSize = 2, anchorMessageId = ids.first(),
                    )
                ).canResummarize
            )

            // 重新摘要原地替换当前快照，其他设置的保存不应补写一条旧草稿。
            val rerun = requireNotNull(
                repository.getGroupChatSummaryData(
                    sessionId = sessionId, maxCandidateMessages = 10, mode = mode, manual = true,
                )
            )
            assertEquals(initial.data, rerun.data)
            assertEquals(currentId, rerun.summaryToUpdate?.id)
            val snapshot =
                repository.getSummaryInputSnapshot(sessionId, rerun.data.messages.map { it.id })
            val updatedId = repository.saveSummary(
                sessionId = sessionId,
                content = "new memory through 20",
                coveredMessageId = ids.last(),
                summaryIdToUpdate = rerun.summaryToUpdate?.id,
                expectedSnapshot = snapshot,
            )
            assertEquals(currentId, updatedId)
            repository.saveSettings(sessionId = sessionId, summary = null) { session ->
                session.copy(title = "Renamed group")
            }
            assertEquals(
                "new memory through 20",
                repository.getGroupChatData(sessionId)?.summary?.content
            )
            assertTrue(repository.restorePreviousSummary(sessionId))
            assertEquals(firstId, repository.getGroupChatData(sessionId)?.summary?.id)
        }
    }
}
