package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript

/**
 * 单角色聊天 Prompt 构建所需的完整只读快照。
 *
 * 构建器不直接读取数据库；调用方应在进入构建流程前一次性准备会话、历史、
 * 世界书和 Regex 脚本，以保证同一次请求使用一致的数据版本。
 *
 * @property currentUserMessage 尚未写入历史的当前用户输入，重新生成时可为空。
 * @property totalMessageCount 会话普通消息总数，用于世界书 sticky/cooldown 计时。
 * @property recursiveScanningLorebookIds 明确允许递归扫描的世界书 ID。
 */
data class PromptBuildContext(
    val userName: String,
    val userDescription: String,
    val character: Character,
    val session: ChatSession,
    val summary: String,
    val messages: List<ChatMessage>,
    val currentUserMessage: String?,
    /** 会话中的普通消息总数，不受总结后历史裁剪影响。 */
    val totalMessageCount: Int = messages.size + if (currentUserMessage.isNullOrBlank()) 0 else 1,
    val candidateLorebookEntries: List<LorebookEntry>,
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val provider: LLMProvider?,
    val maxContextTokens: Int,
    val maxResponseTokens: Int,
    val generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
    val regexScripts: List<ScopedRegexScript> = emptyList()
)

/** 本次构建对应的用户操作，会影响尾部指令和世界书生成类型过滤。 */
enum class PromptGenerationMode {
    Normal,
    Continue,
    Impersonate,
    Regenerate
}

/**
 * 普通回复和重新生成共享“编写角色下一条回复”的任务提示。
 *
 * Continue 与 Impersonate 会在聊天末尾提供各自唯一的生成目标，不能再叠加主提示词或 PHI。
 */
internal fun PromptGenerationMode.usesCharacterReplyTask(): Boolean {
    return this == PromptGenerationMode.Normal || this == PromptGenerationMode.Regenerate
}
