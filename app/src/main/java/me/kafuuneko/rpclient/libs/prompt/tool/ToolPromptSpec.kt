package me.kafuuneko.rpclient.libs.prompt.tool

import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.memory.MemoryTable

/** 工具说明与结构化参数的原始投影，编辑器和运行工厂共享。 */
data class ToolPromptSpec(val description: String, val parameters: ToolSchema)

/** 构造带分页参数的标准关键词检索 Schema。 */
internal fun searchSchema() = objectSchema(
    "query" to string(0, 200),
    "offset" to integer(0),
    "limit" to integer(1, 20),
    required = setOf("query"),
)

/** 构造记忆表枚举约束 Schema，与持久化表名保持一致。 */
internal fun tableSchema() = string(1, 20).copy(
    enumValues = MemoryTable.entries.map { JsonPrimitive(it.name) },
)

/** 构造带长度区间的字符串参数 Schema。 */
internal fun string(min: Int, max: Int) =
    ToolSchema(ToolSchema.Type.String, minLength = min, maxLength = max)

/** 构造带上下界的整数参数 Schema。 */
internal fun integer(min: Int, max: Int? = null) =
    ToolSchema(ToolSchema.Type.Integer, minimum = min.toDouble(), maximum = max?.toDouble())

/** 构造按声明顺序保序的对象参数 Schema。 */
internal fun objectSchema(
    vararg fields: Pair<String, ToolSchema>,
    required: Set<String> = emptySet(),
) = ToolSchema(
    ToolSchema.Type.Object,
    properties = linkedMapOf(*fields),
    required = required,
)
