package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonObject
import java.net.InetAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideCodec
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideData
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolMapping
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpPayload
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolResultCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigValidationException
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpException
import me.kafuuneko.rpclient.libs.agent.connection.ToolJsonPointer
import me.kafuuneko.rpclient.libs.agent.connection.ToolNetworkPolicy
import me.kafuuneko.rpclient.libs.agent.connection.ToolSafeDns
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolResultCodec
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaCodec
import me.kafuuneko.rpclient.libs.agent.tool.builtin.BuiltinAgentTools
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 公网地址、结构化映射与分页边界的算法回归，不验证简单字段传递。 */
class HttpToolBoundaryTest {
    @Test
    fun rejectsPrivateMappedAndReservedAddressesButAllowsGlobalUnicast() {
        // IPv4 映射、转换和隧道地址也不能把内网目标伪装成公共 IPv6。
        val blocked = listOf(
            "0.0.0.0",
            "10.1.2.3",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.169.254",
            "172.16.1.1",
            "192.168.1.1",
            "192.0.2.1",
            "198.19.1.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "255.255.255.255",
            "::",
            "::1",
            "fc00::1",
            "fe80::1",
            "::ffff:127.0.0.1",
            "64:ff9b::a00:1",
            "2001:db8::1",
            "2002:a00:1::1"
        )
        blocked.forEach { assertFalse(it, ToolNetworkPolicy.isPublic(InetAddress.getByName(it))) }
        listOf("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111").forEach {
            assertTrue(it, ToolNetworkPolicy.isPublic(InetAddress.getByName(it)))
        }
    }

    @Test
    fun dnsRejectsEntireMixedAnswerAndDoesNotReusePreflightResolution() {
        // 第二次解析模拟重绑定，不能复用第一次的安全结论。
        var count = 0
        val delegate = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                count++
                return if (count == 1) listOf(InetAddress.getByName("8.8.8.8"))
                else listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"))
            }
        }
        val guarded = ToolSafeDns(false, delegate)
        assertEquals(1, guarded.lookup("fixture.invalid").size)
        assertThrows(ToolHttpException::class.java) { guarded.lookup("fixture.invalid") }
        assertEquals(2, ToolSafeDns(true, delegate).lookup("fixture.invalid").size)
    }

    @Test
    fun rejectsNonHttpSchemesEmbeddedCredentialsAndUnencryptedAuthentication() {
        // 配置和模型 URL 同时受限，认证正文只能通过加密引用注入。
        listOf(
            "file:///etc/hosts",
            "ftp://example.com",
            "https://user:secret@example.com"
        ).forEach { url ->
            assertThrows(ToolHttpException::class.java) { ToolNetworkPolicy.url(url) }
        }
        listOf(
            """{"url":"https://example.com?api_key=secret"}""",
            """{"headers":{"Authorization":"Bearer secret"}}""",
            """{"headers":{"Host":"other.example"}}""",
            """{"query":{"access_token":"secret"}}""",
            """{"allowPrivateAddresses":"true"}""",
            """{"maxResponseBytes":2097153}""",
        ).forEach { raw -> assertThrows(Exception::class.java) { HttpToolConfigCodec.connection(raw) } }
        assertThrows(Exception::class.java) { HttpToolConfigCodec.mapping("""{"bindings":[{"argument":"/url","target":"url","path":"host"}]}""") }
    }

    @Test
    fun pointersKeepLiteralValuesNullAndEscapedKeysWithoutExpandingMacros() {
        // 路径转义和字面内容必须分别处理，不能把工具输入当作 Prompt 模板。
        val target = AgentJson.parse("""{"list":[{"a/b":0}]}""").asJsonObject
        val input = AgentJson.parse("""{"value":"{{user}}","null":null}""")
        ToolJsonPointer.assign(
            target,
            "/list/0/a~1b",
            requireNotNull(ToolJsonPointer.read(input, "/value"))
        )
        assertEquals("{{user}}", ToolJsonPointer.read(target, "/list/0/a~1b")?.asString)
        assertTrue(requireNotNull(ToolJsonPointer.read(input, "/null")).isJsonNull)
        assertEquals(null, ToolJsonPointer.read(input, "/missing"))
        assertThrows(Exception::class.java) { ToolJsonPointer.segments("/a~2b") }
        assertThrows(Exception::class.java) {
            ToolJsonPointer.assign(
                target,
                "/list/3",
                JsonObject()
            )
        }
    }

    @Test
    fun resultPaginationPreservesWholeItemsAndReportsBothCursors() {
        // 本地分页与远端游标同时存在时，两者均需保留且不泄露未投影字段。
        val source = jsonObject("results" to jsonArray((0..24).map {
            jsonObject(
                "title" to "Result $it",
                "url" to "https://example.com/$it",
                "secret" to "omit"
            )
        }), "next" to "remote")
        val mapping = HttpToolMapping(
            responsePath = "/results",
            fields = mapOf("title" to "/title", "url" to "/url"),
            nextCursorPath = "/next"
        )
        val payload = ToolHttpPayload(source.toString(), "application/json")
        val first = HttpToolResultCodec.map(payload, mapping, 0) as ToolResult.Success
        assertEquals(20, first.data.getAsJsonArray("items").size())
        assertEquals("20", first.nextCursor)
        assertEquals("remote", first.data["remoteCursor"].asString)
        assertTrue(first.data["untrusted"].asBoolean)
        assertFalse(ToolResultCodec.encode(first).toString().contains("omit"))
        val last = HttpToolResultCodec.map(payload, mapping, 20) as ToolResult.Success
        assertEquals(5, last.data.getAsJsonArray("items").size())
        assertFalse(last.truncated)
        ToolResultCodec.encode(last)
    }

    @Test
    fun oversizedItemsAndErrorPathsNeverProduceBrokenJsonOrRawErrors() {
        // 大条目和业务错误均返回固定错误码，不通过截断原始响应来容错。
        val oversized =
            ToolHttpPayload(jsonObject("value" to "x".repeat(9000)).toString(), "application/json")
        assertEquals(
            ToolResult.RecoverableError("http_item_too_large"),
            HttpToolResultCodec.map(oversized, HttpToolMapping(), 0)
        )
        val remote = ToolHttpPayload("""{"error":{"token":"private"}}""", "application/json")
        assertEquals(
            ToolResult.RecoverableError("http_remote_error"),
            HttpToolResultCodec.map(remote, HttpToolMapping(errorPath = "/error"), 0)
        )
        val cursor = ToolHttpPayload(
            jsonObject(
                "results" to jsonArray(
                    listOf(
                        jsonObject(
                            "text" to "x".repeat(5000)
                        )
                    )
                ), "next" to "c".repeat(2000)
            ).toString(), "application/json"
        )
        ToolResultCodec.encode(
            HttpToolResultCodec.map(
                cursor,
                HttpToolMapping(responsePath = "/results", nextCursorPath = "/next"),
                0
            )
        )
        val text = HttpToolResultCodec.text("x".repeat(20_000), 0) as ToolResult.Success
        assertTrue(text.truncated)
        ToolResultCodec.encode(text)
        assertEquals(
            ToolResult.RecoverableError("http_invalid_cursor"),
            HttpToolResultCodec.text("short", 50)
        )
    }

    @Test
    fun userSchemaCannotSilentlyWeakenTypesOrDropUnsupportedRules() {
        // 编辑器必须明确拒绝不支持的关键字，不能删除约束后继续运行。
        listOf(
            """{"type":"object","oneOf":[]}""",
            """{"type":"object","additionalProperties":"false"}""",
            """{"type":"string","minLength":1.5}""",
            """{"type":"array"}""",
            """{"type":"object","required":["missing"]}""",
        ).forEach { raw -> assertThrows(Exception::class.java) { ToolSchemaCodec.decode(raw) } }
    }

    @Test
    fun httpConfigurationRejectsSchemaNodeOverflowAfterAddingPagination() {
        // 原始 Schema 恰好占满节点预算，补入客户端分页参数后必须在保存前拒绝。
        val properties = JsonObject().apply {
            repeat(1023) { add("arg$it", jsonObject("type" to "string")) }
        }
        val value = jsonObject(
            "name" to "fixture_http",
            "parameters" to jsonObject("type" to "object", "properties" to properties),
        )
        ToolSchemaCodec.decode(value["parameters"].toString())
        assertThrows(Exception::class.java) {
            ToolConfigCodec.readTool(value, UUID.randomUUID().toString())
        }
        value.getAsJsonObject("parameters").getAsJsonObject("properties").remove("arg1022")
        ToolConfigCodec.readTool(value, UUID.randomUUID().toString())
    }

    @Test
    fun httpConfigurationRejectsOversizedCompleteRuntimeSnapshot() {
        // 单独 Schema 未超限，但说明与冻结元数据合并后不能超过运行时快照上限。
        val value = jsonObject(
            "name" to "fixture_http",
            "description" to "d".repeat(8000),
            "parameters" to jsonObject(
                "type" to "object",
                "properties" to jsonObject(
                    "query" to jsonObject("type" to "string", "description" to "s".repeat(58_000))
                ),
            ),
        )
        ToolSchemaCodec.decode(value["parameters"].toString())
        assertThrows(Exception::class.java) {
            ToolConfigCodec.readTool(value, UUID.randomUUID().toString())
        }
    }

    @Test
    fun paginationQueryTokensPassWhileCredentialsAndAuthHeadersRemainBlocked() {
        // 分页令牌参数允许放入查询与绑定，但认证密钥和保留请求头仍必须拦截。
        val connection = HttpToolConfigCodec.connection(
            """{"url":"https://example.com/search","query":{"pageToken":"p1","next_page_token":"p2"},"authMode":"Query","authHeader":"key"}"""
        )
        assertEquals("Query", connection.authMode)
        assertEquals("key", connection.authHeader)
        val mapping = HttpToolConfigCodec.mapping(
            """{"bindings":[{"argument":"/cursor","target":"query","path":"nextPageToken"}]}"""
        )
        assertEquals("nextPageToken", mapping.bindings.single().path)
        assertThrows(ToolConfigValidationException::class.java) {
            HttpToolConfigCodec.connection("""{"url":"https://example.com","headers":{"X-Page-Token":"secret"}}""")
        }
        assertThrows(ToolConfigValidationException::class.java) {
            HttpToolConfigCodec.connection("""{"url":"https://example.com","authMode":"Query","authHeader":""}""")
        }
    }

    @Test
    fun resultProjectionSkipsMissingOptionalFieldsButFailsWhenNoFieldMatches() {
        // 单条记录缺少可选字段时跳过该字段；全部字段都未命中时才判定映射失败。
        val partial = ToolHttpPayload(
            """{"items":[{"title":"Only title"}]}""",
            "application/json",
        )
        val mapping = HttpToolMapping(
            responsePath = "/items",
            fields = mapOf("title" to "/title", "snippet" to "/snippet"),
        )
        val success = HttpToolResultCodec.map(partial, mapping, 0) as ToolResult.Success
        val item = success.data.getAsJsonArray("items")[0].asJsonObject
        assertEquals("Only title", item["title"].asString)
        assertFalse(item.has("snippet"))

        val unmatched = ToolHttpPayload("""{"items":[{"other":"value"}]}""", "application/json")
        assertEquals(
            ToolResult.RecoverableError("http_mapping_missing"),
            HttpToolResultCodec.map(unmatched, mapping, 0),
        )
    }

    @Test
    fun builtinOverrideDelegatesEnabledPreservesBlankDescriptionAndValidatesSchema() = runBlocking {
        // 内置覆盖必须透传 isEnabled、保留空白说明回退并校验默认参数是否属于目标 Schema。
        val rollDiceSchema = requireNotNull(BuiltinAgentTools.PARAMETER_SCHEMAS["roll_dice"])
        val disabledTool = object : AgentTool {
            override val definition = AgentToolDefinition(
                toolId = "roll_dice",
                name = "roll_dice",
                contractVersion = 1,
                description = "Default dice description",
                parameters = rollDiceSchema,
                effect = ToolEffect.NonDeterministicRead,
            )

            override suspend fun isEnabled(): Boolean = false

            override suspend fun prepare(
                context: ToolExecutionContext,
                arguments: JsonObject,
            ): ToolAction = ToolAction.Read { ToolResult.Success(JsonObject()) }
        }
        val rawOverride = BuiltinToolOverrideCodec.encode(
            BuiltinToolOverrideData(displayName = "Dice", description = "   ")
        )
        val wrapped = BuiltinToolOverrideCodec.apply(disabledTool, rawOverride)
        assertEquals("Default dice description", wrapped.definition.description)
        assertFalse(wrapped.isEnabled())
        assertTrue(
            BuiltinToolOverrideCodec.isEmpty(
                ToolConfigCodec.parse(BuiltinToolOverrideCodec.encode(BuiltinToolOverrideData()))
            )
        )
        assertThrows(ToolConfigValidationException::class.java) {
            BuiltinToolOverrideCodec.validateForSchema(
                "roll_dice",
                rollDiceSchema,
                ToolConfigCodec.parse(
                    BuiltinToolOverrideCodec.encode(
                        BuiltinToolOverrideData(defaults = mapOf("limit" to 10))
                    )
                ),
            )
        }
        assertThrows(ToolConfigValidationException::class.java) {
            BuiltinToolOverrideCodec.validateForSchema(
                "roll_dice",
                rollDiceSchema,
                ToolConfigCodec.parse(
                    BuiltinToolOverrideCodec.encode(
                        BuiltinToolOverrideData(parameterDescriptions = mapOf("nonexistent" to "hint"))
                    )
                ),
            )
        }
        Unit
    }

    @Test
    fun toolBindingValidationRejectsGetBodyBindingsAndUnknownArguments() {
        // GET/HEAD 不允许写入 body，且绑定参数名必须存在于 Schema properties 中。
        val toolWithBodyBinding = ToolConfigCodec.readTool(
            jsonObject(
                "name" to "search_api",
                "parameters" to jsonObject(
                    "type" to "object",
                    "properties" to jsonObject("query" to jsonObject("type" to "string")),
                ),
                "mapping" to jsonObject(
                    "bindings" to jsonArray(
                        listOf(
                            jsonObject(
                                "argument" to "/query",
                                "target" to "body",
                                "path" to "/q",
                            )
                        )
                    )
                ),
            ),
            UUID.randomUUID().toString(),
        )
        assertThrows(ToolConfigValidationException::class.java) {
            ToolConfigCodec.validateBindings(toolWithBodyBinding, "GET")
        }
        ToolConfigCodec.validateBindings(toolWithBodyBinding, "POST")

        val unknownArgTool = ToolConfigCodec.readTool(
            jsonObject(
                "name" to "unknown_arg_api",
                "parameters" to jsonObject(
                    "type" to "object",
                    "properties" to jsonObject("query" to jsonObject("type" to "string")),
                ),
                "mapping" to jsonObject(
                    "bindings" to jsonArray(
                        listOf(
                            jsonObject(
                                "argument" to "/missing",
                                "target" to "query",
                                "path" to "q",
                            )
                        )
                    )
                ),
            ),
            UUID.randomUUID().toString(),
        )
        assertThrows(ToolConfigValidationException::class.java) {
            ToolConfigCodec.validateBindings(unknownArgTool, "GET")
        }
    }
}
