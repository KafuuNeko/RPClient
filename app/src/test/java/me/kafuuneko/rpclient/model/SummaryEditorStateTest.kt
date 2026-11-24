package me.kafuuneko.rpclient.model

import me.kafuuneko.rpclient.libs.room.model.MessageKey
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.model.MessageWithImages
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot
import me.kafuuneko.rpclient.libs.room.model.SummaryVersion
import me.kafuuneko.rpclient.libs.room.model.SummaryDraftVersion
import me.kafuuneko.rpclient.libs.room.model.withDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证摘要多轮生成、连续编辑和撤销时的版本顺序、覆盖位置与输入依赖。 */
class SummaryEditorStateTest {
    private val mSaved = SummaryVersion(content = "A", coveredMessageId = 2L, id = 10L)
    private val mInput = SummaryInputSnapshot(emptyList(), "persisted-A")

    /** 用户示例必须精确回退 D → C → B → A，回到基线后不再提示保存。 */
    @Test
    fun restoresEditedThenGeneratedVersionsInOrder() {
        var state = SummaryEditorState(saved = mSaved, latestMessageId = 4L)
            .generate("B", 4L).generate("C", 4L)
        // 连续改写合并为一轮编辑，不能暴露中间按键状态。
        for (text in listOf("C plus", "C rewritten", "D")) state = state.copy(text = text)
        assertTrue(state.hasChanges)
        assertEquals("A", state.saved.content)
        for (expected in listOf("C", "B", "A")) {
            state = requireNotNull(state.restoreLocal())
            assertEquals(expected, state.text)
        }
        assertFalse(state.hasChanges)
        assertFalse(state.canResummarize)
        assertEquals(2L, state.effectiveSummary.coveredMessageId)
        assertNull(state.restoreLocal())
    }

    /** 改写后再次生成仍保留改写前的机器稿，生成分支不丢失用户手稿。 */
    @Test
    fun generationAfterManualEditingKeepsBothCheckpoints() {
        var state = SummaryEditorState(saved = mSaved).generate("B", 3L).copy(text = "D")
            .generate("E", 4L)
        for (expected in listOf("D", "B", "A")) {
            state = requireNotNull(state.restoreLocal())
            assertEquals(expected, state.text)
        }
    }

    /** 一次保存包含本轮全部有效版本，已保存基线不重复追加，撤销后的版本不提交。 */
    @Test
    fun commitsAllActiveVersionsAndDropsUndoneSuccessors() {
        val state = SummaryEditorState(saved = mSaved)
            .generate("B", 3L).generate("C", 4L).copy(text = "Edited C")
        assertEquals(
            listOf("B", "C", "Edited C"),
            state.toCommit()?.versions?.map { it.summary.content })
        val restored = requireNotNull(requireNotNull(state.restoreLocal()).restoreLocal())
        assertEquals(listOf("B"), restored.toCommit()?.versions?.map { it.summary.content })
        val cleared = state.copy(text = "")
        assertEquals(listOf(""), cleared.toCommit()?.versions?.map { it.summary.content })
        val restarted = requireNotNull(cleared.generate("New", 4L).toCommit())
        assertEquals(listOf("", "New"), restarted.versions.map { it.summary.content })
        assertNull(restarted.history.first().inputSnapshot)
        assertTrue(restarted.history.first().sources.isEmpty())
    }

    /** 分批重做的中间稿同样可保存，即使最终正文与已保存摘要相同也保留生成历史。 */
    @Test
    fun replayBatchesRemainSaveableWhenFinalMemoryMatchesBaseline() {
        val intermediate = SummaryDraftVersion(
            summary = SummaryVersion(content = "Batch", coveredMessageId = 1L),
            inputSnapshot = mInput,
            sources = listOf(mSaved),
        )
        val state = SummaryEditorState(saved = mSaved).generated(
            content = mSaved.content,
            coveredMessageId = mSaved.coveredMessageId,
            inputSnapshot = mInput,
            sources = listOf(mSaved),
            intermediateVersions = listOf(intermediate),
        )
        assertTrue(state.hasChanges)
        assertEquals(listOf("Batch", "A"), state.toCommit()?.versions?.map { it.summary.content })
        assertEquals("Batch", requireNotNull(state.restoreLocal()).text)
    }

    /** 相同正文推进覆盖位置仍是新版本；完全相同的结果不产生额外撤销步骤。 */
    @Test
    fun comparesMemoryUsingContentAndCoverage() {
        val base = SummaryEditorState(saved = mSaved, latestMessageId = 4L)
        assertFalse(base.copy(text = "  A  ").hasChanges)
        val advanced = base.generate("A", 4L)
        assertTrue(advanced.hasChanges)
        assertTrue(advanced.canResummarize)
        val duplicate = advanced.generate("  A  ", 4L)
        assertEquals(1, duplicate.previous.size)
        assertFalse(requireNotNull(duplicate.restoreLocal()).hasChanges)
    }

    /** 手工改写部分摘要不能把尚未总结的消息标记为已覆盖，清空则恢复全部原文。 */
    @Test
    fun manualEditsAndClearingKeepCorrectCoverage() {
        val base = SummaryEditorState(saved = mSaved, latestMessageId = 4L)
        assertEquals(2L, base.copy(text = "Edited A").toCommit()?.summary?.coveredMessageId)
        val cleared = base.copy(text = " ")
        assertEquals(0L, cleared.toCommit()?.summary?.coveredMessageId)
        assertFalse(cleared.canResummarize)
        assertEquals("A", requireNotNull(cleared.restoreLocal()).text)
        val handwritten = SummaryEditorState(latestMessageId = 4L).copy(text = "Manual memory")
        assertEquals(4L, handwritten.toCommit()?.summary?.coveredMessageId)
    }

    /** 尚无摘要时也能回退到空草稿；放弃或回到空基线后无需写空快照。 */
    @Test
    fun firstGeneratedSummaryCanBeUndoneToNoMemory() {
        val state = SummaryEditorState(latestMessageId = 4L).generate("B", 4L)
        val restored = requireNotNull(state.restoreLocal())
        assertEquals("", restored.text)
        assertNull(restored.toCommit())
        assertNull(restored.restoreLocal())
    }

    /** 从空白记忆手写后重做需保留空白基础，主动清空不再依赖被删除的生成输入。 */
    @Test
    fun clearingMemorySeparatesNewGenerationFromOldDependencies() {
        val handwritten = SummaryEditorState(latestMessageId = 4L).copy(text = "Manual memory")
        assertEquals("", handwritten.toGenerationContext().previous.last().summary.content)
        val cleared = SummaryEditorState(saved = mSaved).generate("B", 4L).copy(text = "")
        assertNull(cleared.toCommit()?.inputSnapshot)
        assertTrue(cleared.toCommit()?.sources.orEmpty().isEmpty())
        assertNull(cleared.toGenerationContext().current.inputSnapshot)
    }

    /** 清空后生成再恢复，手写摘要不能重新依赖清空前的生成输入。 */
    @Test
    fun restoringClearedCheckpointKeepsHandwrittenSummaryIndependent() {
        val state = SummaryEditorState(saved = mSaved, latestMessageId = 4L)
            .generate("B", 4L)
            .copy(text = " \n")
            .generated("C", 4L, mInput.copy(summaryVersion = "cleared"), emptyList())
        val restored = requireNotNull(state.restoreLocal())
        assertTrue(restored.text.isBlank())
        // 空白检查点恢复后的新手稿不应重新激活旧输入或旧摘要依赖。
        val handwritten = restored.copy(text = "Manual memory")
        val commit = requireNotNull(handwritten.toCommit())
        assertNull(commit.inputSnapshot)
        assertTrue(commit.sources.isEmpty())
        val context = handwritten.toGenerationContext()
        assertNull(context.current.inputSnapshot)
        assertTrue(context.current.sources.isEmpty())
        assertEquals(4L, commit.summary.coveredMessageId)
    }

    /** 重新摘要会取前一份低覆盖位置的检查点，清空稿必须切断旧分支依赖。 */
    @Test
    fun replayBaseAfterClearingHasNoOldDependencies() {
        val state = SummaryEditorState(saved = mSaved, latestMessageId = 4L)
            .generate("B", 4L)
            .copy(text = "")
            .generated("C", 4L, mInput.copy(summaryVersion = "cleared"), emptyList())
        val checkpoint = state.toGenerationContext().previous.last()
        assertEquals("", checkpoint.summary.content)
        assertEquals(0L, checkpoint.summary.coveredMessageId)
        assertNull(checkpoint.inputSnapshot)
        assertTrue(checkpoint.sources.isEmpty())
    }

    /** 已保存摘要的手工改写是新草稿，保留历史游标但不能冒用持久化主键。 */
    @Test
    fun manuallyEditedCheckpointHasNoPersistedId() {
        val state = SummaryEditorState(saved = mSaved, latestMessageId = 4L)
            .copy(text = "Edited A")
            .generate("B", 4L)
        val restored = requireNotNull(state.restoreLocal())
        assertEquals("Edited A", restored.text)
        assertNull(restored.current.summary.id)
        assertEquals(mSaved.id, restored.current.historyCursor)
        assertEquals(listOf(mSaved), restored.current.sources)
        assertNull(requireNotNull(state.toCommit()).history.single().summary.id)
    }

    /** 恢复旧稿后再生成和撤销，历史游标仍指向旧稿之前而非打开表单时的最新稿。 */
    @Test
    fun restoredHistoryKeepsItsCursorAndOriginalSaveBaseline() {
        val older = SummaryVersion(content = "Older", coveredMessageId = 1L, id = 5L)
        val restored = SummaryEditorState(saved = mSaved).restorePersisted(older)
        assertEquals(mSaved, restored.toCommit()?.baseline)
        assertEquals(listOf(older), restored.toCommit()?.sources)
        assertEquals(
            5L,
            requireNotNull(restored.generate("B", 4L).restoreLocal()).current.historyCursor
        )
    }

    /** 撤销到某份生成稿时，保存必须使用它自己的输入，不能沿用被撤销的较新请求。 */
    @Test
    fun undoRestoresTheMatchingGenerationDependencies() {
        val firstInput = mInput.copy(messages = listOf(message(3L)))
        val secondInput = mInput.copy(messages = listOf(message(4L)))
        val first =
            SummaryEditorState(saved = mSaved).generated("B", 3L, firstInput, listOf(mSaved))
        val second = first.generated("C", 4L, secondInput, emptyList())
        val restored = requireNotNull(second.restoreLocal()).toCommit()
        assertEquals(firstInput, restored?.inputSnapshot)
        assertEquals(listOf(mSaved), restored?.sources)
        assertEquals(3L, restored?.summary?.coveredMessageId)
    }

    /** 后续增量稿继承前序所有输入，重叠消息仅保留一份以便事务内复核。 */
    @Test
    fun incrementalInputDependenciesAreMergedWithoutLosingEarlierMessages() {
        val first = mInput.copy(messages = listOf(message(1L), message(2L)))
        val second = mInput.copy(messages = listOf(message(2L), message(3L)))
        val merged = second.withDependency(first)
        assertEquals(listOf(1L, 2L, 3L), merged.messages.map { it.key.messageId })
    }

    /** 两轮之间原始消息发生修改时不能把相互矛盾的版本合并为可保存草稿。 */
    @Test(expected = IllegalArgumentException::class)
    fun incrementalInputRejectsChangedMessages() {
        mInput.copy(messages = listOf(message(1L).copy(content = "Changed")))
            .withDependency(mInput.copy(messages = listOf(message(1L))))
    }

    /** 摘要基线变化同样使跨轮合成结果失效，避免旧记忆覆盖新摘要。 */
    @Test(expected = IllegalArgumentException::class)
    fun incrementalInputRejectsChangedSummaryBaseline() {
        mInput.copy(summaryVersion = "new summary").withDependency(mInput)
    }

    private fun SummaryEditorState.generate(content: String, boundary: Long): SummaryEditorState =
        generated(content, boundary, mInput, listOf(mSaved))

    private fun message(id: Long): MessageWithImages = MessageWithImages(
        key = MessageKey(MessageType.Single, id), content = "message-$id",
        source = "User", createTime = id, images = emptyList(),
    )
}
