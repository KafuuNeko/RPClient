package me.kafuuneko.rpclient.libs.agent.runtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef

/** 用户可见的有界预览，绝不接收工具参数、原始结果、签名或认证信息。 */
data class AgentProgress(val runId: String, val text: String = "", val reasoning: String = "")

/**
 * 流式预览与持久化账本隔离。
 * - 每次请求最多保留 6,000 字符，以 80ms 水位发布合并后的最新状态。
 * - 状态流天然合并慢读者，不逐 Token 写入 Room。
 */
class AgentProgressStore {
    private val mValues = ConcurrentHashMap<String, AgentProgress>()
    private val mPublishedAt = ConcurrentHashMap<String, Long>()
    private val mSignals = MutableStateFlow(0L)

    /** 订阅当前会话的低成本刷新信号，正文仍在读取时按运行 ID 过滤。 */
    fun observe(conversation: ConversationRef) =
        mSignals.map { mValues[conversation.key] }.distinctUntilChanged()

    /** 返回当前运行的可展示预览。 */
    fun get(conversation: ConversationRef, runId: String): AgentProgress? =
        mValues[conversation.key]?.takeIf { it.runId == runId }

    /** 同轮首片清空旧请求预览，不把过程说明误拼为最终正文。 */
    fun begin(conversation: ConversationRef, runId: String) {
        mValues[conversation.key] = AgentProgress(runId)
        publish(conversation, true)
    }

    /** 追加已由协议层识别的文本或可展示思考，原生签名没有进入本接口的路径。 */
    fun append(conversation: ConversationRef, runId: String, text: String, reasoning: Boolean) {
        mValues.compute(conversation.key) { _, previous ->
            val current = previous?.takeIf { it.runId == runId } ?: AgentProgress(runId)
            if (reasoning) current.copy(reasoning = (current.reasoning + text).takeLast(6000)) else current.copy(
                text = (current.text + text).takeLast(6000)
            )
        }
        publish(conversation, false)
    }

    /** 在轮次结束时补上非流式响应的预览。 */
    fun complete(conversation: ConversationRef, runId: String, text: String, reasoning: String) {
        mValues[conversation.key] =
            AgentProgress(runId, text.takeLast(6000), reasoning.takeLast(6000))
        publish(
            conversation,
            true
        )
    }

    /** 整库替换时清空本机预览，避免相同会话 ID 关联旧库正文。 */
    fun clear() {
        mValues.clear()
        mPublishedAt.clear()
        mSignals.update { it + 1 }
    }

    private fun publish(conversation: ConversationRef, force: Boolean) {
        val now = System.nanoTime()
        if (!force && now - (mPublishedAt[conversation.key] ?: 0) < 80_000_000) return
        mPublishedAt[conversation.key] = now
        mSignals.update { it + 1 }
    }
}

/** 只有禁工具收尾阶段才允许把流式片段视为可接受正文。 */
internal data class AgentPartialReply(
    val token: AgentExecutionToken,
    val plan: AgentTurnPlan,
    val content: String
)
