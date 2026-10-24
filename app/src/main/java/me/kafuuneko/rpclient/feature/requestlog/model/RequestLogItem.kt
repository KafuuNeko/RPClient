package me.kafuuneko.rpclient.feature.requestlog.model

import me.kafuuneko.rpclient.libs.room.model.LLMRequestLogOverview
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 请求日志列表展示模型，只保存固定长度预览；完整 JSON 在用户操作时按 ID 加载。 */
data class RequestLogItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 标题下方用于补充说明的副标题。 */
    val subtitle: String,
    /** 经过脱敏和长度限制的请求预览。 */
    val requestPreview: String,
    /** 经过脱敏和长度限制的响应预览。 */
    val responsePreview: String
)

/** 将数据库日志实体转换为列表展示项模型。 */
internal fun LLMRequestLogOverview.toUiModel(): RequestLogItem {
    val mode = if (isStreaming) "stream" else "once"
    return RequestLogItem(
        id = id,
        title = "$providerName / $model",
        subtitle = "${createTime.formatTimestamp("MM-dd HH:mm:ss")} · ${protocol.name} · $mode",
        requestPreview = requestPreview,
        responsePreview = responsePreview
    )
}
