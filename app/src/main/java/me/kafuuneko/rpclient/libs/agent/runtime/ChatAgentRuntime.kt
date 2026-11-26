package me.kafuuneko.rpclient.libs.agent.runtime

import me.kafuuneko.rpclient.libs.agent.model.ConversationRef

/** 单聊适配器只限定会话域，正文规则和恢复由共用运行层管理。 */
class ChatAgentRuntime(private val mManager: AgentRunManager) {
    /** 在原生成入口固定本次 Agent 模式，后续设置变化只能撤权。 */
    suspend fun run(input: AgentTurnInput) {
        require(input.conversation is ConversationRef.Single) { "Expected single conversation" }
        mManager.run(input)
    }

    /** 恢复同一个角色运行，不重新发送用户消息。 */
    suspend fun resume(chatId: Long, answer: String, confirmed: Boolean) {
        mManager.resume(ConversationRef.Single(chatId), answer, confirmed)
    }
}
