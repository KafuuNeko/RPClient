package me.kafuuneko.rpclient.libs.memory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType
import me.kafuuneko.rpclient.libs.prompt.request.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizer
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.MemoryContent
import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 普通召回的词项排序、整条裁剪与协议重计量边界。 */
class MemoryRecallTest {
    private val mTokenizer = object : PromptTokenizer {
        override val name = "Length fixture"
        override val strategy = PromptTokenizerStrategy.ModelAware
        override fun countText(text: String) = text.length
    }
    private val mFinalizer = PromptRequestFinalizer { mTokenizer }
    private val mTemplates = PromptTemplateSnapshot.defaults().let {
        it.copy(
            values = it.values + mapOf(
                PromptType.CharacterMemoryContext to "<mem>{{memory}}</mem>",
                PromptType.CharacterMemoryItem to "<fact>{{fact}}</fact>",
            ),
        )
    }

    /** Unicode 归一、中文双字和稳定排序共同防止无关事实进入正文。 */
    @Test
    fun rankingUsesPinnedThenNormalizedRelevanceImportanceAndStableId() {
        // 构造置顶、重复命中、同分重要度与无关候选并校验确定性排序结果。
        val entries = listOf(
            entry(3, "喝茶不加糖", 40),
            entry(2, "喝茶不加糖", 40),
            entry(4, "Coffee", 100),
            entry(5, "unrelated", 0, true),
            entry(6, "tea tea tea", 1),
        )
        assertEquals(
            listOf(5L, 6L, 2L, 3L),
            MemoryRetriever.rank(entries, listOf("ＴＥＡ 喝茶")).map { it.ref.id },
        )
        // 验证思考块内容被剔除后仅召回置顶项，且中英边界不粘连分词。
        assertEquals(
            listOf(5L),
            MemoryRetriever.rank(entries, listOf("<think>喝茶</think>!?")).map { it.ref.id },
        )
        assertTrue("喝茶" in MemoryRetriever.terms("abc喝茶"))
        assertFalse("abc喝" in MemoryRetriever.terms("abc喝茶"))
    }

    /** 独立 CJK 单字保留召回能力，拉丁单字符噪声仍过滤。 */
    @Test
    fun standaloneCjkCharactersRemainSearchable() {
        assertEquals(
            setOf("剑", "茶", "tea", "abc", "喝茶"),
            MemoryRetriever.terms("剑，茶 a ＴＥＡ abc喝茶")
        )
        assertEquals(
            listOf(2L),
            MemoryRetriever.rank(
                listOf(entry(1, "茶", 100), entry(2, "剑", 0)),
                listOf("剑"),
            ).map { it.ref.id },
        )
    }

    /** 同样命中时短精准事实优先，重复词不加分，置顶和重要度只在既定层级内生效。 */
    @Test
    fun lengthNormalizationFavorsFocusedFactsOverVerboseMemories() {
        val entries = listOf(
            entry(1, "tea sugar " + (1..40).joinToString(" ") { "filler$it" }, 100),
            entry(2, "tea sugar", 0),
            entry(3, "tea tea tea", 30),
            entry(4, "unrelated", 0, pinned = true),
        )
        assertEquals(
            listOf(4L, 2L, 3L, 1L),
            MemoryRetriever.rank(entries, listOf("tea sugar")).map { it.ref.id },
        )
    }

    /** 主体命中额外加权，也可单独提供相关性；无命中的高重要度条目仍排除。 */
    @Test
    fun subjectKeyMatchesContributeExtraRelevance() {
        val entries = listOf(
            entry(1, "tea", 0, subjectKey = "tea"),
            entry(2, "tea", 100),
            entry(3, "habit", 0, subjectKey = "tea"),
            entry(4, "unrelated", 100),
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            MemoryRetriever.rank(entries, listOf("tea")).map { it.ref.id },
        )
    }

    /** 记忆条数、独立 Token 与整体剩余额度不足均只记录记忆预算原因。 */
    @Test
    fun memoryBudgetOmissionsDoNotReportContextTrimming() {
        val protocol = LLMProviderProtocol.OpenAIResponses
        val mode = PromptPostProcessingMode.None
        val baseline = build(protocol, mode, null)
        // 基线完整保留时，任何记忆限额都不能制造聊天上下文裁剪原因。
        val cases = listOf(
            memory(listOf("first", "second")).copy(maxItems = 1) to 4096,
            memory(listOf("too large")).copy(maxTokens = 1) to 4096,
            memory(listOf("tiny")) to (baseline.inspection.finalTokenCount + 10),
        )
        cases.forEach { (input, limit) ->
            val result = build(protocol, mode, input, limit)
            assertTrue(result.inspection.omittedItems.isNotEmpty())
            assertTrue(result.inspection.omittedItems.all { it.reason == PromptOmissionReason.MemoryBudget })
            assertEquals(
                baseline.retainedDrafts.map { it.content },
                result.retainedDrafts.filterNot { it.source.kind == PromptSourceKind.Memory }
                    .map { it.content },
            )
        }
    }

    /** 读取失败只增加脱敏诊断，保留实际请求和基线已有的真实裁剪原因。 */
    @Test
    fun recallErrorsKeepBaselineAndRecordUnavailableWithoutExceptionDetails() = runBlocking {
        val protocol = LLMProviderProtocol.OpenAIResponses
        val mode = PromptPostProcessingMode.None
        val baseline = build(protocol, mode, null, limit = 90)
        // 故意携带敏感样式异常文本，诊断只能保存结构化的不可用原因。
        val input = requireNotNull(MemoryRetriever.prepareWithFallback(memory(emptyList())) {
            error("private database details")
        })
        val result = build(protocol, mode, input, limit = 90)
        assertEquals(baseline.request, result.request)
        assertTrue(baseline.inspection.omittedItems.any { it.reason == PromptOmissionReason.ContextBudget })
        assertEquals(
            baseline.inspection.omittedItems + input.omittedItems,
            result.inspection.omittedItems
        )
        assertEquals(PromptOmissionReason.MemoryUnavailable, input.omittedItems.single().reason)
        assertEquals(PromptSource(PromptSourceKind.Memory), input.omittedItems.single().source)
    }

    /** 内部 250ms 超时可见，正常空结果不产生失败诊断。 */
    @Test
    fun recallTimeoutIsVisibleWhileEmptyResultsRemainSilent() = runBlocking {
        val input = MemoryRetriever.prepareWithFallback(memory(emptyList())) { awaitCancellation() }
        val result =
            build(LLMProviderProtocol.OpenAIResponses, PromptPostProcessingMode.None, input)
        val baseline =
            build(LLMProviderProtocol.OpenAIResponses, PromptPostProcessingMode.None, null)
        assertEquals(baseline.request, result.request)
        assertEquals(
            PromptOmissionReason.MemoryUnavailable,
            result.inspection.omittedItems.single().reason
        )
        assertNull(MemoryRetriever.prepareWithFallback(memory(emptyList())) { emptyList() })
    }

    /** 调用方主动取消及外层超时不得被内部召回降级吞掉。 */
    @Test
    fun recallPropagatesExternalCancellationAndParentTimeout() = runBlocking {
        val cancellation = CancellationException("fixture cancellation")
        val propagated = runCatching {
            MemoryRetriever.prepareWithFallback(memory(emptyList())) { throw cancellation }
        }.exceptionOrNull()
        assertTrue(propagated is CancellationException)
        assertEquals(cancellation.message, propagated?.message)
        assertTrue(runCatching {
            withTimeout(25) {
                MemoryRetriever.prepareWithFallback(memory(emptyList())) { awaitCancellation() }
            }
        }.exceptionOrNull() is TimeoutCancellationException)
    }

    /** 五种协议和全部后处理模式均保留基线草稿，只增加有身份标记的资料块。 */
    @Test
    fun everyProtocolAndProcessingModeKeepsBaselineAndTracksMemory() {
        // 遍历全部协议与后处理模式验证记忆注入不淘汰基线草稿且携带完整检查器来源。
        LLMProviderProtocol.entries.forEach { protocol ->
            PromptPostProcessingMode.entries.forEach { mode ->
                val baseline = build(protocol, mode, null)
                val result = build(protocol, mode, memory(listOf("UNIQUE_MEMORY")))
                val original = baseline.retainedDrafts.map { it.content }
                assertEquals(
                    original,
                    result.retainedDrafts
                        .filterNot { it.source.kind == PromptSourceKind.Memory }
                        .map { it.content },
                )
                assertTrue(result.request.messages.any { it.content.contains("UNIQUE_MEMORY") })
                // 验证检查器来源标记及零记忆预算回退至原请求。
                val selected = result.inspection.items
                    .flatMap { it.sources }
                    .filter { it.kind == PromptSourceKind.Memory }
                assertTrue(selected.isNotEmpty())
                assertTrue(
                    selected.all {
                        it.memoryTable == "Character" && it.referenceId == 1L && it.revision == 3L
                    },
                )
                assertTrue(result.inspection.finalTokenCount <= result.inspection.promptBudget)
                assertEquals(
                    baseline.request,
                    build(protocol, mode, memory(listOf("too large")).copy(maxTokens = 0)).request,
                )
            }
        }
    }

    /** 裁剪基线历史后不能被新增资料挤掉，超长候选跳过后仍能装入后续短项。 */
    @Test
    fun oversizedMemoriesCannotEvictBaselineOrBlockLaterSmallItems() {
        // 先获取受限上下文下的基线，再注入超长与短记忆组合验证跳过与续装。
        val protocol = LLMProviderProtocol.OpenAIResponses
        val baseline = build(protocol, PromptPostProcessingMode.None, null, 240)
        val result = build(
            protocol = protocol,
            mode = PromptPostProcessingMode.None,
            memory = memory(listOf("x".repeat(2000), "tiny")),
            limit = 240,
        )
        assertEquals(
            baseline.retainedDrafts.map { it.content },
            result.retainedDrafts
                .filterNot { it.source.kind == PromptSourceKind.Memory }
                .map { it.content },
        )
        assertFalse(result.request.messages.any { it.content.contains("x".repeat(2000)) })
        assertTrue(
            result.inspection.omittedItems.any {
                it.source.kind == PromptSourceKind.Memory && it.source.referenceId == 1L
            },
        )
        assertTrue(result.request.messages.any { it.content.contains("tiny") })
        // 剩余额度刚好不足以容纳记忆块时完整保留基线请求。
        val exact = build(
            protocol = protocol,
            mode = PromptPostProcessingMode.None,
            memory = memory(listOf("tiny")),
            limit = baseline.inspection.finalTokenCount + 10,
        )
        assertEquals(baseline.request, exact.request)
    }

    /** 插入记忆之后保留图文位置和图片身份，协议重排不能丢失附件。 */
    @Test
    fun imagesSurviveMemoryInsertionAcrossProtocolsAndModes() {
        // 在五种协议与全部后处理模式下验证带图片附件的历史消息不受记忆插入破坏。
        val images = listOf(LLMImageReference("fixture-image", "image/png", 640, 480, 1000))
        LLMProviderProtocol.entries.forEach { protocol ->
            PromptPostProcessingMode.entries.forEach { mode ->
                val baseline = build(protocol, mode, null, limit = 8192, images = images)
                val result = build(
                    protocol = protocol,
                    mode = mode,
                    memory = memory(listOf("memory")),
                    limit = 8192,
                    images = images,
                )
                assertEquals(
                    baseline.request.messages.flatMap { it.images },
                    result.request.messages.flatMap { it.images },
                )
                assertEquals(images, result.request.messages.flatMap { it.images })
                assertEquals(
                    baseline.retainedDrafts.map { it.content },
                    result.retainedDrafts
                        .filterNot { it.source.kind == PromptSourceKind.Memory }
                        .map { it.content },
                )
            }
        }
    }

    /** 空模板和损坏模板均回退原请求；事实宏样式及标签保持字面含义。 */
    @Test
    fun emptyOrInvalidWrapperFallsBackAndFactsAreLiteral() {
        // 验证空外层模板与缺槽外层模板均安全回退到基线请求。
        val baseline =
            build(LLMProviderProtocol.OpenAIResponses, PromptPostProcessingMode.None, null)
        listOf("", "missing slot").forEach { wrapper ->
            val input = memory(listOf("secret")).copy(
                templates = mTemplates.copy(
                    values = mTemplates.values + (PromptType.CharacterMemoryContext to wrapper),
                ),
            )
            assertEquals(
                baseline.request,
                build(
                    LLMProviderProtocol.OpenAIResponses,
                    PromptPostProcessingMode.None,
                    input,
                ).request,
            )
        }
        // 验证事实正文中的闭合标签与随机宏字面保留。
        val rendered =
            MemoryPromptRenderer.renderItem("</fact>{{random::LEAK}}", mTemplates, MacroScope())
        assertTrue(rendered.contains("&lt;/fact&gt;{{random::LEAK}}"))
    }

    private fun entry(
        id: Long,
        body: String,
        importance: Int,
        pinned: Boolean = false,
        subjectKey: String = "self"
    ) =
        MemoryEntry(
            ref = MemoryRef(MemoryTable.Character, id),
            target = MemoryTarget.Character(1),
            privateCharacterId = null,
            overrideCharacterMemoryId = null,
            fields = MemoryFields(
                uuid = "fixture-$id",
                subjectKey = subjectKey,
                factKey = "$id",
                slotKey = "0/0",
                content = MemoryContent(body, "fact", "Active", importance, pinned),
                createdAt = 1,
                updatedAt = 1,
            ),
        )

    private fun memory(bodies: List<String>) = MemoryPromptInput(
        items = bodies.mapIndexed { index, text ->
            MemoryPromptItem(
                ref = MemoryRef(MemoryTable.Character, index + 1L),
                revision = 3,
                text = text,
            )
        },
        templates = mTemplates,
        scope = MacroScope(),
        maxItems = 6,
        maxTokens = 512,
    )

    private fun build(
        protocol: LLMProviderProtocol,
        mode: PromptPostProcessingMode,
        memory: MemoryPromptInput?,
        limit: Int = 4096,
        images: List<LLMImageReference> = emptyList(),
    ) = mFinalizer.finalize(
        drafts = listOf(
            PromptMessageDraft(
                role = LLMMessageRole.System,
                content = "FIXED",
                source = PromptSource(PromptSourceKind.MainPrompt),
                retentionPriority = 100,
                canDrop = false,
                beforeMemorySlot = true,
            ),
            PromptMessageDraft(
                role = LLMMessageRole.User,
                content = "OLD_HISTORY_" + "o".repeat(100),
                source = PromptSource(PromptSourceKind.ChatHistory, referenceId = 8),
                retentionPriority = 1,
                canDrop = true,
                images = images,
            ),
            PromptMessageDraft(
                role = LLMMessageRole.Assistant,
                content = "CURRENT_REPLY",
                source = PromptSource(PromptSourceKind.ChatHistory, referenceId = 9),
                retentionPriority = 10,
                canDrop = true,
            ),
            PromptMessageDraft(
                role = LLMMessageRole.User,
                content = "TASK",
                source = PromptSource(PromptSourceKind.CharacterReplyNudge),
                retentionPriority = 100,
                canDrop = false,
            ),
        ),
        provider = LLMProvider(
            id = 1,
            name = "Fixture",
            providerType = LLMProviderType.Custom,
            protocol = protocol,
            baseUrl = "https://example.invalid",
            model = "fixture",
            createTime = 1,
            updateTime = 1,
        ),
        model = "fixture",
        options = LLMGenerationOptions(maxTokens = 10),
        includeReasoningInContent = false,
        maxContextTokens = limit,
        maxResponseTokens = 10,
        postProcessingMode = mode,
        strictPromptPlaceholder = "START",
        normalMemory = memory,
        tokenizer = mTokenizer,
    )
}
