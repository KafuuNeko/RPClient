package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.utils.stripThinkBlocks

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
     * 预算按最终单条 user 消息计算，已包含模板、已有总结、宏展开、角色标签和消息格式开销。
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
        val maxContextTokens = provider?.contextTokens ?: DEFAULT_CONTEXT_TOKENS
        val responseTokens = AppModel.summaryResponseTokens
        val promptBudget = maxContextTokens - responseTokens
        require(promptBudget > 0) {
            "Summary response token reserve must be smaller than the context token limit."
        }
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        val limited = messages.limitSummaryCount(AppModel.summaryMaxMessagesPerRequest)
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitizedById = limited.associate { message ->
            message.id to message.copy(content = message.content.summarySafeContent())
        }
        val selected = selectSummaryPrefix(
            items = limited,
            promptBudget = promptBudget
        ) { prefix ->
            val sanitized = prefix.map { sanitizedById.getValue(it.id) }
            tokenizer.countMessages(
                listOf(
                    LLMMessage(
                        role = LLMMessageRole.User,
                        content = renderPrompt(
                            userName = userName,
                            userDescription = userDescription,
                            character = character,
                            session = session,
                            existingSummary = safeExistingSummary,
                            messages = sanitized,
                            provider = provider
                        )
                    )
                )
            )
        }
        if (limited.isNotEmpty() && selected.isEmpty()) {
            val required = tokenizer.countMessages(
                listOf(
                    LLMMessage(
                        LLMMessageRole.User,
                        renderPrompt(
                            userName,
                            userDescription,
                            character,
                            session,
                            safeExistingSummary,
                            listOf(sanitizedById.getValue(limited.first().id)),
                            provider
                        )
                    )
                )
            )
            throw PromptBudgetExceededException(required, promptBudget)
        }
        val sanitizedSelected = selected.map { sanitizedById.getValue(it.id) }
        val request = LLMGenerationRequest(
            messages = listOf(
                LLMMessage(
                    LLMMessageRole.User,
                    renderPrompt(
                        userName,
                        userDescription,
                        character,
                        session,
                        safeExistingSummary,
                        sanitizedSelected,
                        provider
                    )
                )
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

    private fun renderPrompt(
        userName: String,
        userDescription: String,
        character: Character,
        session: ChatSession,
        existingSummary: String,
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): String {
        val history = mHistoryBuilder.build(messages, userName, character.name)
        val context = PromptBuildContext(
            userName = userName,
            userDescription = userDescription,
            character = character,
            session = session,
            summary = existingSummary,
            messages = messages,
            currentUserMessage = null,
            candidateLorebookEntries = emptyList(),
            provider = provider,
            maxContextTokens = provider?.contextTokens ?: DEFAULT_CONTEXT_TOKENS,
            maxResponseTokens = AppModel.summaryResponseTokens
        )
        return mMacroResolver.resolve(
            template = AppModel.summarizePrompt,
            context = context,
            history = history
        ).replace(
            "{{words}}",
            AppModel.summaryWordsLimit.toString(),
            ignoreCase = true
        )
    }

    private companion object {
        const val DEFAULT_CONTEXT_TOKENS = 8192
    }
}

/** 按数量限制保留连续消息前缀。 */
private fun <T> List<T>.limitSummaryCount(maxMessages: Int): List<T> {
    return if (maxMessages > 0) take(maxMessages) else this
}

/**
 * 用完整前缀请求的 Token 数选择连续消息，第一条超预算时也不会被强行纳入。
 */
internal fun <T> selectSummaryPrefix(
    items: List<T>,
    promptBudget: Int,
    countPrefixTokens: (List<T>) -> Int
): List<T> {
    val selected = mutableListOf<T>()
    for (item in items) {
        val candidate = selected + item
        if (countPrefixTokens(candidate) > promptBudget) break
        selected += item
    }
    return selected
}

/** 总结路径始终排除 reasoning，不受普通聊天上下文展示设置影响。 */
internal fun String.summarySafeContent(): String {
    return stripThinkBlocks()
}
