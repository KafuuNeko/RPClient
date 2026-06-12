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
    strictPromptPlaceholder: String
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
            strictPromptPlaceholder
        ).map { LLMMessage(it.role, it.content) },
        isPromptFinalized = true
    )
}

internal data class TrackedPromptMessage(
    val role: LLMMessageRole,
    val content: String,
    val sources: List<PromptSource>
)

internal fun postProcessTrackedMessages(
    messages: List<TrackedPromptMessage>,
    mode: PromptPostProcessingMode,
    strictPromptPlaceholder: String
): List<TrackedPromptMessage> {
    return when (mode) {
        PromptPostProcessingMode.None -> messages
        PromptPostProcessingMode.Merge -> messages.mergeConsecutiveRoles()
        PromptPostProcessingMode.SemiStrict -> messages.toSemiStrictMessages()
        PromptPostProcessingMode.Strict -> messages.toStrictMessages(strictPromptPlaceholder)
        PromptPostProcessingMode.SingleUserMessage -> messages.toSingleUserMessage()
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
                sources = (previous.sources + message.sources).distinct()
            )
        } else {
            merged += message
        }
        merged
    }
}

private fun List<TrackedPromptMessage>.toSemiStrictMessages(): List<TrackedPromptMessage> {
    // 部分协议只支持开头 system；中途 system 统一前置可避免被 adapter 降级成普通 user 内容。
    val systemMessages = filter { it.role == LLMMessageRole.System }
    val systemContent = systemMessages
        .joinToString("\n\n") { it.content }
        .trim()
    val nonSystemMessages = filterNot { it.role == LLMMessageRole.System }
    return buildList {
        if (systemContent.isNotBlank()) {
            add(
                TrackedPromptMessage(
                    role = LLMMessageRole.System,
                    content = systemContent,
                    sources = systemMessages.flatMap { it.sources }.distinct()
                )
            )
        }
        addAll(nonSystemMessages)
    }.mergeConsecutiveRoles()
}

private fun List<TrackedPromptMessage>.toStrictMessages(
    strictPromptPlaceholder: String
): List<TrackedPromptMessage> {
    val semiStrict = toSemiStrictMessages()
    val systemPrefix = semiStrict.takeWhile { it.role == LLMMessageRole.System }
    val body = semiStrict.drop(systemPrefix.size)
    if (body.isEmpty()) return semiStrict
    return buildList {
        addAll(systemPrefix)
        // 一些聊天模板要求第一条正文必须是 user；若角色问候在最前面，用占位用户消息补齐。
        if (body.first().role == LLMMessageRole.Assistant) {
            add(
                TrackedPromptMessage(
                    role = LLMMessageRole.User,
                    content = strictPromptPlaceholder,
                    sources = listOf(PromptSource(PromptSourceKind.PostProcessing))
                )
            )
        }
        addAll(body)
    }.mergeConsecutiveRoles()
}

private fun List<TrackedPromptMessage>.toSingleUserMessage(): List<TrackedPromptMessage> {
    if (isEmpty()) return this
    // 保留 role 标签，虽然失去原生 messages 结构，但模型仍能读到原始分区含义。
    val content = joinToString("\n\n") { message ->
        "${message.role.toPromptLabel()}:\n${message.content}"
    }
    return listOf(
        TrackedPromptMessage(
            role = LLMMessageRole.User,
            content = content,
            sources = flatMap { it.sources }.distinct()
        )
    )
}

private fun LLMMessageRole.toPromptLabel(): String {
    return when (this) {
        LLMMessageRole.System -> "System"
        LLMMessageRole.User -> "User"
        LLMMessageRole.Assistant -> "Assistant"
    }
}
