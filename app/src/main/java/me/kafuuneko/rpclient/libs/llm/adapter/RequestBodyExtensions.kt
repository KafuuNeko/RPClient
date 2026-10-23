package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID
import org.json.JSONObject

/**
 * 统一解析运行时变量并应用用户请求体 Patch。
 *
 * 协议适配器只负责基础请求结构；动态字段是否发送完全由高级 JSON 决定。
 */
internal fun JSONObject.withRequestBodyExtensions(
    provider: LLMProviderConfig,
    request: LLMGenerationRequest,
): JSONObject {
    return JSONObject(
        mergeRequestBodyExtensionsJson(
            baseJson = toString(),
            patchJson = provider.requestBodyPatchJson,
            protectedPaths = protectedRequestBodyPaths(provider.protocol),
            routingSessionId = request.routingSessionId,
        ),
    )
}

/** 仅依赖 Gson 的请求扩展核心，保持可在本地 JVM 测试中验证。 */
internal fun mergeRequestBodyExtensionsJson(
    baseJson: String,
    patchJson: String,
    protectedPaths: Set<String>,
    routingSessionId: String?,
): String {
    return mergeRequestBodyJson(
        baseJson = baseJson,
        patchJson = patchJson,
        protectedPaths = protectedPaths,
        systemVariables = systemVariables(routingSessionId),
    )
}

private fun systemVariables(routingSessionId: String?): Map<String, JsonElement> {
    return routingSessionId
        ?.let { mapOf(LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID to JsonPrimitive(it)) }
        .orEmpty()
}
