package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind

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
    /** 当前会话或 Prompt 使用的用户名称。 */
    val userName: String = "",
    /** 关联角色的显示名称快照。 */
    val characterName: String = "",
    /** Prompt 后处理规则识别的分组名称映射。 */
    val groupNames: List<String> = emptyList()
)

/**
 * 后处理阶段携带来源追踪的消息。
 *
 * role 或正文可以因协议兼容策略改变，但 [sources] 必须随合并结果保留，供预算检查器
 * 和世界书时序提交判断实际进入请求的内容。
 */
internal data class TrackedPromptMessage(
    /** 当前对象在业务流程中承担的角色。 */
    val role: LLMMessageRole,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 当前 Prompt 项合并后保留的原始来源列表。 */
    val sources: List<PromptSource>
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

/** 将连续相同角色的消息合并为单条消息，使用双换行符拼接。 */
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

/**
 * 将消息转为 Semi-Strict 半严格模式。
 *
 * 仅保留首条 System 消息，后续 System 消息转为 User 消息并合并连续相同角色。
 */
private fun List<TrackedPromptMessage>.toSemiStrictMessages(): List<TrackedPromptMessage> {
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

/**
 * 将消息转为 Strict 严格模式（Anthropic / Gemini 等要求起始 User 消息的模型协议）。
 *
 * 在首条 System 之后或以 Assistant 开头时补入占位 User 消息。
 */
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

    // 为起始无 User 消息的序列补入占位符
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

/**
 * 将所有消息扁平化压缩为单条 User 消息（Single User Message 模式）。
 *
 * 自动为不同角色的文本附加发言者前缀以保持对话结构。
 */
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

/** 辅助扩展：为 Assistant 消息附加角色发言者前缀。 */
private fun String.withAssistantPrefix(names: PromptPostProcessingNames): String {
    if (names.groupNames.any { startsWith("$it: ") }) return this
    return withSpeakerPrefix(names.characterName)
}

/** 辅助扩展：若无前缀则添加冒号发言者前缀。 */
private fun String.withSpeakerPrefix(name: String): String {
    if (name.isBlank() || startsWith("$name: ")) return this
    return "$name: $this"
}

/** 默认严格模式占位提示词内容。 */
const val DEFAULT_STRICT_PROMPT_PLACEHOLDER = "Let's get started."

