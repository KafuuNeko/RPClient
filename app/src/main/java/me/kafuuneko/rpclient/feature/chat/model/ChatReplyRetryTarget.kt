package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.prompt.model.PromptGenerationMode

/**
 * 单聊一次可重试生成任务的恢复快照。
 *
 * - 记录本轮生成模式、已落库的用户触发消息以及原位覆盖的目标消息。
 * - 流式生成中途失败并落库残缺回复时，[targetMessageId] 会更新为该残缺消息 ID，
 *   重试时排除该残缺正文并在原气泡上覆盖输出。
 */
internal data class ChatReplyRetryTarget(
    val sessionId: Long,
    val generationMode: PromptGenerationMode,
    /** 已提交的用户触发消息 ID；仅普通发送路径非空。 */
    val triggerUserMessageId: Long? = null,
    /** 原位覆盖的目标消息 ID：重生成目标或流式中断留下的残缺回复。 */
    val targetMessageId: Long? = null,
    /** 是否以用户消息身份落库（扮演用户生成消息模式）。 */
    val isUserImpersonation: Boolean = false,
)
