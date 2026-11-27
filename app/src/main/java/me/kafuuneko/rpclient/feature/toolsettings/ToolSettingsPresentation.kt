package me.kafuuneko.rpclient.feature.toolsettings

import androidx.annotation.StringRes
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.agent.connection.ToolAuthMode
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigValidationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaCodec

/** 根据参数 Schema 生成紧凑参数摘要列表供编辑器与测试框展示。 */
internal fun summarizeSchema(rawSchema: String): List<String> {
    val schema = runCatching { ToolSchemaCodec.decode(rawSchema) }.getOrNull() ?: return emptyList()
    return schema.properties.entries.take(12).map { (name, child) ->
        val mark = if (name in schema.required) "*" else ""
        "$name$mark (${child.type.name.lowercase()})"
    }
}

/** 根据工具参数 Schema 预生成可直接填写的测试参数 JSON 模板。 */
internal fun defaultTestArguments(rawSchema: String): String {
    val schema = runCatching { ToolSchemaCodec.decode(rawSchema) }.getOrNull() ?: return "{}"
    val selectedKeys = if (schema.required.isNotEmpty()) {
        schema.properties.keys.filter { it in schema.required }
    } else {
        schema.properties.keys.take(3)
    }
    val sample = JsonObject()
    selectedKeys.forEach { key ->
        val prop = schema.properties[key] ?: return@forEach
        when (prop.type) {
            ToolSchema.Type.String -> sample.addProperty(
                key,
                if (key == "url") "https://example.com" else "",
            )

            ToolSchema.Type.Integer -> sample.addProperty(key, prop.minimum?.toLong() ?: 0L)
            ToolSchema.Type.Number -> sample.addProperty(key, prop.minimum ?: 0.0)
            ToolSchema.Type.Boolean -> sample.addProperty(key, false)
            ToolSchema.Type.Array -> sample.add(key, JsonArray())
            ToolSchema.Type.Object -> sample.add(key, JsonObject())
            ToolSchema.Type.Null -> sample.add(key, null)
        }
    }
    return ToolConfigCodec.pretty(sample)
}

/** 为模板新建或复制的工具生成不与已有列表冲突的模型函数名。 */
internal fun suggestUniqueToolName(base: String, existing: Set<String>): String {
    if (base !in existing) return base
    var suffix = 2
    while (true) {
        val candidate = "${base.take(56)}_$suffix"
        if (candidate !in existing) return candidate
        suffix++
    }
}

/** 根据认证方式给出建议的默认认证头或查询参数名。 */
internal fun defaultAuthFieldName(authMode: String): String = when (authMode) {
    ToolAuthMode.Query -> "api_key"
    ToolAuthMode.ApiKey, ToolAuthMode.Header -> "X-API-Key"
    else -> "Authorization"
}

/** 将结构化配置校验异常映射为本地化提示资源，不向用户暴露内部异常原文。 */
@StringRes
internal fun validationErrorResource(error: Exception): Int {
    // 仅识别结构化工具配置错误码，未知异常统一回落为通用非法配置提示。
    val code = (error as? ToolConfigValidationException)?.code
    return when (code) {
        ToolErrorCode.INVALID_JSON -> R.string.tool_settings_error_invalid_json
        ToolErrorCode.INVALID_NAME -> R.string.tool_settings_error_invalid_name
        ToolErrorCode.DUPLICATE_NAME -> R.string.tool_settings_error_duplicate_name
        ToolErrorCode.INVALID_URL -> R.string.tool_settings_error_invalid_url
        ToolErrorCode.CREDENTIAL_IN_PLAIN_CONFIG -> R.string.tool_settings_error_plain_credential
        ToolErrorCode.INVALID_HEADERS, ToolErrorCode.INVALID_AUTH, ToolErrorCode.INVALID_METHOD, ToolErrorCode.INVALID_LIMITS ->
            R.string.tool_settings_error_invalid_connection

        ToolErrorCode.INVALID_SCHEMA -> R.string.tool_settings_error_invalid_schema
        ToolErrorCode.INVALID_MAPPING -> R.string.tool_settings_error_invalid_mapping
        ToolErrorCode.CONNECTION_IN_USE_BODY_BINDING -> R.string.tool_settings_error_connection_body_conflict
        ToolErrorCode.MISSING_CONNECTION -> R.string.tool_settings_needs_configuration
        ToolErrorCode.INVALID_OVERRIDE -> R.string.tool_settings_error_invalid_override
        ToolErrorCode.INVALID_PACKAGE -> R.string.tool_settings_error_invalid_package
        ToolErrorCode.INVALID_TEST_ARGUMENTS -> R.string.tool_settings_error_invalid_test_args
        ToolErrorCode.INVALID_CREDENTIAL -> R.string.tool_settings_error_invalid_credential
        else -> R.string.tool_settings_invalid
    }
}

/** 将工具真机测试返回的可恢复错误码映射为本地化状态提示。 */
@StringRes
internal fun testErrorResource(code: String): Int = when (code) {
    ToolErrorCode.HTTP_NEEDS_CONFIGURATION -> R.string.tool_settings_needs_configuration
    ToolErrorCode.HTTP_AUTH_REQUIRED -> R.string.tool_settings_test_auth_failed
    ToolErrorCode.HTTP_RATE_LIMITED -> R.string.tool_settings_test_rate_limited
    ToolErrorCode.HTTP_TIMEOUT -> R.string.tool_settings_test_timeout
    ToolErrorCode.HTTP_REMOTE_ERROR -> R.string.tool_settings_test_remote_error
    ToolErrorCode.HTTP_ADDRESS_BLOCKED, ToolErrorCode.HTTP_UNSAFE_REDIRECT, ToolErrorCode.HTTP_INVALID_URL, ToolErrorCode.HTTP_REDIRECT_LIMIT ->
        R.string.tool_settings_blocked

    ToolErrorCode.HTTP_RESPONSE_TOO_LARGE, ToolErrorCode.HTTP_ITEM_TOO_LARGE -> R.string.tool_settings_too_large
    ToolErrorCode.HTTP_MAPPING_MISSING, ToolErrorCode.HTTP_INVALID_MAPPING, ToolErrorCode.HTTP_INVALID_JSON, ToolErrorCode.HTTP_UNSUPPORTED_CONTENT ->
        R.string.tool_settings_mapping_failed

    else -> R.string.tool_settings_test_failed
}
