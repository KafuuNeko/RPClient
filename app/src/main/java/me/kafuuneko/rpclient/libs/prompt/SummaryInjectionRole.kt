package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole

/** 摘要按聊天深度注入时使用的消息角色。 */
enum class SummaryInjectionRole(val persistedValue: Int) {
    System(0),
    User(1),
    Assistant(2);

    /** 转换为通用 LLM 请求使用的消息角色。 */
    fun toMessageRole(): LLMMessageRole {
        return when (this) {
            System -> LLMMessageRole.System
            User -> LLMMessageRole.User
            Assistant -> LLMMessageRole.Assistant
        }
    }

    companion object {
        /** 将持久化整数转换为摘要消息角色。 */
        fun fromPersistedValue(value: Int): SummaryInjectionRole {
            return entries.firstOrNull { it.persistedValue == value } ?: System
        }
    }
}
