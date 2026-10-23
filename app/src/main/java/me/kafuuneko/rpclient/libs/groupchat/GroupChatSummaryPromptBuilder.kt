package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
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
    /** 使用当前摘要和未覆盖消息构建群聊摘要请求。 */
    fun buildWithSelection(
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>,
        provider: LLMProvider,
        messageImages: Map<Long, List<LLMImageReference>> = emptyMap(),
        unavailableImages: Map<Long, List<UnavailablePromptImage>> = emptyMap(),
    ): GroupChatSummaryBuildResult {
        val responseTokens = AppModel.summaryResponseTokens
        val promptBudget = summaryPromptBudget(provider.contextTokens, responseTokens)
        val limited = messages.summaryCandidates(AppModel.summaryMaxMessagesPerRequest)
        val safeExistingSummary = existingSummary.summarySafeContent()
        val sanitized = limited.map { message ->
            message.copy(content = message.content.summarySafeContent())
        }
        val tokenizer = mRequestFinalizer.tokenizerFor(provider)
        val selected = selectSummaryPrefix(limited, promptBudget) { prefix ->
            val requestMessages = renderRequestMessages(
                session = session,
                memberNames = memberNames,
                existingSummary = safeExistingSummary,
                messages = sanitized.subList(fromIndex = 0, toIndex = prefix.size),
                messageImages = messageImages,
                unavailableImages = unavailableImages,
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
                session = session,
                memberNames = memberNames,
                existingSummary = safeExistingSummary,
                messages = listOf(sanitized.first()),
                messageImages = messageImages,
                unavailableImages = unavailableImages,
            )
            throw PromptBudgetExceededException(
                requiredTokens = (tokenizer.countMessages(requestMessages).toLong() +
                    tokenizer.countUnavailableImages(unavailableImages[limited.first().id].orEmpty().size)).toTokenInt(),
                promptBudget = promptBudget,
            )
        }
        val requestMessages = renderRequestMessages(
            session = session,
            memberNames = memberNames,
            existingSummary = safeExistingSummary,
            messages = sanitized.take(selected.size),
            messageImages = messageImages,
            unavailableImages = unavailableImages,
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
        session: GroupChatSession,
        memberNames: List<String>,
        existingSummary: String,
        messages: List<GroupChatMessage>,
        messageImages: Map<Long, List<LLMImageReference>>,
        unavailableImages: Map<Long, List<UnavailablePromptImage>>,
    ): List<LLMMessage> {
        val history = messages.joinToString("\n") { message ->
            "${message.speakerNameSnapshot}: ${message.content}"
        }
        val instruction = AppModel
            .groupSummarizePrompt
            .replace(oldValue = "{{user}}", newValue = session.userName, ignoreCase = true)
            .replace(oldValue = "{{group}}", newValue = memberNames.joinToString(", "), ignoreCase = true)
            .replace(oldValue = "{{summary}}", newValue = "", ignoreCase = true)
            .replace(oldValue = "{{history}}", newValue = "", ignoreCase = true)
            .replace(oldValue = "{{words}}", newValue = AppModel.summaryWordsLimit.toString(), ignoreCase = true)
        val historyBlocks = messages.flatMap { message ->
            listOf(LLMContentBlock.Text("${message.speakerNameSnapshot}:")) +
                messageImages[message.id].orEmpty().map { image -> LLMContentBlock.Image(image) } +
                LLMContentBlock.Text(message.content)
        }
        return buildRawSummaryMessages(
            instruction = instruction,
            existingSummary = existingSummary,
            history = history,
            historyBlocks = historyBlocks.takeIf { messages.any { message ->
                !messageImages[message.id].isNullOrEmpty() || !unavailableImages[message.id].isNullOrEmpty()
            } },
        )
    }
}
