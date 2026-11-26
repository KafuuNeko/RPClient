package me.kafuuneko.rpclient.libs.agent.runtime

import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolExchange
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 页面交给领域运行层的只读输入，页面生命周期不参与后续持久化。 */
data class AgentTurnInput(
    val conversation: ConversationRef,
    val characterId: Long,
    val personaId: Long,
    val provider: LLMProvider,
    val request: LLMGenerationRequest,
    val drafts: List<PromptMessageDraft>,
    val templates: PromptTemplateSnapshot,
    val scripts: List<ScopedRegexScript>,
    val macros: Map<String, String>,
    val worldInfoStateJson: String,
    val replacingMessageId: Long? = null,
    val speakerName: String = "",
    val otherSpeakerNames: List<String> = emptyList(),
    val trimOtherSpeakers: Boolean = true,
    val batchId: String? = null,
    val remainingSpeakers: List<Long> = emptyList(),
    val streaming: Boolean = true,
)

/** 运行冻结快照不保存 Provider 密钥，恢复时以配置指纹复核模型身份。 */
data class AgentTurnPlan(
    val input: AgentTurnInput,
    val providerDigest: String,
    val toolDigests: Map<String, String>,
    val finalizeInstruction: String,
    val errorInstruction: String,
    val compactionInstruction: String,
    val compactionCoveredMessageId: Long = 0,
    val prefixMessageCount: Int = 0,
)

/** 每轮模型和工具完成后发布一次检查点，流式 Token 不写 Room。 */
data class AgentRuntimeCheckpoint(
    val plan: AgentTurnPlan,
    val exchanges: List<LLMToolExchange> = emptyList(),
    val pending: LLMToolTurn? = null,
    val pendingStepId: Long = 0,
    val pendingInstruction: String = "",
)

/** 挂起属于正常运行结果，外层群聊必须暂停剩余队列。 */
class AgentWaitingForUser : IllegalStateException("Agent is waiting for user input")

/** 稳定运行失败分类由页面映射为本地化提示，不展示内部错误原文。 */
class AgentRuntimeException(val code: String) : IllegalStateException("Agent runtime: $code")
