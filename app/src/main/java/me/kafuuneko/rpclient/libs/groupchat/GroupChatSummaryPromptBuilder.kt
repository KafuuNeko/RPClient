package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.buildRawSummaryMessages
import me.kafuuneko.rpclient.libs.prompt.selectSummaryPrefix
import me.kafuuneko.rpclient.libs.prompt.summaryCandidates
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
        val limited = messages.summaryCandidates(AppModel.summaryMaxMessagesPerRequest)
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitizedById = limited.associate { message ->
            message.id to message.copy(content = message.content.summarySafeContent())
        }
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        val selected = selectSummaryPrefix(limited, promptBudget) { prefix ->
            val requestMessages = renderRequestMessages(
                session,
                memberNames,
                safeExistingSummary,
                prefix.map { sanitizedById.getValue(it.id) }
            )
            tokenizer.countMessages(requestMessages)
        }
        if (limited.isNotEmpty() && selected.isEmpty()) {
            val requestMessages = renderRequestMessages(
                session,
                memberNames,
                safeExistingSummary,
                listOf(sanitizedById.getValue(limited.first().id))
            )
            throw PromptBudgetExceededException(
                tokenizer.countMessages(requestMessages),
                promptBudget
            )
        }
        val requestMessages = renderRequestMessages(
            session,
            memberNames,
            safeExistingSummary,
            selected.map { sanitizedById.getValue(it.id) }
        )
        return GroupChatSummaryBuildResult(
            request = LLMGenerationRequest(
                messages = requestMessages,
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

    /** 渲染群聊摘要使用的 system 指令和 user 原始素材。 */
    private fun renderRequestMessages(
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>
    ): List<LLMMessage> {
        val history = messages.joinToString("\n") {
            "${it.speakerNameSnapshot}: ${it.content}"
        }
        val instruction = AppModel.groupSummarizePrompt
            .replace("{{user}}", session.userName, ignoreCase = true)
            .replace("{{group}}", memberNames.joinToString(", "), ignoreCase = true)
            .replace("{{summary}}", "", ignoreCase = true)
            .replace("{{history}}", "", ignoreCase = true)
            .replace("{{words}}", AppModel.summaryWordsLimit.toString(), ignoreCase = true)
        return buildRawSummaryMessages(instruction, existingSummary, history)
    }
}
