package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol

/** 请求日志列表专用投影，只携带元数据和固定长度预览，不加载完整原始载荷。 */
data class LLMRequestLogOverview(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 记录创建时的时间戳，单位为毫秒。 */
    val createTime: Long,
    /** 请求发生时模型配置的显示名称快照。 */
    val providerName: String,
    /** 模型配置实际采用的通信协议。 */
    val protocol: LLMProviderProtocol,
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 当前消息或请求是否处于流式生成状态。 */
    val isStreaming: Boolean,
    /** 经过脱敏和长度限制的请求预览。 */
    val requestPreview: String,
    /** 经过脱敏和长度限制的响应预览。 */
    val responsePreview: String
)
