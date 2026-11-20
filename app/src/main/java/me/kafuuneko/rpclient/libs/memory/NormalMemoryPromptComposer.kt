package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.request.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.request.PromptFinalizationResult
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizer
import java.util.UUID

/**
 * 仅在原请求的剩余额度内加入普通记忆。
 * - 复用 Finalizer 保留的原始草稿及插槽，不从合并后的 Inspector 反推历史。
 * - 原草稿在重计量时全部保护，只允许跳过整条记忆。
 */
object NormalMemoryPromptComposer {
    /**
     * 尝试按排序逐条装入；任一候选过大不会淘汰原有内容或阻止后续短条目。
     *
     * @param baseline 已经成功的原请求
     * @param input 已展开且通过范围校验的候选
     * @param tokenizer 与原请求相同的计量器
     * @param finalize 仅重新后处理和计量草稿，不重新运行宏、Regex 或世界书
     * @return 基线或只增加允许参考内容的新请求
     */
    fun compose(
        baseline: PromptFinalizationResult,
        input: MemoryPromptInput?,
        tokenizer: PromptTokenizer,
        finalize: (List<PromptMessageDraft>) -> PromptFinalizationResult,
    ): PromptFinalizationResult {
        if (input == null) return baseline
        // 无候选时也保留读取失败诊断，实际请求继续使用已成功构建的基线。
        val base = if (input.omittedItems.isEmpty()) baseline else baseline.copy(
            inspection = baseline.inspection.copy(
                omittedItems = baseline.inspection.omittedItems + input.omittedItems,
            ),
        )
        if (input.items.isEmpty()) return base
        // 包装模板也只展开一次，含随机宏的模板在预算重试时不会改变已冻结内容。
        val slot = "memory-slot-${UUID.randomUUID()}"
        val frame = try {
            MemoryPromptRenderer.renderContext(listOf(slot), input.templates, input.scope)
        } catch (error: IllegalArgumentException) {
            return base
        }
        if (frame.isBlank()) return base
        // 锁定基线全部已保留草稿并定位记忆插槽位置。
        val kept = base.retainedDrafts.map { it.copy(canDrop = false) }
        val insertion = kept.indexOfLast { it.beforeMemorySlot } + 1
        val selected = mutableListOf<MemoryPromptItem>()
        val omitted = mutableListOf<PromptOmittedItem>()
        var result = base
        // 基线的所有内容已经确定；候选失败只影响记忆集合。
        for (item in input.items) {
            val next = selected + item
            val text = frame.replace(slot, next.joinToString("\n") { it.text })
            if (text.isBlank()) return base
            val memoryTokens =
                tokenizer.countMessages(listOf(LLMMessage(LLMMessageRole.User, text)))
            val sources = next.map { it.source() }
            val draft = PromptMessageDraft(
                role = LLMMessageRole.User,
                content = text,
                source = sources.first(),
                retentionPriority = Int.MAX_VALUE,
                canDrop = false,
                sources = sources,
            )
            // 在条数与记忆独立 Token 限额内尝试重新后处理并核对最终增量。
            val candidate = if (next.size <= input.maxItems && memoryTokens <= input.maxTokens) {
                try {
                    finalize(kept.take(insertion) + draft + kept.drop(insertion))
                } catch (error: PromptBudgetExceededException) {
                    null
                } catch (error: ImageRequestException) {
                    null
                }
            } else {
                null
            }
            // 超限时记录被跳过的记忆项，满足预算时推进已选集合与最终请求结果。
            val deltaTokens =
                candidate?.let { it.inspection.finalTokenCount.toLong() - base.inspection.finalTokenCount }
            if (candidate == null || deltaTokens == null || deltaTokens > input.maxTokens) {
                omitted += PromptOmittedItem(
                    source = item.source(),
                    tokenCount = tokenizer.countText(item.text),
                    reason = PromptOmissionReason.MemoryBudget,
                )
            } else {
                selected += item
                result = candidate
            }
        }
        // 合并基线与记忆裁剪项到最终检查报告。
        return result.copy(
            inspection = result.inspection.copy(
                omittedItems = base.inspection.omittedItems + omitted,
            ),
        )
    }

    private fun MemoryPromptItem.source() = PromptSource(
        kind = PromptSourceKind.Memory,
        referenceId = ref.id,
        memoryTable = ref.table.name,
        revision = revision,
    )
}
