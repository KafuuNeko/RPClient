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
        character: Character,
        session: ChatSession,
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): LLMGenerationRequest {
        val selectedMessages = fitSummaryMessages(messages, provider)
        val history = mHistoryBuilder.build(selectedMessages, userName, character.name)
        val context = PromptBuildContext(
            userName = userName,
            character = character,
            session = session,
            messages = selectedMessages,
            currentUserMessage = null,
            candidateLorebookEntries = emptyList(),
            provider = provider,
            maxContextTokens = provider?.contextTokens ?: 8192,
            maxResponseTokens = AppModel.summaryResponseTokens
        )
        val prompt = mMacroResolver.resolve(
            template = AppModel.summarizePrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_SUMMARY_PROMPT,
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

    fun selectSummarizedMessageIds(
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): List<Long> {
        // 与 build() 使用相同裁剪逻辑，确保只标记实际送入总结请求的消息。
        return fitSummaryMessages(messages, provider).map { it.id }
    }

    private fun fitSummaryMessages(
        messages: List<ChatMessage>,
        provider: LLMProvider?
    ): List<ChatMessage> {
        // 增量总结按时间顺序选择尚未总结的旧消息，超出预算时保留已纳入的前段。
        val maxMessages = AppModel.summaryMaxMessagesPerRequest
        val limitedByCount = if (maxMessages > 0) messages.take(maxMessages) else messages
        val tokenBudget = ((provider?.contextTokens ?: 8192) - AppModel.summaryResponseTokens)
            .coerceAtLeast(1024)
        val selected = mutableListOf<ChatMessage>()
        var usedTokens = 0
        limitedByCount.forEach { message ->
            val nextTokens = (message.content.length / 3).coerceAtLeast(1)
            if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) return@forEach
            selected += message
            usedTokens += nextTokens
        }
        return selected
    }

    private companion object {
        const val DEFAULT_SUMMARY_PROMPT = """
Please summarize the following chat history into concise story memory.
Rules:
- Do not continue roleplay.
- Do not generate a new reply for {{char}} or {{user}}.
- Only summarize what already happened in the chat.
- Preserve important facts, relationship changes, promises, injuries, locations, goals, unresolved conflicts and current scene state.
- Keep it within {{words}} words.

Existing summary:
{{summary}}

Chat history to summarize:
{{history}}
"""
    }
}
