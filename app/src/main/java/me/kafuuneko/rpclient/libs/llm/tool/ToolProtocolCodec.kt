package me.kafuuneko.rpclient.libs.llm.tool

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaCodec
import me.kafuuneko.rpclient.libs.llm.adapter.arrayOrNull
import me.kafuuneko.rpclient.libs.llm.adapter.cleanString
import me.kafuuneko.rpclient.libs.llm.adapter.objectOrNull
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.utils.jsonObject

/** 五协议只负责线格式，工具处理器与运行状态机不依赖协议名称。 */
internal interface ToolProtocolCodec {
    /** 将工具与交换链追加到已编码的基础请求，不改变冻结消息前缀。 */
    fun extend(payload: JsonObject, request: LLMToolGenerationRequest)

    /** 提取完整轮次，同时保留下一次发送必需的有序原生块。 */
    fun decode(response: JsonObject, model: String): LLMToolTurn

    /** 为每次 HTTP 响应创建独立流聚合器，禁止跨请求复用可变缓冲。 */
    fun stream(): ToolStreamAccumulator
}

/** 以协议终态而非 TCP EOF 判定轮次完成。 */
internal interface ToolStreamAccumulator {
    /** 消费已完整解码的 SSE 帧；预览与最终可执行调用分离。 */
    fun accept(type: String, data: JsonObject)

    /** EOF 时校验终态及各调用完整性。 */
    fun finish(model: String): LLMToolTurn
}

/** 缺省数组可以省略，错误类型不能被当成没有调用而静默忽略。 */
internal fun JsonObject.objects(key: String): List<JsonObject> {
    val value = get(key) ?: return emptyList()
    if (!value.isJsonArray) throw LLMToolProtocolException("invalid_blocks")
    return value.asJsonArray.map {
        if (!it.isJsonObject) throw LLMToolProtocolException("invalid_block")
        it.asJsonObject
    }
}

/** 关联 ID 及名称不可为空，诊断只包含固定字段名。 */
internal fun JsonObject.required(key: String): String =
    cleanString(key).ifEmpty { throw LLMToolProtocolException("missing_$key") }

/** 只在当前响应内追加协议声明为增量的文本。 */
internal fun JsonObject.append(field: String, text: String) {
    addProperty(field, cleanString(field) + text)
}

/** 参数兼容 JSON 字符串与原生对象，最终仍经严格对象解析器校验。 */
internal fun JsonObject.callArguments(key: String): String =
    get(key)?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else it.toString() }
        ?: "{}"

/** Provider 错误信封不能作为普通完成响应继续处理。 */
internal fun JsonObject.checkError() {
    if (has("error")) throw LLMToolProtocolException("provider_error")
}

/** 基础协议必须已经提供有序历史，禁止构造空历史降级。 */
internal fun JsonObject.history(key: String): JsonArray =
    arrayOrNull(key) ?: throw LLMToolProtocolException("missing_history")

/** 无远程 ID 协议同样要求逐位置一一配对。 */
internal fun LLMToolGenerationRequest.checkExchanges() {
    exchanges.forEach { if (it.turn.calls.size != it.results.size) throw LLMToolProtocolException("unpaired_results") }
}

/** 可空 Schema 用原生 JSON Schema 表示；不把可选参数改写为必填 null。 */
internal fun wireSchema(schema: ToolSchema): JsonObject {
    fun convert(node: JsonObject) {
        node.objectOrNull("properties")?.entrySet()
            ?.forEach { (_, value) -> convert(value.asJsonObject) }
        node.objectOrNull("items")?.let(::convert)
        if (node.remove("nullable")?.asBoolean == true) {
            val type = node.remove("type")
            node.add("type", JsonArray().apply {
                add(type)
                add("null")
            })
            node.getAsJsonArray("enum")
                ?.let { values -> if (values.none { it.isJsonNull }) values.add(JsonNull.INSTANCE) }
        }
    }
    return ToolSchemaCodec.encode(schema).also(::convert)
}

/** Strict 仅用于属性均必填且禁止额外属性的对象定义。 */
internal fun strictCompatible(schema: ToolSchema): Boolean =
    (schema.type != ToolSchema.Type.Object || (!schema.additionalProperties && schema.required == schema.properties.keys)) &&
        schema.properties.values.all(::strictCompatible) && (schema.items?.let(::strictCompatible) != false)

/** 保留可选字段语义，不把缺省字段改写成可空必填。 */
internal fun functionDefinition(definition: AgentToolDefinition): JsonObject = jsonObject(
    "name" to definition.name,
    "description" to definition.description,
    "parameters" to wireSchema(definition.parameters),
    "strict" to strictCompatible(definition.parameters)
)

/** Gson 的解析边界在 HTTP 层受体积限制；调用参数另经严格 JSON 解析。 */
internal fun parseObject(value: String): JsonObject = try {
    AgentJson.parse(value, 4 * 1024 * 1024, 64).asJsonObject
} catch (_: Exception) {
    throw LLMToolProtocolException("invalid_json")
}
