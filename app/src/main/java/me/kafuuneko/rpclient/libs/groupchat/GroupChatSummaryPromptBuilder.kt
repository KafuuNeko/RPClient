package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.selectSummaryPrefix
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 群聊总结请求和其实际覆盖的连续消息。 */
data class GroupChatSummaryBuildResult(
    val request: LLMGenerationRequest,
    val selectedMessages: List<GroupChatMessage>
)

/** 构建群聊增量摘要请求，并按最终请求 Token 数确定覆盖边界。 */
class GroupChatSummaryPromptBuilder(
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer()
) {
    /** 使用当前摘要和未覆盖消息构建群聊摘要请求。 */
    fun buildWithSelection(
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>,
        provider: LLMProvider
    ): GroupChatSummaryBuildResult {
        val responseTokens = AppModel.summaryResponseTokens
        val promptBudget = provider.contextTokens - responseTokens
        require(promptBudget > 0) {
            "Summary response token reserve must be smaller than the context token limit."
        }
        val maxMessages = AppModel.summaryMaxMessagesPerRequest
        val limited = if (maxMessages > 0) messages.take(maxMessages) else messages
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitizedById = limited.associate { message ->
            message.id to message.copy(content = message.content.summarySafeContent())
        }
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        val selected = selectSummaryPrefix(limited, promptBudget) { prefix ->
            val prompt = renderPrompt(
                session,
                memberNames,
                safeExistingSummary,
                prefix.map { sanitizedById.getValue(it.id) }
            )
            tokenizer.countMessages(listOf(LLMMessage(LLMMessageRole.User, prompt)))
        }
        if (limited.isNotEmpty() && selected.isEmpty()) {
            val prompt = renderPrompt(
                session,
                memberNames,
                safeExistingSummary,
                listOf(sanitizedById.getValue(limited.first().id))
            )
            throw PromptBudgetExceededException(
                tokenizer.countMessages(listOf(LLMMessage(LLMMessageRole.User, prompt))),
                promptBudget
            )
        }
        val prompt = renderPrompt(
            session,
            memberNames,
            safeExistingSummary,
            selected.map { sanitizedById.getValue(it.id) }
        )
        return GroupChatSummaryBuildResult(
            request = LLMGenerationRequest(
                messages = listOf(LLMMessage(LLMMessageRole.User, prompt)),
                model = provider.model,
                options = LLMGenerationOptions(
                    temperature = provider.temperature,
                    maxTokens = responseTokens,
                    topP = provider.topP
                ),
                isPromptFinalized = true
            ),
            selectedMessages = selected
        )
    }

    private fun renderPrompt(
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>
    ): String {
        val history = messages.joinToString("\n") {
            "${it.speakerNameSnapshot}: ${it.content}"
        }
        return AppModel.groupSummarizePrompt
            .replace("{{user}}", session.userName, ignoreCase = true)
            .replace("{{group}}", memberNames.joinToString(", "), ignoreCase = true)
            .replace("{{summary}}", existingSummary, ignoreCase = true)
            .replace("{{history}}", history, ignoreCase = true)
            .replace("{{words}}", AppModel.summaryWordsLimit.toString(), ignoreCase = true)
    }
}
