package me.kafuuneko.rpclient.libs.agent.connection

import android.text.Html
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaValidator
import me.kafuuneko.rpclient.libs.agent.tool.builtin.BuiltinAgentTools
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository

/**
 * 从权威配置创建冻结处理器，五协议和聊天入口无需认识具体 HTTP 工具名称。
 * - 连接和映射参与契约指纹，凭据正文只在派发闭包内读取。
 * - 每次实际派发再次检查全局启停，等待确认不能绕开配置撤权。
 */
class HttpAgentTools(
    private val mConfigurations: ToolConfigRepository,
    private val mCredentials: ToolCredentialRepository,
    private val mHttp: ToolHttpEngine,
    private val mBuiltins: BuiltinAgentTools,
) {
    /**
     * 捕获当前工具定义；所有集合均属于本次快照。
     *
     * @return 包含内置覆盖和 HTTP 工具的冻结处理器集合。
     */
    suspend fun create(): List<AgentTool> {
        val overrides = mConfigurations.overrides().associate { it.toolId to it.configJson }
        val local = mBuiltins.create()
            .map { tool -> BuiltinToolOverrideCodec.apply(tool, overrides[tool.definition.toolId]) }
        val connections = mConfigurations.connections().associateBy { it.id }
        return local + mConfigurations.tools().filter { it.enabled }.map { tool ->
            handler(tool, tool.connectionId?.let(connections::get))
        }
    }

    /** 返回所有固定内置工具的原始契约，供配置页面展示原始说明与参数定义。 */
    fun builtinDefinitions(): List<AgentToolDefinition> = mBuiltins.create().map { it.definition }

    /**
     * 为编辑器验证内置覆盖，类型、模型函数名及执行性质保持固定。
     *
     * @param id 客户端持有的稳定身份。
     * @param raw 待严格解析的 JSON 或地址文本。
     */
    fun validateOverride(id: String, raw: String) {
        val tool = mBuiltins.create().firstOrNull { it.definition.toolId == id }
            ?: throw ToolConfigValidationException("invalid_override", "Unknown built-in tool")
        BuiltinToolOverrideCodec.apply(tool, raw)
    }

    /**
     * 测试由用户明确点击触发，运行完整 Schema 和网络安全边界，不创建聊天运行。
     *
     * @param id 客户端持有的稳定身份。
     * @param arguments 待执行的 JSON 参数，不包含客户端授权身份。
     * @return 有界测试结果，不包含底层请求头。
     */
    suspend fun test(id: String, arguments: JsonObject): ToolResult {
        val tool = mConfigurations.tool(id)
            ?: throw ToolConfigValidationException("invalid_tool", "Tool is missing")
        val connection = tool.connectionId?.let { mConfigurations.connection(it) }
        requireConfig(
            ToolSchemaValidator.validate(
                ToolConfigCodec.definition(tool, connection).parameters,
                arguments,
            ).isEmpty(),
            "invalid_test_arguments",
            "Invalid test arguments",
        )
        return execute(tool, connection, arguments)
    }

    private fun handler(tool: ToolDefinition, connection: ToolConnection?): AgentTool =
        object : AgentTool {
            override val definition = ToolConfigCodec.definition(tool, connection)

            /** 停用或删除配置后拒绝旧结果回放及新派发。 */
            override suspend fun isEnabled(): Boolean = mConfigurations.permitted(tool, connection)

            /**
             * 只准备固定目标与参数摘要，真正的 HTTP 调用等待执行器授权。
             *
             * @param arguments 待执行的 JSON 参数，不包含客户端授权身份。
             */
            override suspend fun prepare(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolAction {
                if (!mConfigurations.permitted(
                        tool,
                        connection
                    )
                ) throw ToolPreparationException("http_needs_configuration")
                val config = connection?.let { HttpToolConfigCodec.connection(it.configJson) }
                if (config != null && config.authMode != "None" && config.credentialRef?.let {
                        mCredentials.read(
                            it
                        )
                    } == null) throw ToolPreparationException("http_needs_configuration")
                val url =
                    if (tool.kind == "ReadUrl") arguments["url"].asString else config?.url.orEmpty()
                if (url.isBlank()) throw ToolPreparationException("http_needs_configuration")
                val host = try {
                    ToolNetworkPolicy.url(url).host
                } catch (error: ToolHttpException) {
                    throw ToolPreparationException(error.code)
                }
                val summary = arguments.deepCopy()
                return if (definition.effect == ToolEffect.ExternalWrite) {
                    ToolAction.ExternalWrite(host, summary) { execute(tool, connection, arguments) }
                } else {
                    ToolAction.ExternalRead(host, summary) { execute(tool, connection, arguments) }
                }
            }
        }

    private suspend fun execute(
        tool: ToolDefinition,
        connection: ToolConnection?,
        arguments: JsonObject,
    ): ToolResult {
        if (!mConfigurations.permitted(tool, connection)) {
            return ToolResult.RecoverableError("http_needs_configuration")
        }
        try {
            val offset = arguments["page_offset"]?.asInt ?: 0
            // read_url 永远创建无认证公网连接，不能继承局域网白名单或 Provider 密钥。
            if (tool.kind == "ReadUrl") {
                val payload = mHttp.execute(
                    config = HttpToolConnection(arguments["url"].asString),
                    query = emptyMap(),
                    body = null,
                    readUrl = true,
                )
                val text = if (payload.contentType in setOf("text/plain", "text/markdown")) {
                    payload.text
                } else {
                    extractReadableHtmlText(payload.text)
                }
                return HttpToolResultCodec.text(text, offset)
            }
            // 读取绑定的连接配置与本机加密凭据，按结构化映射拼装 Query 与 JSON 请求体。
            val config = connection?.let { HttpToolConfigCodec.connection(it.configJson) }
                ?: return ToolResult.RecoverableError("http_needs_configuration")
            if (config.url.isBlank()) return ToolResult.RecoverableError("http_needs_configuration")
            val secret = config.credentialRef?.let { mCredentials.read(it) }
            val mapping = HttpToolConfigCodec.mapping(tool.mappingJson)
            val body = mapping.body.deepCopy()
            val query = mutableMapOf<String, String>()
            mapping.bindings.forEach { binding ->
                val value = ToolJsonPointer.read(arguments, binding.argument) ?: return@forEach
                if (binding.target == "body") {
                    ToolJsonPointer.assign(body, binding.path, value)
                } else if (!value.isJsonNull) {
                    require(value.isJsonPrimitive) { "Query binding must be a scalar" }
                    query[binding.path] = value.asString
                }
            }
            // DELETE 在未配置静态请求体与 body 绑定时不主动附带空 JSON，避免被严格服务端拒绝。
            val hasExplicitBody = mapping.body.size() > 0 || mapping.bindings.any { it.target == "body" }
            val bodyPayload = when {
                config.method in setOf("POST", "PUT", "PATCH") -> body.toString()
                config.method == "DELETE" && hasExplicitBody -> body.toString()
                else -> null
            }
            val payload = mHttp.execute(config, query, bodyPayload, secret)
            return HttpToolResultCodec.map(payload, mapping, offset)
        } catch (error: CancellationException) {
            throw error
        } catch (error: ToolHttpException) {
            return ToolResult.RecoverableError(error.code)
        } catch (_: Exception) {
            return ToolResult.RecoverableError("http_invalid_mapping")
        }
    }

    /** 过滤脚本、样式与非正文节点并清理图片占位符和多余空行，提升网页正文信噪比。 */
    private fun extractReadableHtmlText(rawHtml: String): String {
        val stripped = rawHtml.replace(IGNORED_HTML_BLOCKS, "")
        val rendered = Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString()
        return rendered
            .replace("\uFFFC", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(EXCESSIVE_SPACES, " ")
            .replace(EXCESSIVE_NEWLINES, "\n\n")
            .trim()
    }

    private companion object {
        val IGNORED_HTML_BLOCKS = Regex(
            "<(script|style|head|noscript|svg|iframe|template)\\b[^>]*>.*?(?:</\\1\\s*>|\\z)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val EXCESSIVE_SPACES = Regex("[ \\t\\x0B\\f]{2,}")
        val EXCESSIVE_NEWLINES = Regex("\\n{3,}")
    }
}
