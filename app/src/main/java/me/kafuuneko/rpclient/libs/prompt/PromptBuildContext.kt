package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

data class PromptBuildContext(
    val userName: String,
    val character: Character,
    val session: ChatSession,
    val messages: List<ChatMessage>,
    val currentUserMessage: String?,
    val candidateLorebookEntries: List<LorebookEntry>,
    val provider: LLMProvider?,
    val maxContextTokens: Int,
    val maxResponseTokens: Int
)

