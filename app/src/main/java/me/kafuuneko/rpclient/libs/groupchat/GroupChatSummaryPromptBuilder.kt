package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.RawSummaryHistoryMessage
import me.kafuuneko.rpclient.libs.prompt.buildRawSummaryMessages
import me.kafuuneko.rpclient.libs.prompt.countSummaryTokens
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.prompt.model.requireAvailable
import me.kafuuneko.rpclient.libs.prompt.selectSummaryPrefix
import me.kafuuneko.rpclient.libs.prompt.summaryCandidates
import me.kafuuneko.rpclient.libs.prompt.summaryPromptBudget
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.prompt.toTokenInt
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 群聊总结请求和其实际覆盖的连续消息。 */
data class GroupChatSummaryBuildResult(
    /** 经过业务层组装、准备提交给模型服务的请求。 */
    val request: LLMGenerationRequest,
    /** 按当前规则选入 Prompt 的历史消息。 */
    val selectedMessages: List<GroupChatMessage>
)

/** 构建群聊增量摘要请求，并按最终请求 Token 数确定覆盖边界。 */
class GroupChatSummaryPromptBuilder(
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer()
) {
    /**
     * 使用已有摘要和候选消息构建群聊摘要请求。
     *
     * @param includeLastMessage 候选窗口已在数据层选定时，保留窗口中的末条消息。
     */
    fun buildWithSelection(
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>,
        provider: LLMProvider,
        messageImages: Map<Long, List<LLMImageReference>> = emptyMap(),
        unavailableImages: Map<Long, List<UnavailablePromptImage>> = emptyMap(),
        templates: PromptTemplateSnapshot = PromptTemplateSnapshot.defaults(),
        userName: String = session.userName ?: DefaultNames.USER,
        includeLastMessage: Boolean = false,
    ): GroupChatSummaryBuildResult {
        // 摘要先按模型预算选择历史，再使用同一身份渲染请求与检查信息。
        val responseTokens = AppModel.summaryResponseTokens
        val promptBudget = summaryPromptBudget(provider.contextTokens, responseTokens)
        val limited = messages.summaryCandidates(
            maxMessages = AppModel.summaryMaxMessagesPerRequest,
            includeLastMessage = includeLastMessage,
        )
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitized = limited.map { message ->
            message.copy(content = message.content.summarySafeContent())
        }
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        val selected = selectSummaryPrefix(limited, promptBudget) { prefix ->
            val requestMessages = renderRequestMessages(
                templates = templates,
                userName = userName,
                memberNames = memberNames,
                existingSummary = safeExistingSummary,
                messages = sanitized.subList(fromIndex = 0, toIndex = prefix.size),
                messageImages = messageImages,
            )
            countSummaryTokens(
                tokenizer = tokenizer,
                messages = requestMessages,
                budget = promptBudget,
                unavailableCount = prefix.sumOf { message -> unavailableImages[message.id].orEmpty().size },
            )
        }
        if (limited.isNotEmpty() && selected.isEmpty()) {
            val requestMessages = renderRequestMessages(
                templates = templates,
                userName = userName,
                memberNames = memberNames,
                existingSummary = safeExistingSummary,
                messages = listOf(sanitized.first()),
                messageImages = messageImages,
            )
            throw PromptBudgetExceededException(
                requiredTokens = (tokenizer.countMessages(requestMessages).toLong() +
                    tokenizer.countUnavailableImages(unavailableImages[limited.first().id].orEmpty().size)).toTokenInt(),
                promptBudget = promptBudget,
            )
        }
        val requestMessages = renderRequestMessages(
            templates = templates,
            userName = userName,
            memberNames = memberNames,
            existingSummary = safeExistingSummary,
            messages = sanitized.take(selected.size),
            messageImages = messageImages,
        )
        selected.flatMap { unavailableImages[it.id].orEmpty() }.requireAvailable()
        return GroupChatSummaryBuildResult(
            request = LLMGenerationRequest(
                messages = requestMessages,
                model = provider.model,
                options = LLMGenerationOptions(
                    temperature = provider.temperature,
                    maxTokens = responseTokens,
                    topP = provider.topP,
                ),
                isPromptFinalized = true,
            ),
            selectedMessages = selected,
        )
    }

    /** 渲染群聊摘要使用的 system 指令和 user 原始素材。 */
    private fun renderRequestMessages(
        templates: PromptTemplateSnapshot,
        userName: String,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>,
        messageImages: Map<Long, List<LLMImageReference>>,
    ): List<LLMMessage> {
        // 摘要任务独立于图文素材，旧摘要与历史不在指令中重复展开。
        val instruction = renderGroupMacros(
            templates.groupSummarizePrompt,
            mapOf(
                "user" to userName,
                "group" to memberNames.joinToString(", "),
                "summary" to "",
                "history" to "",
                "words" to AppModel.summaryWordsLimit.toString(),
            ),
        )
        // 保留发送时的成员署名及图文顺序。
        val history = messages.map { message ->
            RawSummaryHistoryMessage(
                speakerName = message.speakerName(userName),
                content = message.content,
                images = messageImages[message.id].orEmpty(),
            )
        }
        return buildRawSummaryMessages(
            templates = templates,
            instruction = instruction,
            existingSummary = existingSummary,
            history = history,
        )
    }
}
