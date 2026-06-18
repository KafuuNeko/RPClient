package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode

/** Prompt 内容来源，用于检查器展示、预算裁剪记录和领域对象追踪。 */
enum class PromptSourceKind {
    MainPrompt,
    WorldInfo,
    UserPersona,
    CharacterDescription,
    CharacterPersonality,
    Scenario,
    Summary,
    AuxiliaryPrompt,
    ExampleDialogue,
    NewChatMarker,
    ChatHistory,
    UserNote,
    CharacterNote,
    PostHistoryInstructions,
    CharacterReplyNudge,
    ContinueNudge,
    ImpersonationNudge,
    GroupIdentity,
    CharacterCard,
    GroupNudge,
    PostProcessing,
    Other
}

/**
 * 一段 Prompt 的领域来源。
 *
 * [referenceId] 用于在后处理合并消息后仍能追踪世界书等实体，
 * [detail] 仅用于人类可读的名称或补充说明。
 */
data class PromptSource(
    val kind: PromptSourceKind,
    val detail: String = "",
    /** 领域对象的稳定 ID，用于在后处理和预算裁剪后追踪来源。 */
    val referenceId: Long? = null
)

/** Prompt 内容未进入最终请求的原因。 */
enum class PromptOmissionReason {
    ContextBudget,
    WorldInfoBudget
}

/** 一项被预算器移除的内容及其估算成本。 */
data class PromptOmittedItem(
    val source: PromptSource,
    val tokenCount: Int,
    val reason: PromptOmissionReason
)

/** Token 统计的可信度策略。 */
enum class PromptTokenizerStrategy {
    /** 根据模型选择已知编码器，统计结果更接近供应商实际值。 */
    ModelAware,
    /** 使用 UTF-8 字节上界，宁可少装内容也不冒超出上下文的风险。 */
    Conservative
}

enum class PromptCacheNoteKind {
    DynamicMacro,
    PrefixWorldInfo,
    PrefixSummary,
    RegexPromptRewrite,
    ContextTrim
}

data class PromptCacheNote(
    val kind: PromptCacheNoteKind,
    val detail: String = ""
)

/** 最终请求中一条消息的检查快照。 */
data class PromptInspectionItem(
    val index: Int,
    val role: LLMMessageRole,
    val sources: List<PromptSource>,
    val tokenCount: Int,
    val content: String,
    val cacheNotes: List<PromptCacheNote> = emptyList()
)

/**
 * Prompt 构建检查报告。
 *
 * 同时保留最终消息、预算移除项和 Regex 执行记录，供调试界面解释
 * “模型实际收到了什么”以及“哪些内容为什么没有发送”。
 */
data class PromptInspection(
    val model: String,
    val tokenizerName: String,
    val tokenizerStrategy: PromptTokenizerStrategy,
    val postProcessingMode: PromptPostProcessingMode,
    val contextLimit: Int,
    val responseReserve: Int,
    val promptBudget: Int,
    val finalTokenCount: Int,
    val items: List<PromptInspectionItem>,
    val omittedItems: List<PromptOmittedItem>,
    val regexExecutions: List<RegexExecutionHit> = emptyList(),
    val regexErrors: List<RegexExecutionError> = emptyList(),
    val cacheNotes: List<PromptCacheNote> = emptyList()
) {
    val hasOmissions: Boolean
        get() = omittedItems.isNotEmpty()

    val hasCacheNotes: Boolean
        get() = cacheNotes.isNotEmpty() || items.any { it.cacheNotes.isNotEmpty() }
}

/**
 * 尚未执行后处理与预算裁剪的消息草稿。
 *
 * [retentionPriority] 与 [canDrop] 共同决定预算不足时的移除顺序。
 */
data class PromptMessageDraft(
    val role: LLMMessageRole,
    val content: String,
    val source: PromptSource,
    /** 数值越小越早从整体 Prompt 中移除。 */
    val retentionPriority: Int,
    /** 核心设定不可静默移除；空间不足时由预算器阻止请求。 */
    val canDrop: Boolean,
    /** 合并消息包含的全部领域来源；未合并消息默认只包含 [source]。 */
    val sources: List<PromptSource> = listOf(source),
    val cacheNotes: List<PromptCacheNote> = emptyList()
)

internal fun List<RegexExecutionHit>.promptCacheNotes(): List<PromptCacheNote> {
    return filter { it.mode == RegexExecutionMode.Prompt && it.changed }
        .map {
            PromptCacheNote(
                PromptCacheNoteKind.RegexPromptRewrite,
                it.scriptName.ifBlank { it.scriptId }
            )
        }
        .distinct()
}
