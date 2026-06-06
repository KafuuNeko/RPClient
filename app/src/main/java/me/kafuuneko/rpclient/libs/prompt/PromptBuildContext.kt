package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

data class PromptBuildContext(
    val userName: String,
    val userDescription: String,
    val character: Character,
    val session: ChatSession,
    val summary: String,
    val messages: List<ChatMessage>,
    val currentUserMessage: String?,
    // 会话中的普通消息总数，不受总结后历史裁剪影响。
    val totalMessageCount: Int = messages.size + if (currentUserMessage.isNullOrBlank()) 0 else 1,
    val candidateLorebookEntries: List<LorebookEntry>,
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val provider: LLMProvider?,
    val maxContextTokens: Int,
    val maxResponseTokens: Int,
    val generationMode: PromptGenerationMode = PromptGenerationMode.Normal
)

enum class PromptGenerationMode {
    Normal,
    Continue,
    Impersonate
}
