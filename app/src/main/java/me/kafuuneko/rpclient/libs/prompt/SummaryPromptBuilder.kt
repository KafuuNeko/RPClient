package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.utils.stripThinkBlocks

/** 摘要消息首次读取窗口，后续仅在完整窗口仍符合预算时按倍数扩展。 */
internal const val INITIAL_SUMMARY_CANDIDATE_WINDOW_SIZE = 128

/** 无模型配置时沿用的摘要上下文 Token 上限。 */
internal const val DEFAULT_SUMMARY_CONTEXT_TOKENS = 8192

/** 单聊总结请求与其实际覆盖消息使用同一次预算选择结果。 */
data class SummaryPromptBuildResult(
    val request: LLMGenerationRequest,
    val selectedMessages: List<ChatMessage>
)

/** 构建不含角色扮演设定的增量聊天摘要请求。 */
class SummaryPromptBuilder(
    private val mMacroResolver: PromptMacroResolver,
    private val mHistoryBuilder: FormattedHistoryBuilder,
    private val mRequestFinalizer: PromptRequestFinalizer
) {
    /**
     * 构建独立的总结请求。
     *
     * 核心步骤：
     * - 校验上下文 Token 上限与回复预留；
     * - 获取用于生成摘要的候选历史消息（排除最新的正在生成或编辑的消息）；
     * - 对历史消息与已有摘要进行 Think 思考块剥离与清洗；
     * - 从最早消息开始贪婪选择不超过 Prompt 预算的最大消息前缀；
     * - 组装并渲染包含总结指令模板与格式化历史的请求对象。
     */
    fun buildWithSelection(
        userName: String,
        userDescription: String,
        character: Character,
        session: ChatSession,
        existingSummary: String,
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): SummaryPromptBuildResult {
        // 计算扣除回复预留后的输入 Prompt 预算
        val maxContextTokens = provider?.contextTokens ?: DEFAULT_SUMMARY_CONTEXT_TOKENS
        val responseTokens = AppModel.summaryResponseTokens
        val promptBudget = summaryPromptBudget(maxContextTokens, responseTokens)
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        // 获取候选摘要消息（排除最后一条正在变动的消息并限制最大单次处理条数）
        val limited = messages.summaryCandidates(AppModel.summaryMaxMessagesPerRequest)
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitized = limited.map { message ->
            message.copy(content = message.content.summarySafeContent())
        }
        // 使用有界 Token 统计定位符合预算的最长连续前缀
        val selected = selectSummaryPrefix(
            items = limited,
            promptBudget = promptBudget
        ) { prefix ->
            tokenizer.countMessagesUpTo(
                renderRequestMessages(
                    userName = userName,
                    userDescription = userDescription,
                    character = character,
                    session = session,
                    existingSummary = safeExistingSummary,
                    messages = sanitized.subList(0, prefix.size),
                    provider = provider
                ),
                promptBudget
            )
        }
        // 若存在候选消息但连单条都超出预算则抛出异常
        if (limited.isNotEmpty() && selected.isEmpty()) {
            val required = tokenizer.countMessages(
                renderRequestMessages(
                    userName,
                    userDescription,
                    character,
                    session,
                    safeExistingSummary,
                    listOf(sanitized.first()),
                    provider
                )
            )
            throw PromptBudgetExceededException(required, promptBudget)
        }
        val sanitizedSelected = sanitized.take(selected.size)
        // 组装最终的总结生成请求
        val request = LLMGenerationRequest(
            messages = renderRequestMessages(
                userName,
                userDescription,
                character,
                session,
                safeExistingSummary,
                sanitizedSelected,
                provider
            ),
            model = provider?.model,
            options = LLMGenerationOptions(
                temperature = provider?.temperature,
                maxTokens = responseTokens,
                topP = provider?.topP
            ),
            isPromptFinalized = true
        )
        return SummaryPromptBuildResult(request, selected)
    }

    /** 渲染摘要指令和原始聊天素材两条消息。 */
    private fun renderRequestMessages(
        userName: String,
        userDescription: String,
        character: Character,
        session: ChatSession,
        existingSummary: String,
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): List<LLMMessage> {
        val history = mHistoryBuilder.build(messages, userName, character.name)
        val context = PromptBuildContext(
            userName = userName,
            userDescription = userDescription,
            character = character,
            session = session,
            summary = "",
            messages = messages,
            currentUserMessage = null,
            candidateLorebookEntries = emptyList(),
            provider = provider,
            maxContextTokens = provider?.contextTokens ?: DEFAULT_SUMMARY_CONTEXT_TOKENS,
            maxResponseTokens = AppModel.summaryResponseTokens
        )
        val instruction = mMacroResolver.resolve(
            template = AppModel.summarizePrompt,
            context = context,
            history = ""
        ).replace(
            "{{words}}",
            AppModel.summaryWordsLimit.toString(),
            ignoreCase = true
        ).replace(
            "{{summary}}",
            "",
            ignoreCase = true
        ).replace(
            "{{history}}",
            "",
            ignoreCase = true
        )
        return buildRawSummaryMessages(instruction, existingSummary, history)
    }

}

/**
 * 选择本轮可摘要消息。
 *
 * 最后一条消息固定排除，再按用户配置保留连续前缀。
 */
internal fun <T> List<T>.summaryCandidates(maxMessages: Int): List<T> {
    if (size <= 1) return emptyList()
    val candidateCount = if (maxMessages > 0) {
        minOf(size - 1, maxMessages)
    } else {
        size - 1
    }
    return subList(0, candidateCount).toList()
}

/** 构建 Raw 摘要路径发送给模型的 system 指令和 user 素材。 */
internal fun buildRawSummaryMessages(
    instruction: String,
    existingSummary: String,
    history: String
): List<LLMMessage> {
    val rawPrompt = buildList {
        existingSummary.takeIf { it.isNotBlank() }?.let {
            add("Existing summary:\n$it")
        }
        history.takeIf { it.isNotBlank() }?.let {
            add("Chat history:\n$it")
        }
    }.joinToString("\n\n")
    return listOf(
        LLMMessage(LLMMessageRole.System, instruction.trim()),
        LLMMessage(LLMMessageRole.User, rawPrompt)
    ).filter { it.content.isNotBlank() }
}

/**
 * 用完整前缀请求的 Token 数选择连续消息，第一条超预算时也不会被强行纳入。
 *
 * 摘要历史追加非空发言者行后 Token 数单调不减，因此先按指数窗口定位预算边界，再在
 * 最后一段执行二分查找。返回结果仍是旧实现遇到首个超预算前缀之前的连续消息。
 */
internal fun <T> selectSummaryPrefix(
    items: List<T>,
    promptBudget: Int,
    countPrefixTokens: (List<T>) -> Int
): List<T> {
    if (items.isEmpty()) return emptyList()
    var acceptedSize = 0
    var probeSize = 1
    // 指数扩展只探测少量前缀，避免直接格式化远超预算的大窗口
    while (true) {
        if (countPrefixTokens(items.subList(0, probeSize)) > promptBudget) break
        acceptedSize = probeSize
        if (acceptedSize == items.size) return items.toList()
        probeSize = minOf(items.size, probeSize.saturatedDouble())
    }
    // 在最后一个可接受前缀与首个超限探测点之间精确定位边界
    var rejectedSize = probeSize
    while (acceptedSize + 1 < rejectedSize) {
        val middleSize = acceptedSize + (rejectedSize - acceptedSize) / 2
        if (countPrefixTokens(items.subList(0, middleSize)) <= promptBudget) {
            acceptedSize = middleSize
        } else {
            rejectedSize = middleSize
        }
    }
    return items.take(acceptedSize)
}

/** 计算摘要输入预算，并统一校验回复预留不能耗尽上下文。 */
internal fun summaryPromptBudget(maxContextTokens: Int, responseTokens: Int): Int {
    val promptBudget = maxContextTokens - responseTokens
    require(promptBudget > 0) {
        "Summary response token reserve must be smaller than the context token limit."
    }
    return promptBudget
}

/**
 * 计算数据库最多需要提供的摘要候选消息数。
 *
 * 每条格式化历史都包含非空发言者前缀，至少消耗一个 Token，因此超过输入预算数量的
 * 消息不可能被选中；显式摘要条数设置仍优先形成更小上限。
 */
internal fun summaryCandidateMessageLimit(
    maxContextTokens: Int,
    responseTokens: Int,
    configuredMaxMessages: Int
): Int {
    val promptBudget = summaryPromptBudget(maxContextTokens, responseTokens)
    return if (configuredMaxMessages > 0) {
        minOf(configuredMaxMessages, promptBudget)
    } else {
        promptBudget
    }
}

/** 在不超过最终上限的前提下扩展下一轮摘要候选窗口。 */
internal fun nextSummaryCandidateWindowSize(currentSize: Int, maximumSize: Int): Int {
    require(currentSize > 0) { "currentSize must be positive" }
    require(maximumSize >= currentSize) { "maximumSize must not be smaller than currentSize" }
    return minOf(maximumSize, currentSize.saturatedDouble())
}

/** 避免窗口倍增时发生整数溢出。 */
private fun Int.saturatedDouble(): Int {
    return if (this > Int.MAX_VALUE / 2) Int.MAX_VALUE else this * 2
}

/** 总结路径始终排除 reasoning，不受普通聊天上下文展示设置影响。 */
internal fun String.summarySafeContent(): String {
    return stripThinkBlocks()
}
