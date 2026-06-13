package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit

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
    ContinueNudge,
    ImpersonationNudge,
    GroupIdentity,
    CharacterCard,
    GroupNudge,
    PostProcessing,
    Other
}

data class PromptSource(
    val kind: PromptSourceKind,
    val detail: String = "",
    /** 领域对象的稳定 ID，用于在后处理和预算裁剪后追踪来源。 */
    val referenceId: Long? = null
)

enum class PromptOmissionReason {
    ContextBudget,
    WorldInfoBudget
}

data class PromptOmittedItem(
    val source: PromptSource,
    val tokenCount: Int,
    val reason: PromptOmissionReason
)

enum class PromptTokenizerStrategy {
    ModelAware,
    Conservative
}

data class PromptInspectionItem(
    val index: Int,
    val role: LLMMessageRole,
    val sources: List<PromptSource>,
    val tokenCount: Int,
    val content: String
)

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
    val regexErrors: List<RegexExecutionError> = emptyList()
) {
    val hasOmissions: Boolean
        get() = omittedItems.isNotEmpty()
}

data class PromptMessageDraft(
    val role: LLMMessageRole,
    val content: String,
    val source: PromptSource,
    /** 数值越小越早从整体 Prompt 中移除。 */
    val retentionPriority: Int,
    /** 核心设定不可静默移除；空间不足时由预算器阻止请求。 */
    val canDrop: Boolean
)
