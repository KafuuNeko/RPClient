package me.kafuuneko.rpclient.libs.agent.tool.builtin

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** 按完整条目分页，游标不截断 JSON 或原始文本。 */
internal fun page(
    items: List<JsonObject>,
    args: JsonObject,
    context: ToolExecutionContext
): ToolResult.Success {
    // 只按完整条目分页，游标永远指向下一条而不是截断 JSON。
    val offset = args["offset"]?.asInt ?: 0
    val limit = args["limit"]?.asInt ?: 20
    require(offset in 0..items.size) { "Invalid page offset" }
    val selected = items.drop(offset).take(limit)
    val more = offset + selected.size < items.size
    return ToolResult.Success(
        jsonObject(
            "items" to jsonArray(selected),
            "historyVersion" to context.token.historyVersion
        ),
        truncated = more,
        nextCursor = if (more) (offset + selected.size).toString() else null
    )
}

/** 按固定字符窗口分页返回单条长正文，避免单次工具返回突破结果上限。 */
internal fun textPage(text: String, offset: Int, idName: String, id: Long): ToolResult.Success {
    require(offset in 0..text.length) { "Invalid text cursor" }
    val end = (offset + 6000).coerceAtMost(text.length)
    return ToolResult.Success(
        jsonObject(idName to id, "text" to text.substring(offset, end)),
        truncated = end < text.length,
        nextCursor = end.takeIf { it < text.length }?.toString()
    )
}
