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
        messages = when (mode) {
            PromptPostProcessingMode.None -> messages
            PromptPostProcessingMode.Merge -> messages.mergeConsecutiveRoles()
            PromptPostProcessingMode.SemiStrict -> messages.toSemiStrictMessages()
            PromptPostProcessingMode.Strict -> messages.toStrictMessages(strictPromptPlaceholder)
            PromptPostProcessingMode.SingleUserMessage -> messages.toSingleUserMessage()
        }
    )
}

private fun List<LLMMessage>.mergeConsecutiveRoles(): List<LLMMessage> {
    return fold(mutableListOf()) { merged, message ->
        val previous = merged.lastOrNull()
        if (previous?.role == message.role) {
            merged[merged.lastIndex] = previous.copy(
                content = listOf(previous.content, message.content)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
            )
        } else {
            merged += message
        }
        merged
    }
}

private fun List<LLMMessage>.toSemiStrictMessages(): List<LLMMessage> {
    // 部分协议只支持开头 system；中途 system 统一前置可避免被 adapter 降级成普通 user 内容。
    val systemContent = filter { it.role == LLMMessageRole.System }
        .joinToString("\n\n") { it.content }
        .trim()
    val nonSystemMessages = filterNot { it.role == LLMMessageRole.System }
    return buildList {
        if (systemContent.isNotBlank()) add(LLMMessage(LLMMessageRole.System, systemContent))
        addAll(nonSystemMessages)
    }.mergeConsecutiveRoles()
}

private fun List<LLMMessage>.toStrictMessages(strictPromptPlaceholder: String): List<LLMMessage> {
    val semiStrict = toSemiStrictMessages()
    val systemPrefix = semiStrict.takeWhile { it.role == LLMMessageRole.System }
    val body = semiStrict.drop(systemPrefix.size)
    if (body.isEmpty()) return semiStrict
    return buildList {
        addAll(systemPrefix)
        // 一些聊天模板要求第一条正文必须是 user；若角色问候在最前面，用占位用户消息补齐。
        if (body.first().role == LLMMessageRole.Assistant) {
            add(LLMMessage(LLMMessageRole.User, strictPromptPlaceholder))
        }
        addAll(body)
    }.mergeConsecutiveRoles()
}

private fun List<LLMMessage>.toSingleUserMessage(): List<LLMMessage> {
    if (isEmpty()) return this
    // 保留 role 标签，虽然失去原生 messages 结构，但模型仍能读到原始分区含义。
    val content = joinToString("\n\n") { message ->
        "${message.role.toPromptLabel()}:\n${message.content}"
    }
    return listOf(LLMMessage(LLMMessageRole.User, content))
}

private fun LLMMessageRole.toPromptLabel(): String {
    return when (this) {
        LLMMessageRole.System -> "System"
        LLMMessageRole.User -> "User"
        LLMMessageRole.Assistant -> "Assistant"
    }
}
