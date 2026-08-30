package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol

/** 用户请求体扩展无效、引用未知系统变量，或试图修改协议结构字段。 */
internal class LLMRequestBodyPatchException(message: String) : IllegalArgumentException(message)

private val SUPPORTED_SYSTEM_VARIABLES = setOf(LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID)
private const val SYSTEM_VARIABLE_PREFIX = "\$rpclient."

/**
 * 按 RFC 7396 的对象语义把用户 Patch 合并到协议基础请求。
 *
 * 只有完整字符串值与受支持变量相等时才会替换，不对普通字符串进行插值。
 * 数组和值整体替换，对象递归合并，null 删除字段。
 */
internal fun mergeRequestBodyJson(
    baseJson: String,
    patchJson: String,
    protectedPaths: Set<String>,
    systemVariables: Map<String, JsonElement> = emptyMap()
): String {
    val base = JsonParser.parseString(baseJson)
    val userPatch = parseAndValidatePatch(
        patchJson.ifBlank { "{}" },
        protectedPaths
    ).resolveSystemVariables(systemVariables)
    return mergePatch(base, userPatch).toString()
}

private fun parseAndValidatePatch(
    patchJson: String,
    protectedPaths: Set<String>
): JsonObject {
    val patch = runCatching { JsonParser.parseString(patchJson) }
        .getOrElse { throw LLMRequestBodyPatchException("Request body patch is not valid JSON.") }
    if (!patch.isJsonObject) {
        throw LLMRequestBodyPatchException("Request body patch must be a JSON object.")
    }
    val patchObject = patch.asJsonObject
    patchObject.validateSystemVariableReferences()
    val protectedPath = protectedPaths.firstOrNull { patchObject.containsPath(it) }
    if (protectedPath != null) {
        throw LLMRequestBodyPatchException(
            "Request body patch cannot modify protected field: $protectedPath"
        )
    }
    return patchObject
}

private fun JsonElement.validateSystemVariableReferences() {
    when {
        isJsonObject -> {
            val value = asJsonObject
            value.entrySet().forEach { (_, child) ->
                child.validateSystemVariableReferences()
            }
        }
        isJsonArray -> asJsonArray.forEach { it.validateSystemVariableReferences() }
        isJsonPrimitive && asJsonPrimitive.isString -> {
            val value = asString
            if (value.startsWith(SYSTEM_VARIABLE_PREFIX) &&
                value !in SUPPORTED_SYSTEM_VARIABLES
            ) {
                throw LLMRequestBodyPatchException("Unsupported system variable: $value")
            }
        }
    }
}

private fun JsonElement.resolveSystemVariables(
    variables: Map<String, JsonElement>
): JsonElement {
    if (isJsonObject) {
        val value = asJsonObject
        return JsonObject().also { resolved ->
            value.entrySet().forEach { (key, child) ->
                resolved.add(key, child.resolveSystemVariables(variables))
            }
        }
    }
    if (isJsonArray) {
        return JsonArray().also { resolved ->
            asJsonArray.forEach { child ->
                resolved.add(child.resolveSystemVariables(variables))
            }
        }
    }
    if (isJsonPrimitive && asJsonPrimitive.isString && asString in SUPPORTED_SYSTEM_VARIABLES) {
        return variables[asString]?.deepCopy() ?: JsonNull.INSTANCE
    }
    return deepCopy()
}

/** 校验 Patch 的根类型、系统变量引用和保护字段，供保存前表单校验复用。 */
internal fun validateRequestBodyPatch(
    patchJson: String,
    protectedPaths: Set<String>
): Result<Unit> = runCatching {
    parseAndValidatePatch(patchJson.ifBlank { "{}" }, protectedPaths)
    Unit
}

/** 返回当前协议由 RPClient 自己维护、扩展 Patch 不得覆盖的 JSON 路径。 */
internal fun protectedRequestBodyPaths(
    protocol: LLMProviderProtocol
): Set<String> {
    val protocolPaths = when (protocol) {
        LLMProviderProtocol.OpenAICompatible -> OPEN_AI_PROTECTED_REQUEST_FIELDS
        LLMProviderProtocol.AnthropicMessages -> ANTHROPIC_PROTECTED_REQUEST_FIELDS
        LLMProviderProtocol.Gemini -> GEMINI_PROTECTED_REQUEST_FIELDS
    }
    return protocolPaths
}

private fun JsonObject.containsPath(path: String): Boolean {
    val segments = path.split('.')
    var current: JsonElement = this
    segments.forEachIndexed { index, segment ->
        if (!current.isJsonObject || !current.asJsonObject.has(segment)) return false
        current = current.asJsonObject.get(segment)
        if (index == segments.lastIndex) return true
        // 用 null、标量或数组替换父对象，也会间接覆盖其中受保护的子字段。
        if (!current.isJsonObject) return true
    }
    return false
}

private fun mergePatch(target: JsonElement?, patch: JsonElement): JsonElement {
    if (!patch.isJsonObject) return patch.deepCopy()
    val result = target
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.deepCopy()
        ?: JsonObject()
    patch.asJsonObject.entrySet().forEach { (key, patchValue) ->
        if (patchValue.isJsonNull) {
            result.remove(key)
        } else {
            result.add(key, mergePatch(result.get(key), patchValue))
        }
    }
    return result
}

private val OPEN_AI_PROTECTED_REQUEST_FIELDS = setOf(
    "model",
    "messages",
    "stream",
    "stream_options.include_usage",
    "max_tokens",
    "max_completion_tokens",
    "temperature",
    "top_p",
    "stop"
)

private val ANTHROPIC_PROTECTED_REQUEST_FIELDS = setOf(
    "model",
    "messages",
    "system",
    "stream",
    "max_tokens",
    "temperature",
    "top_p",
    "stop_sequences"
)

private val GEMINI_PROTECTED_REQUEST_FIELDS = setOf(
    "contents",
    "systemInstruction",
    "generationConfig.maxOutputTokens",
    "generationConfig.temperature",
    "generationConfig.topP",
    "generationConfig.stopSequences"
)
