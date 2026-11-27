package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import okhttp3.Headers

/**
 * 连接的非敏感配置，认证正文只通过 credentialRef 在派发前读取。
 */
data class HttpToolConnection(
    val url: String,
    val method: String = "GET",
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val authMode: String = "None",
    val authHeader: String = "Authorization",
    val credentialRef: String? = null,
    val timeoutMillis: Long = 30_000,
    val maxResponseBytes: Int = MAX_RESPONSE_BYTES,
    val allowPrivateAddresses: Boolean = false,
) {
    companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

/** 绑定仅能写入查询参数或 JSON 请求体，不接受模型指定的主机、路径或认证头。 */
data class HttpToolBinding(val argument: String, val target: String, val path: String)

/** 响应映射使用 JSON Pointer，数组保持顺序且支持列表字段投影。 */
data class HttpToolMapping(
    val body: JsonObject = JsonObject(),
    val bindings: List<HttpToolBinding> = emptyList(),
    val responsePath: String = "",
    val errorPath: String? = null,
    val nextCursorPath: String? = null,
    val fields: Map<String, String> = emptyMap(),
)

/**
 * 工具配置校验失败的结构化异常，保留固定错误码供页面映射到具体本地化文案。
 */
class ToolConfigValidationException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)

/** 校验配置约束并在失败时抛出带稳定错误码的异常。 */
internal fun requireConfig(condition: Boolean, code: String, message: String) {
    if (!condition) throw ToolConfigValidationException(code, message)
}

/**
 * 手工 JSON 编解码配置，不依赖 Gson 反射，混淆前后字段保持稳定。
 * - 未知字段、认证头明文和过量配置均在保存与导入边界拒绝。
 * - 支持 Header 与 Query 两种加密凭据注入方式，并对分页参数放行非凭据 token。
 * - 程序字段使用英文，错误由页面映射为资源文案。
 */
object HttpToolConfigCodec {
    /**
     * 解码并验证连接配置；空 URL 仅允许用于尚未绑定的搜索模板。
     *
     * @param raw 待严格解析的 JSON 或地址文本。
     * @return 通过安全和体积校验的连接。
     */
    fun connection(raw: String): HttpToolConnection {
        // 解析连接 JSON 并拦截未知配置字段。
        val value = objectValue(raw)
        known(
            value,
            setOf(
                "url",
                "method",
                "query",
                "headers",
                "authMode",
                "authHeader",
                "credentialRef",
                "timeoutMillis",
                "maxResponseBytes",
                "allowPrivateAddresses"
            )
        )
        // 读取强类型字段，拒绝将字符串隐式转换成布尔或整数。
        val result = HttpToolConnection(
            url = value["url"]?.asString.orEmpty(),
            method = value["method"]?.asString ?: "GET",
            query = strings(value, "query"),
            headers = strings(value, "headers"),
            authMode = value["authMode"]?.asString ?: "None",
            authHeader = value["authHeader"]?.asString ?: "Authorization",
            credentialRef = value["credentialRef"]?.takeUnless { it.isJsonNull }?.asString,
            timeoutMillis = value["timeoutMillis"]?.asBigDecimal?.longValueExact() ?: 30_000,
            maxResponseBytes = value["maxResponseBytes"]?.asBigDecimal?.intValueExact()
                ?: HttpToolConnection.MAX_RESPONSE_BYTES,
            allowPrivateAddresses = value["allowPrivateAddresses"]?.let {
                requireConfig(
                    it.isJsonPrimitive && it.asJsonPrimitive.isBoolean,
                    "invalid_limits",
                    "Invalid private address setting",
                )
                it.asBoolean
            } ?: false,
        )
        // 校验方法、认证模式与限额，凭据必须走加密仓库且不能明文写入静态配置。
        requireConfig(
            result.method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"),
            "invalid_method",
            "Invalid HTTP method",
        )
        requireConfig(
            result.authMode in setOf("None", "Bearer", "ApiKey", "Header", "Query"),
            "invalid_auth",
            "Invalid HTTP authentication",
        )
        requireConfig(
            result.timeoutMillis in 1000..120_000 &&
                result.maxResponseBytes in 1..HttpToolConnection.MAX_RESPONSE_BYTES,
            "invalid_limits",
            "Invalid HTTP limits",
        )
        requireConfig(
            result.credentialRef == null || result.credentialRef.matches(Regex("[0-9a-f-]{36}")),
            "invalid_auth",
            "Invalid credential reference",
        )
        requireConfig(
            result.headers.keys.none(::reservedHeader) &&
                result.query.keys.none(::sensitiveQueryName),
            "credential_in_plain_config",
            "Credentials require encrypted storage",
        )
        requireConfig(
            result.authHeader.matches(Regex("[A-Za-z0-9_-]{1,100}")) &&
                (result.authMode == "Query" || result.authHeader.lowercase() !in TRANSPORT_HEADERS),
            "invalid_auth",
            "Invalid authentication header",
        )
        // 验证静态请求头格式并校验非空 URL 的协议与敏感参数约束。
        try {
            Headers.Builder().apply { result.headers.forEach { (name, text) -> add(name, text) } }
                .build()
        } catch (error: IllegalArgumentException) {
            throw ToolConfigValidationException("invalid_headers", error.message ?: "Invalid HTTP headers")
        }
        if (result.url.isNotBlank()) {
            val url = try {
                ToolNetworkPolicy.url(result.url)
            } catch (error: ToolHttpException) {
                throw ToolConfigValidationException("invalid_url", error.message ?: "Invalid HTTP URL")
            }
            requireConfig(
                url.queryParameterNames.none(::sensitiveQueryName),
                "credential_in_plain_config",
                "Credentials require encrypted storage",
            )
            requireConfig(
                url.fragment == null,
                "invalid_url",
                "URL fragment is not supported",
            )
        }
        return result
    }

    /**
     * 输出可导出连接，始终只包含凭据引用。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @return 使用稳定字段名输出的 JSON。
     */
    fun encode(value: HttpToolConnection): String = JsonObject().apply {
        // 凭据字段仅输出引用，不提供任何明文认证入口。
        addProperty("url", value.url)
        addProperty("method", value.method)
        add("query", stringObject(value.query))
        add("headers", stringObject(value.headers))
        addProperty("authMode", value.authMode)
        addProperty("authHeader", value.authHeader)
        value.credentialRef?.let { addProperty("credentialRef", it) }
        addProperty("timeoutMillis", value.timeoutMillis)
        addProperty("maxResponseBytes", value.maxResponseBytes)
        addProperty("allowPrivateAddresses", value.allowPrivateAddresses)
    }.toString()

    /**
     * 解码结构化请求绑定与响应路径，不执行脚本或字符串模板。
     *
     * @param raw 待严格解析的 JSON 或地址文本。
     * @return 通过路径和目标限制校验的映射。
     */
    fun mapping(raw: String): HttpToolMapping {
        // 解析映射对象并验证允许的顶层配置键。
        val value = objectValue(raw)
        known(
            value,
            setOf("body", "bindings", "responsePath", "errorPath", "nextCursorPath", "fields")
        )
        // 读取参数绑定列表并校验每个绑定项的必填字段。
        val bindings = value.getAsJsonArray("bindings")?.map { item ->
            val binding = item.asJsonObject
            known(binding, setOf("argument", "target", "path"))
            HttpToolBinding(
                argument = binding["argument"]?.asString
                    ?: throw ToolConfigValidationException("invalid_mapping", "Missing binding argument"),
                target = binding["target"]?.asString
                    ?: throw ToolConfigValidationException("invalid_mapping", "Missing binding target"),
                path = binding["path"]?.asString
                    ?: throw ToolConfigValidationException("invalid_mapping", "Missing binding path"),
            )
        }.orEmpty()
        val result = HttpToolMapping(
            body = value.getAsJsonObject("body") ?: JsonObject(),
            bindings = bindings,
            responsePath = value["responsePath"]?.asString.orEmpty(),
            errorPath = value["errorPath"]?.takeUnless { it.isJsonNull }?.asString,
            nextCursorPath = value["nextCursorPath"]?.takeUnless { it.isJsonNull }?.asString,
            fields = strings(value, "fields"),
        )
        // 路径预先编译校验，配置错误不留到实际外部派发之后才发现。
        requireConfig(
            bindings.size <= 100 && result.fields.size <= 32,
            "invalid_mapping",
            "Too many HTTP mappings",
        )
        bindings.forEach { binding ->
            requireConfig(
                binding.target in setOf("query", "body"),
                "invalid_mapping",
                "Invalid HTTP binding target",
            )
            validatePointer(binding.argument)
            if (binding.target == "body") {
                requireConfig(
                    validatePointer(binding.path).isNotEmpty(),
                    "invalid_mapping",
                    "Cannot replace request root",
                )
            } else {
                requireConfig(
                    binding.path.isNotBlank(),
                    "invalid_mapping",
                    "Query binding path cannot be empty",
                )
                requireConfig(
                    !sensitiveQueryName(binding.path),
                    "credential_in_plain_config",
                    "Cannot bind authentication query",
                )
            }
        }
        requireConfig(
            bindings.map { it.target to it.path }.distinct().size == bindings.size,
            "invalid_mapping",
            "Duplicate HTTP binding",
        )
        (listOfNotNull(
            result.responsePath,
            result.errorPath,
            result.nextCursorPath
        ) + result.fields.values).forEach(::validatePointer)
        return result
    }

    /**
     * 导出响应映射与绑定，字段顺序不影响运行指纹。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @return 使用稳定字段名输出的 JSON。
     */
    fun encode(value: HttpToolMapping): String = JsonObject().apply {
        // 每个绑定保留独立目标和路径，不把 JSON 降为字符串模板。
        add("body", value.body.deepCopy())
        add("bindings", JsonArray().apply {
            value.bindings.forEach { binding ->
                add(JsonObject().apply {
                    addProperty("argument", binding.argument)
                    addProperty("target", binding.target)
                    addProperty("path", binding.path)
                })
            }
        })
        addProperty("responsePath", value.responsePath)
        value.errorPath?.let { addProperty("errorPath", it) }
        value.nextCursorPath?.let { addProperty("nextCursorPath", it) }
        add("fields", stringObject(value.fields))
    }.toString()

    private fun objectValue(raw: String): JsonObject = try {
        val parsed = AgentJson.parse(raw, maxDepth = 24)
        requireConfig(parsed.isJsonObject, "invalid_json", "Expected JSON object")
        parsed.asJsonObject
    } catch (error: ToolConfigValidationException) {
        throw error
    } catch (error: Exception) {
        throw ToolConfigValidationException("invalid_json", error.message ?: "Invalid JSON")
    }

    private fun validatePointer(pointer: String): List<String> = try {
        ToolJsonPointer.segments(pointer)
    } catch (error: IllegalArgumentException) {
        throw ToolConfigValidationException("invalid_mapping", error.message ?: "Invalid JSON pointer")
    }

    private fun strings(value: JsonObject, key: String): Map<String, String> =
        value.getAsJsonObject(key)?.entrySet()?.associate { (name, text) ->
            requireConfig(
                text.isJsonPrimitive && text.asJsonPrimitive.isString,
                "invalid_json",
                "Expected string mapping",
            )
            name to text.asString
        }.orEmpty()

    private fun stringObject(values: Map<String, String>) = JsonObject().apply {
        values.forEach { (name, value) -> addProperty(name, value) }
    }

    private fun known(value: JsonObject, names: Set<String>) {
        requireConfig(
            value.keySet().all { it in names },
            "invalid_json",
            "Unknown HTTP configuration field",
        )
    }

    /** 校验查询参数名是否属于敏感凭据字段，但对常见分页游标参数放行。 */
    private fun sensitiveQueryName(name: String): Boolean {
        val normalized = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (normalized in PAGINATION_QUERY_TOKENS) return false
        return sensitiveNormalized(normalized)
    }

    /** 校验请求头名是否属于传输保留头、Cookie 或敏感认证字段。 */
    private fun reservedHeader(name: String): Boolean {
        val normalized = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        return name.lowercase() in TRANSPORT_HEADERS ||
            sensitiveNormalized(normalized) ||
            name.contains("cookie", ignoreCase = true)
    }

    private fun sensitiveNormalized(normalized: String): Boolean =
        normalized.contains("token") ||
            normalized.contains("secret") ||
            normalized.contains("password") ||
            normalized.contains("apikey") ||
            normalized.contains("authorization") ||
            normalized.contains("credential") ||
            normalized.endsWith("auth") ||
            normalized == "authentication" ||
            normalized == "key"

    private val PAGINATION_QUERY_TOKENS = setOf(
        "pagetoken",
        "nextpagetoken",
        "prevpagetoken",
        "previouspagetoken",
        "cursortoken",
        "continuationtoken",
        "nexttoken",
        "scrolltoken",
    )

    private val TRANSPORT_HEADERS = setOf(
        "host",
        "connection",
        "content-length",
        "transfer-encoding",
        "accept-encoding",
        "proxy-authorization",
        "proxy-connection",
        "upgrade",
        "trailer",
        "te"
    )
}
