package me.kafuuneko.rpclient.libs.prompt.model

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit

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
    StoryMainPrompt,
    StoryMemory,
    StorySummary,
    StoryAuthorNote,
    StoryCharacter,
    StoryDocumentContext,
    StoryContinuationGuidance,
    StoryTask,
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
    /** 当前 Prompt 来源或推理文本的细分类型。 */
    val kind: PromptSourceKind,
    /** 用于解释当前状态的详细信息。 */
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
    /** 产生当前数据的来源。 */
    val source: PromptSource,
    /** 当前文本或 Prompt 项估算得到的 Token 数。 */
    val tokenCount: Int,
    /** 当前状态或取舍产生的原因。 */
    val reason: PromptOmissionReason
)

/** Token 统计的可信度策略。 */
enum class PromptTokenizerStrategy {
    /** 根据模型选择已知编码器，统计结果更接近模型服务的实际值。 */
    ModelAware,
    /** 使用其他离线 BPE 作为代理，并应用模型配置的估算预留率。 */
    Estimated,
    /** 预留给将来可证明不会低估的统计实现。 */
    Conservative
}

/** 最终请求中一条消息的检查快照。 */
data class PromptInspectionItem(
    /** 当前对象在所属有序集合中的位置。 */
    val index: Int,
    /** 当前对象在业务流程中承担的角色。 */
    val role: LLMMessageRole,
    /** 当前 Prompt 项合并后保留的原始来源列表。 */
    val sources: List<PromptSource>,
    /** 当前文本或 Prompt 项估算得到的 Token 数。 */
    val tokenCount: Int,
    /** 当前对象承载的正文内容。 */
    val content: String
)

/**
 * Prompt 构建检查报告。
 *
 * 同时保留最终消息、预算移除项和 Regex 执行记录，供调试界面解释
 * “模型实际收到了什么”以及“哪些内容为什么没有发送”。
 */
data class PromptInspection(
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 本次估算实际使用的 Tokenizer 名称。 */
    val tokenizerName: String,
    /** 本次 Token 统计采用的估算策略。 */
    val tokenizerStrategy: PromptTokenizerStrategy,
    /** 本地 Token 代理估算额外应用的安全预留率。 */
    val tokenizerReservePercent: Int = 0,
    /** Prompt 提交前采用的后处理模式。 */
    val postProcessingMode: PromptPostProcessingMode,
    /** 本次 Prompt 可使用的模型上下文总上限。 */
    val contextLimit: Int,
    /** Prompt 构建为模型回复预留的 Token 数。 */
    val responseReserve: Int,
    /** 本次 Prompt 构建可使用的 Token 预算。 */
    val promptBudget: Int,
    /** 最终 Prompt 在全部处理完成后的 Token 数。 */
    val finalTokenCount: Int,
    /** 当前状态包含的列表项。 */
    val items: List<PromptInspectionItem>,
    /** 因预算、空内容或业务规则未进入最终 Prompt 的项目。 */
    val omittedItems: List<PromptOmittedItem>,
    /** 本次 Prompt 构建实际执行的正则脚本记录。 */
    val regexExecutions: List<RegexExecutionHit> = emptyList(),
    /** 本次 Prompt 构建收集的正则脚本错误。 */
    val regexErrors: List<RegexExecutionError> = emptyList()
) {
    val hasOmissions: Boolean
        get() = omittedItems.isNotEmpty()
}

/**
 * 尚未执行后处理与预算裁剪的消息草稿。
 *
 * [retentionPriority] 与 [canDrop] 共同决定预算不足时的移除顺序。
 */
data class PromptMessageDraft(
    /** 当前对象在业务流程中承担的角色。 */
    val role: LLMMessageRole,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 产生当前数据的来源。 */
    val source: PromptSource,
    /** 数值越小越早从整体 Prompt 中移除。 */
    val retentionPriority: Int,
    /** 核心设定不可静默移除；空间不足时由预算器阻止请求。 */
    val canDrop: Boolean,
    /** 合并消息包含的全部领域来源；未合并消息默认只包含 [source]。 */
    val sources: List<PromptSource> = listOf(source)
)
