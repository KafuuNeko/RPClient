package me.kafuuneko.rpclient.libs.prompt.summary

import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.prompt.FormattedHistoryBuilder
import me.kafuuneko.rpclient.libs.prompt.macro.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.macro.promptMacroExpander
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.prompt.model.requireAvailable
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.request.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.request.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizer
import me.kafuuneko.rpclient.libs.prompt.request.estimateImageRequestBytes
import me.kafuuneko.rpclient.libs.prompt.request.toTokenInt
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.utils.stripThinkBlocks

/** 摘要消息首次读取窗口，后续仅在完整窗口仍符合预算时按倍数扩展。 */
internal const val INITIAL_SUMMARY_CANDIDATE_WINDOW_SIZE = 128

/** 无模型配置时沿用的摘要上下文 Token 上限。 */
internal const val DEFAULT_SUMMARY_CONTEXT_TOKENS = 8192

/**
 * 准备 Classic 的末尾摘要指令，历史与旧摘要继续由普通上下文的预算器管理。
 * @param template 当前预设中的摘要模板。
 * @param wordsLimit 摘要目标字数。
 * @return 保留人物宏、移除历史内联宏的任务模板。
 */
internal fun classicSummaryInstruction(template: String, wordsLimit: Int): String {
    val scope = MacroScope(
        mapOf(
            "words" to MacroValue.Literal(wordsLimit.toString()),
            "summary" to MacroValue.Literal(""),
            "history" to MacroValue.Literal(""),
        )
    )
    val instruction = promptMacroExpander.expand(template, scope).text
    require(instruction.isNotBlank()) { "Summary instruction cannot be blank" }
    return instruction
}

/** 单聊总结请求与其实际覆盖消息使用同一次预算选择结果。 */
data class SummaryPromptBuildResult(
    val request: LLMGenerationRequest,
    val selectedMessages: List<ChatMessage>
)

/**
 * 构建不含角色扮演设定的增量聊天摘要请求。
 * - 分离摘要指令与原始素材，人物宏按同一上下文展开。
 * - 在文本与图片预算内选择连续历史前缀，返回实际覆盖的原始消息。
 */
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
     * - 按已确定的末条保留规则与单批条数上限选择候选消息。
     * - 对历史消息与已有摘要进行 Think 思考块剥离与清洗；
     * - 从最早消息开始贪婪选择不超过 Prompt 预算的最大消息前缀；
     * - 组装并渲染包含总结指令模板与格式化历史的请求对象。
     *
     * @param userName 本次冻结的有效用户名称。
     * @param userDescription 本次冻结的人设描述。
     * @param character 用于人物宏展开的角色快照。
     * @param session 用于会话宏展开的会话快照。
     * @param existingSummary 本轮继续累积的已有记忆。
     * @param messages 数据层按时间排序的候选历史窗口。
     * @param provider 摘要所用模型配置，缺失时采用默认预算。
     * @param messageImages 候选消息关联的已准备图片。
     * @param unavailableImages 候选消息中暂不可读取的图片，用于选中后的明确失败提示。
     * @param templates 本轮冻结的摘要指令及素材格式。
     * @param includeLastMessage 候选窗口已在数据层选定时，保留窗口中的末条消息。
     * @return 与最终请求使用同一次预算选择结果的消息集合。
     */
    fun buildWithSelection(
        userName: String,
        userDescription: String,
        character: Character,
        session: ChatSession,
        existingSummary: String,
        messages: List<ChatMessage>,
        provider: LLMProvider?,
        messageImages: Map<Long, List<LLMImageReference>> = emptyMap(),
        unavailableImages: Map<Long, List<UnavailablePromptImage>> = emptyMap(),
        templates: PromptTemplateSnapshot = PromptTemplateSnapshot.defaults(),
        includeLastMessage: Boolean = false,
    ): SummaryPromptBuildResult {
        // 计算扣除回复预留后的输入 Prompt 预算
        val maxContextTokens = provider?.contextTokens ?: DEFAULT_SUMMARY_CONTEXT_TOKENS
        val responseTokens = AppModel.summaryResponseTokens
        val promptBudget = summaryPromptBudget(maxContextTokens, responseTokens)
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        // 单聊仓库已排除自动摘要需保留的原文，窗口末条在这里也可参与预算选择。
        val limited = messages.summaryCandidates(
            maxMessages = AppModel.summaryMaxMessagesPerRequest,
            includeLastMessage = includeLastMessage,
        )
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitized = limited.map { message ->
            message.copy(content = message.content.summarySafeContent())
        }
        // 使用有界 Token 统计定位符合预算的最长连续前缀
        val selected = selectSummaryPrefix(
            items = limited,
            promptBudget = promptBudget,
        ) { prefix ->
            countSummaryTokens(
                tokenizer = tokenizer,
                messages = renderRequestMessages(
                    templates = templates,
                    userName = userName,
                    userDescription = userDescription,
                    character = character,
                    session = session,
                    existingSummary = safeExistingSummary,
                    messages = sanitized.subList(fromIndex = 0, toIndex = prefix.size),
                    provider = provider,
                    messageImages = messageImages,
                ),
                budget = promptBudget,
                unavailableCount = prefix.sumOf { message -> unavailableImages[message.id].orEmpty().size },
            )
        }
        // 若存在候选消息但连单条都超出预算则抛出异常
        if (limited.isNotEmpty() && selected.isEmpty()) {
            val required = tokenizer.countMessages(
                messages = renderRequestMessages(
                    templates = templates,
                    userName = userName,
                    userDescription = userDescription,
                    character = character,
                    session = session,
                    existingSummary = safeExistingSummary,
                    messages = listOf(sanitized.first()),
                    provider = provider,
                    messageImages = messageImages,
                ),
            )
            throw PromptBudgetExceededException(
                requiredTokens = (required.toLong() + tokenizer.countUnavailableImages(
                    unavailableImages[limited.first().id].orEmpty().size
                )).toTokenInt(),
                promptBudget = promptBudget,
            )
        }
        val sanitizedSelected = sanitized.take(selected.size)
        // 组装最终的总结生成请求
        val request = LLMGenerationRequest(
            messages = renderRequestMessages(
                templates = templates,
                userName = userName,
                userDescription = userDescription,
                character = character,
                session = session,
                existingSummary = safeExistingSummary,
                messages = sanitizedSelected,
                provider = provider,
                messageImages = messageImages,
            ),
            model = provider?.model,
            options = LLMGenerationOptions(
                temperature = provider?.temperature,
                maxTokens = responseTokens,
                topP = provider?.topP,
            ),
            isPromptFinalized = true,
        )
        selected.flatMap { unavailableImages[it.id].orEmpty() }.requireAvailable()
        return SummaryPromptBuildResult(request, selected)
    }

    /** 渲染摘要指令和原始聊天素材两条消息。 */
    private fun renderRequestMessages(
        templates: PromptTemplateSnapshot,
        userName: String,
        userDescription: String,
        character: Character,
        session: ChatSession,
        existingSummary: String,
        messages: List<ChatMessage>,
        provider: LLMProvider?,
        messageImages: Map<Long, List<LLMImageReference>>,
    ): List<LLMMessage> {
        // 摘要任务只展开指令宏，历史与旧摘要由独立的素材模板承载。
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
            maxResponseTokens = AppModel.summaryResponseTokens,
        )
        val instruction = mMacroResolver.resolve(
            template = templates.summarizePrompt,
            context = context,
            history = "",
            bindings = mapOf("words" to MacroValue.Literal(AppModel.summaryWordsLimit.toString())),
        )
        // 保留候选历史的单条消息边界，预算探测与实际发送使用同一套格式。
        val history = mHistoryBuilder.buildSummaryHistory(
            messages = messages,
            userName = userName,
            characterName = character.name,
            messageImages = messageImages,
        )
        return buildRawSummaryMessages(
            templates = templates,
            instruction = instruction,
            existingSummary = existingSummary,
            history = history,
        )
    }

}

/**
 * 选择本轮可摘要消息。
 *
 * 按用户配置保留连续前缀，调用方可保留已确定的候选窗口末条。
 */
internal fun <T> List<T>.summaryCandidates(
    maxMessages: Int,
    includeLastMessage: Boolean = false,
): List<T> {
    val availableCount = if (includeLastMessage) size else (size - 1).coerceAtLeast(0)
    val candidateCount = if (maxMessages > 0) minOf(availableCount, maxMessages) else availableCount
    return take(candidateCount)
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
    countPrefixTokens: (List<T>) -> Int,
): List<T> {
    if (items.isEmpty()) return emptyList()
    var acceptedSize = 0
    var probeSize = 1
    // 指数扩展只探测少量前缀，避免直接格式化远超预算的大窗口
    while (true) {
        if (countPrefixTokens(
                items.subList(
                    fromIndex = 0,
                    toIndex = probeSize
                )
            ) > promptBudget
        ) break
        acceptedSize = probeSize
        if (acceptedSize == items.size) return items.toList()
        probeSize = minOf(items.size, probeSize.saturatedDouble())
    }
    // 在最后一个可接受前缀与首个超限探测点之间精确定位边界
    var rejectedSize = probeSize
    while (acceptedSize + 1 < rejectedSize) {
        val middleSize = acceptedSize + (rejectedSize - acceptedSize) / 2
        if (countPrefixTokens(items.subList(fromIndex = 0, toIndex = middleSize)) <= promptBudget) {
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
    configuredMaxMessages: Int,
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

/** 摘要前缀同时受图片数量、发送字节与视觉 Token 限制，保持连续前缀的单调成本。 */
internal fun countSummaryTokens(
    tokenizer: PromptTokenizer,
    messages: List<LLMMessage>,
    budget: Int,
    unavailableCount: Int = 0,
): Int {
    val images = messages.flatMap { it.images }
    val imageCount = images.size + unavailableCount
    val bytes = estimateImageRequestBytes(messages, unavailableCount)
    if (imageCount > MessageImagePolicy.MAX_IMAGES_PER_REQUEST
        || (imageCount > 0 && bytes > MessageImagePolicy.MAX_REQUEST_BYTES)
    ) return (budget.toLong() + 1).toTokenInt()
    val unavailableTokens = tokenizer.countUnavailableImages(unavailableCount)
    if (unavailableTokens > budget) return (budget.toLong() + 1).toTokenInt()
    return (tokenizer.countMessagesUpTo(messages, budget - unavailableTokens.toInt()).toLong() +
        unavailableTokens).toTokenInt()
}
