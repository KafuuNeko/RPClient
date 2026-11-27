package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode

/** 配置边界的严格字段类型校验，不接受 Gson 的字符串、数字或布尔隐式转换。 */
internal object ToolConfigValues {
    /** 校验已出现字段的 JSON 类型，缺失字段仍由各自业务规则处理。 */
    fun validate(
        value: JsonObject,
        strings: Set<String> = emptySet(),
        nullableStrings: Set<String> = emptySet(),
        integers: Set<String> = emptySet(),
        booleans: Set<String> = emptySet(),
        objects: Set<String> = emptySet(),
        arrays: Set<String> = emptySet(),
    ) {
        // 只检查显式提供的字段，允许省略可选值但不允许用畸形类型伪装缺省。
        value.entrySet().forEach { (key, child) ->
            val primitive = child.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            val valid = when (key) {
                in strings -> primitive?.isString == true
                in nullableStrings -> child.isJsonNull || primitive?.isString == true
                in integers -> primitive?.isNumber == true && runCatching { primitive.asBigDecimal.toBigIntegerExact() }.isSuccess
                in booleans -> primitive?.isBoolean == true
                in objects -> child.isJsonObject
                in arrays -> child.isJsonArray
                else -> true
            }
            requireConfig(
                valid,
                ToolErrorCode.INVALID_JSON,
                "Invalid configuration field type: $key"
            )
        }
    }
}

/** 仅在配置解码边界归一化结构错误，取消不转换为普通验证失败。 */
internal inline fun <T> validateConfig(code: String, block: () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: ToolConfigValidationException) {
    throw error
} catch (_: Exception) {
    throw ToolConfigValidationException(code, "Invalid tool configuration")
}
