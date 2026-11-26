package me.kafuuneko.rpclient.libs.agent.runtime

import java.util.concurrent.ConcurrentHashMap
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef

/** 普通回复、摘要及 Agent 共用会话级准入；锁只保护登记，不覆盖网络与等待。 */
class ConversationGenerationActivityRegistry {
    private val mOwners = ConcurrentHashMap<String, Any>()

    /** 取得会话租约，已有其他页面或模式运行时明确拒绝重入。 */
    fun acquire(conversation: ConversationRef): Any = Any().also {
        if (mOwners.putIfAbsent(
                conversation.key,
                it
            ) != null
        ) throw AgentRuntimeException("conversation_busy")
    }

    /** 只释放自己持有的租约，旧任务收尾不能覆盖新运行。 */
    fun release(conversation: ConversationRef, owner: Any) {
        mOwners.remove(conversation.key, owner)
    }

    /** 普通生成沿用原实现，仅以租约阻止另一页面同时启动 Agent。 */
    suspend fun <T> withActivity(conversation: ConversationRef, execute: suspend () -> T): T {
        val owner = acquire(conversation)
        try {
            return execute()
        } finally {
            release(conversation, owner)
        }
    }

    /** 用于普通生成入口和设置页判断，不初始化工具目录或 Agent 运行。 */
    fun isBusy(conversation: ConversationRef): Boolean = mOwners.containsKey(conversation.key)
}
