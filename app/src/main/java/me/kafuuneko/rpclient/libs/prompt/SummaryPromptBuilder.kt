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

class SummaryPromptBuilder(
    private val mMacroResolver: PromptMacroResolver,
    private val mHistoryBuilder: FormattedHistoryBuilder
) {
    /**
     * 构建独立的总结请求。
     *
     * 总结不走正常角色扮演 prompt 栈，只提交已有摘要与待总结聊天历史，避免世界书、
     * 角色卡或作者注释把未发生的设定污染进剧情记忆。
     */
    fun build(
        userName: String,
        userDescription: String,
        character: Character,
        session: ChatSession,
        existingSummary: String,
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): LLMGenerationRequest {
        val selectedMessages = fitSummaryMessages(messages, provider)
        val history = mHistoryBuilder.build(selectedMessages, userName, character.name)
        val context = PromptBuildContext(
            userName = userName,
            userDescription = userDescription,
            character = character,
            session = session,
            summary = existingSummary,
            messages = selectedMessages,
            currentUserMessage = null,
            candidateLorebookEntries = emptyList(),
            provider = provider,
            maxContextTokens = provider?.contextTokens ?: 8192,
            maxResponseTokens = AppModel.summaryResponseTokens
        )
        val prompt = mMacroResolver.resolve(
            template = AppModel.summarizePrompt,
            context = context,
            history = history
        ).replace("{{words}}", AppModel.summaryWordsLimit.toString(), ignoreCase = true)

        return LLMGenerationRequest(
            messages = listOf(LLMMessage(LLMMessageRole.User, prompt)),
            model = provider?.model,
            options = LLMGenerationOptions(
                temperature = provider?.temperature,
                maxTokens = AppModel.summaryResponseTokens,
                topP = provider?.topP
            )
        )
    }

    /**
     * 按单次总结数量和上下文预算选择本次实际提交的连续消息前缀。
     *
     * @param messages 最新总结之后尚未覆盖的普通消息。
     * @param provider 当前模型供应商，用于确定上下文容量。
     * @return 本次总结会覆盖的消息；顺序与输入保持一致。
     */
    fun selectMessagesToSummarize(
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): List<ChatMessage> {
        return fitSummaryMessages(messages, provider)
    }

    private fun fitSummaryMessages(
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): List<ChatMessage> {
        // 增量总结按时间顺序选择尚未总结的旧消息，超出预算时保留已纳入的前段。
        val maxMessages = AppModel.summaryMaxMessagesPerRequest
        val tokenBudget = ((provider?.contextTokens ?: 8192) - AppModel.summaryResponseTokens)
            .coerceAtLeast(1024)
        return selectSummaryMessagePrefix(messages, maxMessages, tokenBudget)
    }
}

/**
 * 选择不跨越预算缺口的连续消息前缀，确保总结边界不会覆盖未提交的中间消息。
 */
internal fun selectSummaryMessagePrefix(
    messages: List<ChatMessage>,
    maxMessages: Int,
    tokenBudget: Int
): List<ChatMessage> {
    val limitedByCount = if (maxMessages > 0) messages.take(maxMessages) else messages
    return buildList {
        val selected = mutableListOf<ChatMessage>()
        var usedTokens = 0
        for (message in limitedByCount) {
            val nextTokens = (message.content.length / 3).coerceAtLeast(1)
            if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) break
            selected += message
            usedTokens += nextTokens
        }
        addAll(selected)
    }
}
