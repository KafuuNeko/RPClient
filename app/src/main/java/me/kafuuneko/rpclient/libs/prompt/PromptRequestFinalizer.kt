package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

data class PromptFinalizationResult(
    val request: LLMGenerationRequest,
    val inspection: PromptInspection
)

class PromptBudgetExceededException(
    val requiredTokens: Int,
    val promptBudget: Int
) : IllegalStateException(
    "Prompt requires $requiredTokens tokens, but only $promptBudget input tokens are available. " +
        "Shorten the core prompt or increase the context limit."
)

class PromptRequestFinalizer(
    private val mTokenizerResolver: PromptTokenizerResolver = PromptTokenizerRegistry()
) {
    fun tokenizerFor(provider: LLMProvider?): PromptTokenizer {
        return mTokenizerResolver.resolve(provider)
    }

    fun finalize(
        drafts: List<PromptMessageDraft>,
        provider: LLMProvider?,
        model: String?,
        options: LLMGenerationOptions,
        includeReasoningInContent: Boolean,
        maxContextTokens: Int,
        maxResponseTokens: Int,
        postProcessingMode: PromptPostProcessingMode,
        strictPromptPlaceholder: String,
        preOmittedItems: List<PromptOmittedItem> = emptyList()
    ): PromptFinalizationResult {
        val promptBudget = maxContextTokens - maxResponseTokens
        require(maxContextTokens > 0) { "Context token limit must be greater than zero." }
        require(maxResponseTokens > 0) { "Response token reserve must be greater than zero." }
        require(promptBudget > 0) {
            "Response token reserve must be smaller than the context token limit."
        }

        val tokenizer = tokenizerFor(provider)
        val kept = drafts.filter { it.content.isNotBlank() }.toMutableList()
        val omitted = preOmittedItems.toMutableList()

        while (true) {
            val processed = kept.postProcess(postProcessingMode, strictPromptPlaceholder)
            val messages = processed.map { LLMMessage(it.role, it.content) }
            val finalTokenCount = tokenizer.countMessages(messages)
            if (finalTokenCount <= promptBudget) {
                return PromptFinalizationResult(
                    request = LLMGenerationRequest(
                        messages = messages,
                        model = model,
                        options = options,
                        includeReasoningInContent = includeReasoningInContent,
                        isPromptFinalized = true
                    ),
                    inspection = PromptInspection(
                        model = model.orEmpty(),
                        tokenizerName = tokenizer.name,
                        tokenizerStrategy = tokenizer.strategy,
                        postProcessingMode = postProcessingMode,
                        contextLimit = maxContextTokens,
                        responseReserve = maxResponseTokens,
                        promptBudget = promptBudget,
                        finalTokenCount = finalTokenCount,
                        items = processed.mapIndexed { index, message ->
                            val llmMessage = LLMMessage(message.role, message.content)
                            PromptInspectionItem(
                                index = index + 1,
                                role = message.role,
                                sources = message.sources.distinct(),
                                tokenCount = tokenizer.countMessage(llmMessage),
                                content = message.content
                            )
                        },
                        omittedItems = omitted
                    )
                )
            }

            val removable = kept.withIndex()
                .filter { it.value.canDrop }
                .minWithOrNull(
                    compareBy<IndexedValue<PromptMessageDraft>> { it.value.retentionPriority }
                        .thenBy { it.index }
                )
            if (removable == null) {
                throw PromptBudgetExceededException(finalTokenCount, promptBudget)
            }
            val removed = kept.removeAt(removable.index)
            omitted += PromptOmittedItem(
                source = removed.source,
                tokenCount = tokenizer.countText(removed.content),
                reason = PromptOmissionReason.ContextBudget
            )
        }
    }

    private fun List<PromptMessageDraft>.postProcess(
        mode: PromptPostProcessingMode,
        strictPromptPlaceholder: String
    ): List<TrackedPromptMessage> {
        return postProcessTrackedMessages(
            messages = map {
                TrackedPromptMessage(
                    role = it.role,
                    content = it.content,
                    sources = listOf(it.source)
                )
            },
            mode = mode,
            strictPromptPlaceholder = strictPromptPlaceholder
        )
    }
}
