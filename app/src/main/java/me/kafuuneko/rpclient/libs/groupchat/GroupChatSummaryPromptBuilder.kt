package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

class GroupChatSummaryPromptBuilder {
    /** 使用当前摘要和未覆盖消息构建群聊摘要请求。 */
    fun build(
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>,
        provider: LLMProvider
    ): LLMGenerationRequest {
        val selected = selectMessagesToSummarize(messages, provider)
        val history = selected.joinToString("\n") {
            "${it.speakerNameSnapshot}: ${it.content}"
        }
        val prompt = AppModel.groupSummarizePrompt
            .replace("{{user}}", session.userName, ignoreCase = true)
            .replace("{{group}}", memberNames.joinToString(", "), ignoreCase = true)
            .replace("{{summary}}", existingSummary, ignoreCase = true)
            .replace("{{history}}", history, ignoreCase = true)
            .replace("{{words}}", AppModel.summaryWordsLimit.toString(), ignoreCase = true)
        return LLMGenerationRequest(
            messages = listOf(LLMMessage(LLMMessageRole.User, prompt)),
            model = provider.model,
            options = LLMGenerationOptions(
                temperature = provider.temperature,
                maxTokens = AppModel.summaryResponseTokens,
                topP = provider.topP
            )
        )
    }

    /** 按消息数量及模型上下文预算选择本轮可摘要的消息。 */
    fun selectMessagesToSummarize(
        messages: List<GroupChatMessage>,
        provider: LLMProvider
    ): List<GroupChatMessage> {
        val maxMessages = AppModel.summaryMaxMessagesPerRequest
        val limited = if (maxMessages > 0) messages.take(maxMessages) else messages
        val tokenBudget = (
            provider.contextTokens - AppModel.summaryResponseTokens
        ).coerceAtLeast(1024)
        val selected = mutableListOf<GroupChatMessage>()
        var usedTokens = 0
        for (message in limited) {
            val nextTokens = (message.content.length / 3).coerceAtLeast(1)
            if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) break
            selected += message
            usedTokens += nextTokens
        }
        return selected
    }
}
