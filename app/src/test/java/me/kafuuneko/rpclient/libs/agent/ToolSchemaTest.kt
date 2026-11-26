package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保护跨协议必填、可空、数值及有界解析的共同语义。 */
class ToolSchemaTest {
    private val mSchema = ToolSchema(
        type = ToolSchema.Type.Object,
        properties = mapOf(
            "query" to ToolSchema(
                ToolSchema.Type.String,
                nullable = true,
                minLength = 2,
                maxLength = 5
            ),
            "count" to ToolSchema(ToolSchema.Type.Integer, minimum = 1.0, maximum = 100.0),
        ),
        required = setOf("query"),
    )

    /** 缺失、null 和错误类型不能在编码前被归一化成同一种参数。 */
    @Test
    fun requiredNullableAndUnknownFieldsRemainDistinct() {
        // 缺省字段必须失败，而显式 null 使用可空规则。
        assertEquals("required", violations("{}").single().code)
        assertTrue(violations("""{"query":null}""").isEmpty())
        assertTrue(violations("""{"query":"ok","count":2}""").isEmpty())
        assertEquals("type", violations("""{"query":"ok","count":"2"}""").single().code)
        assertEquals(
            "additional_property",
            violations("""{"query":"ok","secret":"ignored"}""").single().code
        )
        assertEquals("string_length", violations("""{"query":"x"}""").single().code)
    }

    /** 大整数不经 Double 舍入，小数、越界数和不合法枚举定义必须被拒绝。 */
    @Test
    fun integerRangesAndEnumNullAreValidatedLocally() {
        // 整数类型校验不能通过字符串解析或浮点舍入实现。
        assertEquals("type", violations("""{"query":"ok","count":1.5}""").single().code)
        assertEquals("number_range", violations("""{"query":"ok","count":101}""").single().code)
        val integer = ToolSchema(ToolSchema.Type.Integer)
        assertTrue(
            ToolSchemaValidator.validate(integer, AgentJson.parse("9007199254740993")).isEmpty()
        )
        val nullableEnum = ToolSchema(
            ToolSchema.Type.String,
            nullable = true,
            enumValues = listOf(JsonPrimitive("yes"))
        )
        assertEquals(
            "enum",
            ToolSchemaValidator.validate(nullableEnum, JsonNull.INSTANCE).single().code
        )
        assertThrows(IllegalArgumentException::class.java) {
            ToolSchemaValidator.validateDefinition(
                ToolSchema(
                    ToolSchema.Type.Integer,
                    enumValues = listOf(JsonPrimitive("1"))
                )
            )
        }
    }

    /** 严格 JSON 边界必须先于无界树构建和工具派发。 */
    @Test
    fun rejectsAmbiguousAndExcessiveJson() {
        // 歧义与复杂度均在进入工具处理器之前拒绝。
        listOf(
            """{"q":1,"q":2}""",
            "{} {}",
            "{q:1}",
            "NaN",
            "[".repeat(9) + "0" + "]".repeat(9)
        ).forEach { raw ->
            assertThrows(Exception::class.java) { AgentJson.parse(raw) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentJson.parse(
                "\"" + "字".repeat(
                    30_000
                ) + "\""
            )
        }
        assertEquals(
            AgentJson.canonical(AgentJson.parse("""{"a":1,"b":2}""")),
            AgentJson.canonical(AgentJson.parse("""{"b":2,"a":1}"""))
        )
    }

    /** Schema 自身越界时不能注册，也不能让协议编码器静默弱化约束。 */
    @Test
    fun validatesSchemaDepthAndInconsistentConstraints() {
        // 注册阶段拒绝协议之间无法保持共同语义的定义。
        var nested = ToolSchema(ToolSchema.Type.String)
        repeat(8) { nested = ToolSchema(ToolSchema.Type.Array, items = nested) }
        val deep = nested
        assertThrows(IllegalArgumentException::class.java) {
            ToolSchemaValidator.validateDefinition(
                deep
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolSchemaValidator.validateDefinition(
                mSchema.copy(required = setOf("missing"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolSchemaValidator.validateDefinition(
                ToolSchema(ToolSchema.Type.String, minimum = 0.0)
            )
        }
    }

    private fun violations(raw: String) =
        ToolSchemaValidator.validate(mSchema, AgentJson.parse(raw))
}
