package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 稳定字段编码工具结果，避免 R8 改名及直接序列化闭包/业务上下文。 */
object ToolResultCodec {
    const val MAX_RESULT_BYTES = 64 * 1024
    const val MAX_TEXT_CHARACTERS = 8000

    /** 编码完整结果，超限时交由执行器报告错误，禁止截断 JSON。 */
    fun encode(result: ToolResult): JsonObject = JsonObject().apply {
        // 结果类别使用稳定键，用户提问与程序确认保留各自身份。
        when (result) {
            is ToolResult.Success -> {
                addProperty("kind", "success")
                add("data", result.data.deepCopy())
                result.writeDisposition?.let { addProperty("writeDisposition", it.name) }
                addProperty("truncated", result.truncated)
                result.nextCursor?.let { addProperty("nextCursor", it) }
            }

            is ToolResult.RecoverableError -> {
                addProperty("kind", "recoverable_error"); addProperty("code", result.code)
            }

            is ToolResult.FatalError -> {
                addProperty("kind", "fatal_error"); addProperty("code", result.code)
            }

            is ToolResult.AwaitingUser -> {
                addProperty("kind", "awaiting_user")
                addProperty("requestKind", result.kind.name)
                addProperty("question", result.question)
                add("options", JsonArray().apply { result.options.forEach(::add) })
                add("details", result.details.deepCopy())
            }
        }
        val contentLength = when (result) {
            is ToolResult.Success -> textLength(result.data)
            is ToolResult.AwaitingUser -> result.question.length + result.options.sumOf { it.length } + textLength(
                result.details
            )

            else -> 0
        }
        require(toString().toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES && contentLength <= MAX_TEXT_CHARACTERS) { "Tool result is too large" }
    }

    /** 解码已通过信封指纹校验的结果，未知结果类型显式失败。 */
    fun decode(value: JsonObject): ToolResult {
        // 按稳定 kind 字段还原结果，并对旧检查点的外发读取标记执行兼容剥离。
        return when (value["kind"].asString) {
            "success" -> ToolResult.Success(
                value.getAsJsonObject("data").deepCopy(),
                value["writeDisposition"]?.asString?.let(ToolWriteDisposition::valueOf),
                value["truncated"].asBoolean, value["nextCursor"]?.asString,
            )

            "recoverable_error" -> ToolResult.RecoverableError(value["code"].asString)
            "fatal_error" -> ToolResult.FatalError(value["code"].asString)
            "awaiting_user" -> ToolResult.AwaitingUser(
                value["question"].asString,
                value.getAsJsonArray("options").map { it.asString },
                requestKind(value),
                value.getAsJsonObject("details").deepCopy().apply { remove("externalEffect") }
            )

            else -> error("Unsupported tool result kind")
        }
    }

    /** 旧检查点用写入确认加 read 标记表示外发读取，仅在读取边界做一次兼容转换。 */
    private fun requestKind(value: JsonObject): ToolUserRequestKind {
        val kind = ToolUserRequestKind.valueOf(value["requestKind"].asString)
        return if (kind == ToolUserRequestKind.ExternalWriteConfirmation &&
            value.getAsJsonObject("details")["externalEffect"]?.asString == "read"
        ) ToolUserRequestKind.ExternalReadConfirmation else kind
    }

    private fun textLength(value: JsonElement): Int = when {
        value.isJsonObject -> value.asJsonObject.entrySet().sumOf { textLength(it.value) }
        value.isJsonArray -> value.asJsonArray.sumOf(::textLength)
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.length
        else -> 0
    }
}
