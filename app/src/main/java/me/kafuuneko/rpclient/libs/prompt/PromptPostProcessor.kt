package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole

/**
 * 在协议适配前改写通用消息结构。
 *
 * 这样 OpenAI-compatible、Gemini、Anthropic 都能共享同一套 Tavern 风格
 * Prompt Post-Processing，避免把兼容逻辑散落到各个 HTTP adapter。
 */
fun LLMGenerationRequest.withPostProcessedMessages(
    mode: PromptPostProcessingMode,
    strictPromptPlaceholder: String,
    names: PromptPostProcessingNames = PromptPostProcessingNames()
): LLMGenerationRequest {
    return copy(
        messages = postProcessTrackedMessages(
            messages.map {
                TrackedPromptMessage(
                    role = it.role,
                    content = it.content,
                    sources = listOf(PromptSource(PromptSourceKind.Other))
                )
            },
            mode,
            strictPromptPlaceholder,
            names
        ).map { LLMMessage(it.role, it.content) },
        isPromptFinalized = true
    )
}

/**
 * Prompt 后处理使用的会话名称。
 *
 * Single 模式会丢失原生 role，因此需要把用户和角色名称写回消息正文，
 * 以保留多轮对话中的发言者边界。
 */
data class PromptPostProcessingNames(
    val userName: String = "",
    val characterName: String = "",
    val groupNames: List<String> = emptyList()
)

internal data class TrackedPromptMessage(
    val role: LLMMessageRole,
    val content: String,
    val sources: List<PromptSource>,
    val cacheNotes: List<PromptCacheNote> = emptyList()
)

/**
 * 在保留来源追踪的前提下执行 Prompt Post-Processing。
 *
 * 合并消息时必须同步合并 [TrackedPromptMessage.sources]，否则检查器和世界书时序状态
 * 会误以为被合并的内容已从最终请求中移除。
 */
internal fun postProcessTrackedMessages(
    messages: List<TrackedPromptMessage>,
    mode: PromptPostProcessingMode,
    strictPromptPlaceholder: String,
    names: PromptPostProcessingNames = PromptPostProcessingNames()
): List<TrackedPromptMessage> {
    return when (mode) {
        PromptPostProcessingMode.None -> messages
        PromptPostProcessingMode.Merge -> messages.mergeConsecutiveRoles()
        PromptPostProcessingMode.SemiStrict -> messages.toSemiStrictMessages()
        PromptPostProcessingMode.Strict -> messages.toStrictMessages(strictPromptPlaceholder)
        PromptPostProcessingMode.SingleUserMessage -> messages.toSingleUserMessage(names)
    }
}

private fun List<TrackedPromptMessage>.mergeConsecutiveRoles(): List<TrackedPromptMessage> {
    return fold(mutableListOf()) { merged, message ->
        val previous = merged.lastOrNull()
        if (previous?.role == message.role) {
            merged[merged.lastIndex] = previous.copy(
                content = listOf(previous.content, message.content)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n"),
                sources = (previous.sources + message.sources).distinct(),
                cacheNotes = (previous.cacheNotes + message.cacheNotes).distinct()
            )
        } else {
            merged += message
        }
        merged
    }
}

private fun List<TrackedPromptMessage>.toSemiStrictMessages(): List<TrackedPromptMessage> {
    // Semi-strict 保留首条 system，后续 system 在原索引转为 user。
    return mergeConsecutiveRoles()
        .mapIndexed { index, message ->
            if (index > 0 && message.role == LLMMessageRole.System) {
                message.copy(role = LLMMessageRole.User)
            } else {
                message
            }
        }
        .mergeConsecutiveRoles()
}

private fun List<TrackedPromptMessage>.toStrictMessages(
    strictPromptPlaceholder: String
): List<TrackedPromptMessage> {
    val semiStrict = toSemiStrictMessages()
    val placeholder = TrackedPromptMessage(
        role = LLMMessageRole.User,
        content = strictPromptPlaceholder,
        sources = listOf(PromptSource(PromptSourceKind.PostProcessing))
    )
    val strict = semiStrict.toMutableList()

    // Strict 为 system -> assistant 和仅 system 的起始序列补入 user 占位消息。
    when {
        strict.isEmpty() -> strict += placeholder
        strict.first().role == LLMMessageRole.System &&
            (strict.size == 1 || strict[1].role != LLMMessageRole.User) -> {
            strict.add(1, placeholder)
        }
        strict.first().role == LLMMessageRole.Assistant -> strict.add(0, placeholder)
    }
    return strict.mergeConsecutiveRoles()
}

private fun List<TrackedPromptMessage>.toSingleUserMessage(
    names: PromptPostProcessingNames
): List<TrackedPromptMessage> {
    val flattened = ifEmpty {
        listOf(
            TrackedPromptMessage(
                role = LLMMessageRole.User,
                content = DEFAULT_STRICT_PROMPT_PLACEHOLDER,
                sources = listOf(PromptSource(PromptSourceKind.PostProcessing))
            )
        )
    }.map { message ->
        val content = when (message.role) {
            LLMMessageRole.User -> message.content.withSpeakerPrefix(names.userName)
            LLMMessageRole.Assistant -> message.content.withAssistantPrefix(names)
            LLMMessageRole.System -> message.content
        }
        message.copy(role = LLMMessageRole.User, content = content)
    }
    return flattened.mergeConsecutiveRoles()
}

private fun String.withAssistantPrefix(names: PromptPostProcessingNames): String {
    if (names.groupNames.any { startsWith("$it: ") }) return this
    return withSpeakerPrefix(names.characterName)
}

private fun String.withSpeakerPrefix(name: String): String {
    if (name.isBlank() || startsWith("$name: ")) return this
    return "$name: $this"
}

const val DEFAULT_STRICT_PROMPT_PLACEHOLDER = "Let's get started."
