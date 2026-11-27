package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.ExternalRequestPlan
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition

/**
 * 无会话身份的 HTTP 调用器，运行工具与手工测试共用请求规划及结果映射。
 * - 凭据正文仅在执行闭包内读取。
 * - 公网读取始终隔离认证与私网许可，不借用模型 Provider 权限。
 */
class HttpToolInvoker(
    private val mCredentials: ToolCredentialRepository,
    private val mHttp: ToolHttpEngine,
    private val mHtml: HtmlTextExtractor,
) {
    /** @return 固定目标与独立参数快照，规划本身不产生网络 IO。 */
    fun plan(
        tool: ToolDefinition,
        connection: ToolConnection?,
        arguments: JsonObject
    ): ExternalRequestPlan {
        // 先解析并验证目标主机，再深拷贝入参构造延后执行闭包。
        val config = connection?.let { HttpToolConfigCodec.connection(it.configJson) }
        val url =
            if (tool.kind == HttpToolKind.ReadUrl) arguments["url"].asString else config?.url.orEmpty()
        if (url.isBlank()) throw ToolPreparationException(ToolErrorCode.HTTP_NEEDS_CONFIGURATION)
        val host = try {
            ToolNetworkPolicy.url(url).host
        } catch (error: ToolHttpException) {
            throw ToolPreparationException(error.code)
        }
        val frozen = arguments.deepCopy()
        return ExternalRequestPlan(host, frozen.deepCopy()) { execute(tool, connection, frozen) }
    }

    private suspend fun execute(
        tool: ToolDefinition,
        connection: ToolConnection?,
        arguments: JsonObject,
    ): ToolResult {
        try {
            val offset = arguments["page_offset"]?.asInt ?: 0
            // read_url 永远创建无认证公网连接，不能继承局域网白名单或 Provider 密钥。
            if (tool.kind == HttpToolKind.ReadUrl) {
                val payload = mHttp.execute(
                    config = HttpToolConnection(arguments["url"].asString),
                    query = emptyMap(),
                    body = null,
                    readUrl = true,
                )
                val text = if (payload.contentType in setOf("text/plain", "text/markdown")) {
                    payload.text
                } else {
                    mHtml.extract(payload.text)
                }
                return HttpToolResultCodec.text(text, offset)
            }
            // 读取绑定的连接配置与本机加密凭据，按结构化映射拼装 Query 与 JSON 请求体。
            val config = connection?.let { HttpToolConfigCodec.connection(it.configJson) }
                ?: return ToolResult.RecoverableError(ToolErrorCode.HTTP_NEEDS_CONFIGURATION)
            if (config.url.isBlank()) return ToolResult.RecoverableError(ToolErrorCode.HTTP_NEEDS_CONFIGURATION)
            val secret = config.credentialRef?.let { mCredentials.read(it) }
            val mapping = HttpToolConfigCodec.mapping(tool.mappingJson)
            val body = mapping.body.deepCopy()
            val query = mutableMapOf<String, String>()
            mapping.bindings.forEach { binding ->
                val value = ToolJsonPointer.read(arguments, binding.argument) ?: return@forEach
                if (binding.target == ToolBindingTarget.Body) {
                    ToolJsonPointer.assign(body, binding.path, value)
                } else if (!value.isJsonNull) {
                    require(value.isJsonPrimitive) { "Query binding must be a scalar" }
                    query[binding.path] = value.asString
                }
            }
            // DELETE 在未配置静态请求体与 body 绑定时不主动附带空 JSON，避免被严格服务端拒绝。
            val hasExplicitBody =
                mapping.body.size() > 0 || mapping.bindings.any { it.target == ToolBindingTarget.Body }
            val bodyPayload = when {
                config.method in ToolHttpMethod.requiresBody -> body.toString()
                config.method == ToolHttpMethod.Delete && hasExplicitBody -> body.toString()
                else -> null
            }
            val payload = mHttp.execute(config, query, bodyPayload, secret)
            return HttpToolResultCodec.map(payload, mapping, offset)
        } catch (error: CancellationException) {
            throw error
        } catch (error: ToolHttpException) {
            return ToolResult.RecoverableError(error.code)
        } catch (_: Exception) {
            return ToolResult.RecoverableError(ToolErrorCode.HTTP_INVALID_MAPPING)
        }
    }

}
