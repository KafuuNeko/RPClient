package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.SanitizingHtmlTextExtractor
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigValidationException
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolInputParser
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolResultCodec
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractAgentTool
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAuthorizationRevokedException
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailability
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailabilityState
import me.kafuuneko.rpclient.libs.agent.tool.catalog.BuiltinToolRegistrations
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EditableDefaultSpec
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolCatalog
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolGroup
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolRegistration
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolSpecification
import me.kafuuneko.rpclient.libs.prompt.tool.ToolPromptSpec
import me.kafuuneko.rpclient.utils.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆写、并发隔离、目录生命周期与历史检查点的高风险兼容回归。 */
class ToolArchitectureTest {
    private val mContext = ToolExecutionContext(
        AgentExecutionToken(ConversationRef.Single(1), "run", 0, 0, 0),
        1,
        "call",
        1,
        1
    )

    @Test
    fun builtinsPreserveBaselineSchemasDescriptionsOrderAndFingerprints() {
        // 固定基线来自改造前的完整定义投影，同时保护说明、Schema、执行性质和注册顺序。
        val expected =
            requireNotNull(javaClass.getResourceAsStream("/agent/builtin-tool-fingerprints.txt"))
                .bufferedReader().use { it.readLines() }
        val actual = BuiltinToolRegistrations.catalog.registrations.map { registration ->
            val definition = registration.specification.definition()
            definition.toolId + " " + AgentJson.digest(ToolRegistry.snapshot(definition))
        }
        assertEquals(expected, actual)
    }

    @Test
    fun displayNameNeverChangesExecutionDigestButDefaultsDo() {
        val catalog = BuiltinToolRegistrations.catalog
        val original = catalog.resolve("search_memory", null).definition
        val displayOnly =
            catalog.resolve("search_memory", """{"displayName":"Renamed"}""").definition
        assertEquals(ToolRegistry.snapshot(original), ToolRegistry.snapshot(displayOnly))
        // 非空运行覆写修改展示名也不能中断等待，而默认值变化必须改变指纹。
        val first = catalog.resolve(
            "search_memory",
            """{"displayName":"A","defaults":{"limit":3}}"""
        ).definition
        val renamed = catalog.resolve(
            "search_memory",
            """{"displayName":"B","defaults":{"limit":3}}"""
        ).definition
        val changed = catalog.resolve(
            "search_memory",
            """{"displayName":"B","defaults":{"limit":4}}"""
        ).definition
        assertEquals(ToolRegistry.snapshot(first), ToolRegistry.snapshot(renamed))
        assertNotEquals(ToolRegistry.snapshot(first), ToolRegistry.snapshot(changed))
        assertEquals(AgentJson.digest("""{"defaults":{"limit":3}}"""), first.configurationDigest)
    }

    @Test
    fun defaultsAndArgumentsRemainIsolatedAcrossConcurrentCalls() = runBlocking {
        val schema = ToolSchema(
            ToolSchema.Type.Object,
            properties = mapOf("value" to ToolSchema(ToolSchema.Type.Integer, nullable = true))
        )
        val defaults = jsonObject("value" to 7)
        val tool = object : AbstractReadTool(EffectiveToolConfig(definition(schema), defaults)) {
            override suspend fun read(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolResult {
                yield()
                return ToolResult.Success(
                    arguments.deepCopy().also { arguments.addProperty("mutated", true) })
            }
        }
        defaults.addProperty("value", 99)
        val input = listOf(
            JsonObject(),
            jsonObject("value" to 3),
            JsonObject().apply { add("value", JsonNull.INSTANCE) })
        val digests = input.map { AgentJson.digest(AgentJson.canonical(it)) }
        val results = input.map { arguments ->
            async {
                (tool.prepare(
                    mContext,
                    arguments
                ) as ToolAction.Read).execute() as ToolResult.Success
            }
        }.awaitAll()
        assertEquals(7, results[0].data["value"].asInt)
        assertEquals(3, results[1].data["value"].asInt)
        assertTrue(results[2].data["value"].isJsonNull)
        assertEquals(digests, input.map { AgentJson.digest(AgentJson.canonical(it)) })
        assertFalse(input.any { it.has("mutated") })
    }

    @Test
    fun availabilitySeparatesRevocationFromMissingConfiguration() = runBlocking {
        var availability = ToolAvailabilityState.Ready
        val tool = object : AbstractAgentTool(
            EffectiveToolConfig(definition()),
            ToolAvailability { availability }) {
            override suspend fun createAction(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolAction =
                ToolAction.Read { ToolResult.Success(arguments) }
        }
        assertTrue(tool.isEnabled())
        availability = ToolAvailabilityState.Revoked
        assertFalse(tool.isEnabled())
        assertThrows(ToolAuthorizationRevokedException::class.java) {
            runBlocking {
                tool.prepare(
                    mContext,
                    JsonObject()
                )
            }
        }
        availability = ToolAvailabilityState.NeedsConfiguration
        assertTrue(tool.isEnabled())
        val failure = assertThrows(ToolPreparationException::class.java) {
            runBlocking {
                tool.prepare(
                    mContext,
                    JsonObject()
                )
            }
        }
        assertEquals("http_needs_configuration", failure.code)
    }

    @Test
    fun legacyReadConfirmationsDecodeToExplicitKind() {
        val legacy = ToolResultCodec.encode(
            ToolResult.AwaitingUser(
                "", kind = ToolUserRequestKind.ExternalWriteConfirmation,
                details = jsonObject("externalEffect" to "read", "destinationHost" to "example.com")
            )
        )
        val decoded = ToolResultCodec.decode(legacy) as ToolResult.AwaitingUser
        assertEquals(ToolUserRequestKind.ExternalReadConfirmation, decoded.kind)
        assertFalse(decoded.details.has("externalEffect"))
        assertEquals(decoded, ToolResultCodec.decode(ToolResultCodec.encode(decoded)))
        val write =
            ToolResult.AwaitingUser("", kind = ToolUserRequestKind.ExternalWriteConfirmation)
        assertEquals(write, ToolResultCodec.decode(ToolResultCodec.encode(write)))
    }

    @Test
    fun registryRefreshesOnlyOnVersionChangesAndProtectsWaitingDigests() = runBlocking {
        var version = 0L
        var loads = 0
        var activeRuns = setOf("waiting")
        val registry = ToolRegistry(emptyList(), mSource = {
            loads++
            listOf(readTool(definition().copy(description = "version/$version")))
        }, mConfigurationVersion = { version }, mActiveRunIds = { activeRuns }, mCacheLimit = 2)
        registry.refresh()
        val oldest = registry.retainAvailable("waiting", setOf("test")).single()
        repeat(10) { registry.refresh() }
        assertEquals(1, loads)
        repeat(8) { version++; registry.refresh() }
        assertNotNull(registry.find("test", oldest.digest))
        assertNotEquals(oldest.digest, registry.find("test")?.digest)
        // 运行主动释放引用后不再保护旧指纹，即使活动集合尚未同步，下一次版本刷新超出缓存上限即回收。
        registry.release("waiting")
        version++; registry.refresh()
        assertNull(registry.find("test", oldest.digest))
        val retainedAgain = registry.retainAvailable("waiting", setOf("test")).single()
        repeat(4) { version++; registry.refresh() }
        assertNotNull(registry.find("test", retainedAgain.digest))
        // 运行终止后才允许回收，整库替换清除所有旧引用并强制重编译。
        activeRuns = emptySet()
        version++; registry.refresh()
        assertNull(registry.find("test", retainedAgain.digest))
        val current = requireNotNull(registry.find("test"))
        registry.clear()
        assertNull(registry.find("test", current.digest))
        registry.refresh()
        assertEquals(current.digest, registry.find("test")?.digest)
    }

    @Test
    fun oneRegistrationSupportsGenericDefaultsOrderingAndCollisionChecks() {
        val schema = ToolSchema(
            ToolSchema.Type.Object,
            properties = mapOf(
                "precision" to ToolSchema(
                    ToolSchema.Type.Integer,
                    minimum = 1.0,
                    maximum = 8.0
                )
            )
        )
        val spec = ToolSpecification(
            "new_read", ToolPromptSpec("Custom read", schema), ToolGroup.Utility,
            editableDefaults = listOf(
                EditableDefaultSpec(
                    "precision",
                    JsonPrimitive(2),
                    R.string.tool_settings_default_limit
                )
            )
        )
        val registration = ToolRegistration(
            spec,
            -1,
            R.string.agent_step_tools,
            { config, _ -> readTool(config.definition) })
        val catalog = ToolCatalog(BuiltinToolRegistrations.entries.reversed() + registration)
        assertEquals("new_read", catalog.registrations.first().specification.toolId)
        assertTrue(AgentJson.parse(catalog.defaultToolIdsJson).asJsonArray.any { it.asString == "new_read" })
        assertTrue("new_read" in catalog.reservedNames)
        assertEquals(
            5,
            catalog.resolve(
                "new_read",
                """{"defaults":{"precision":5}}"""
            ).defaults["precision"].asInt
        )
        assertThrows(ToolConfigValidationException::class.java) {
            catalog.resolve(
                "new_read",
                """{"defaults":{"precision":9}}"""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolCatalog(
                listOf(
                    registration,
                    registration
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolRegistry(
                listOf(
                    readTool(definition()),
                    readTool(definition())
                )
            )
        }
    }

    @Test
    fun configurationTypesAreRejectedWithoutGsonCoercion() {
        // 畸形字符串、布尔、整数与映射形状统一走配置错误，不能漏出原生 Gson 异常。
        listOf(
            """{"url":[]} """, """{"method":1}""", """{"timeoutMillis":"3000"}""",
            """{"maxResponseBytes":1.5}""", """{"headers":false}""", """{"credentialRef":{}}"""
        ).forEach { raw ->
            assertThrows(ToolConfigValidationException::class.java) {
                HttpToolConfigCodec.connection(
                    raw
                )
            }
        }
        listOf(
            """{"bindings":{}}""", """{"body":[]}""", """{"responsePath":3}""",
            """{"bindings":[{"argument":1,"target":"query","path":"q"}]}"""
        ).forEach { raw ->
            assertThrows(ToolConfigValidationException::class.java) {
                HttpToolConfigCodec.mapping(
                    raw
                )
            }
        }
        val id = "12345678-1234-1234-1234-123456789abc"
        val base =
            ToolConfigCodec.parse("""{"name":"custom","parameters":{"type":"object","properties":{}}}""")
        listOf("kind", "description", "enabled", "mapping", "connectionId").forEach { key ->
            val malformed = base.deepCopy().apply { add(key, JsonArray()) }
            assertThrows(ToolConfigValidationException::class.java) {
                ToolConfigCodec.readTool(
                    malformed,
                    id
                )
            }
        }
        assertThrows(ToolPreparationException::class.java) {
            ToolInputParser.parse(
                """{"x":1,"x":2}""",
                definition().parameters
            )
        }
    }

    @Test
    fun htmlExtractionDropsHiddenBlocksAndNormalizesText() {
        val extractor = SanitizingHtmlTextExtractor { html ->
            html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&")
        }
        val result =
            extractor.extract("<HEAD>secret</HEAD><p>Hello &amp; world</p><SCRIPT type='x'>bad()</SCRIPT>\r\n\r\n\r\n  tail\uFFFC<style>hidden</style><iframe>private</iframe>")
        assertEquals("Hello & world\n\n tail", result)
        assertEquals("visible", extractor.extract("visible<script>unterminated"))
        assertEquals(
            "body",
            extractor.extract("<template>hidden</template><svg>shape</svg><noscript>fallback</noscript>body")
        )
    }

    private fun definition(schema: ToolSchema = ToolSchema(ToolSchema.Type.Object)) =
        AgentToolDefinition(
            "test",
            "test",
            1,
            "Read",
            schema,
            ToolEffect.ReadOnly,
            parallelSafe = true
        )

    private fun readTool(definition: AgentToolDefinition): AgentTool =
        object : AbstractReadTool(EffectiveToolConfig(definition)) {
            override suspend fun read(
                context: ToolExecutionContext,
                arguments: JsonObject
            ): ToolResult = ToolResult.Success(arguments)
        }
}
