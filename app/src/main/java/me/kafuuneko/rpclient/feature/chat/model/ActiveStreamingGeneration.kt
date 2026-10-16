package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript

/**
 * 当前正在进行的流式生成状态快照。
 *
 * @property token 唯一令牌，用于防止多任务并发时的快照竞态
 * @property sessionId 会话 ID
 * @property output 生成目标形态
 * @property messageId 数据库中对应的占位消息 ID
 * @property createdPlaceholder 是否为本次生成新创建了占位记录
 * @property content 当前已累积接收到的原始文本
 * @property regexScripts 冻结的正则脚本列表
 * @property regexMacros 冻结的正则宏映射
 * @property worldInfoStateJson 世界书时序状态快照
 */
internal data class ActiveStreamingGeneration(
    /** 用于识别并取消当前生成任务的唯一令牌。 */
    val token: Any,
    /** 当前操作关联的会话 ID。 */
    val sessionId: Long,
    /** 当前流式生成累计得到的正文。 */
    val output: GenerationOutput,
    /** 当前操作关联的消息 ID。 */
    val messageId: Long?,
    /** 本次流式生成是否已经创建待写回的占位记录。 */
    val createdPlaceholder: Boolean,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 当前对象关联或允许执行的正则脚本列表。 */
    val regexScripts: List<ScopedRegexScript>,
    /** 流式生成完成时执行 Source 正则所需的宏映射。 */
    val regexMacros: Map<String, String>,
    /** 序列化后的世界书时序状态，需要随会话或故事持久化。 */
    val worldInfoStateJson: String
)
