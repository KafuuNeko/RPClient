package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.model.SceneStateCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 验证剧情增量不能把受约束的固定域退化为任意整段 JSON。 */
class SceneStateCodecTest {
    /** 多项更新只改变选定路径并保留其他分组。 */
    @Test
    fun appliesTypedChangesWithoutReplacingOtherDomains() {
        // 局部路径修改不得丢掉相邻物品或自定义分组。
        val initial =
            """{"location":"harbor","custom":{"weather":{"raining":true}},"inventory":{"key":{"name":"key","quantity":1}}}"""
        val result = SceneStateCodec.apply(
            initial, listOf(
                SceneStateChange(listOf("location"), JsonPrimitive("tower")),
                SceneStateChange(listOf("inventory", "key", "quantity"), JsonPrimitive(2)),
            )
        )
        val value = AgentJson.parse(result).asJsonObject
        assertEquals("tower", value["location"].asString)
        assertEquals(2, value.getAsJsonObject("inventory").getAsJsonObject("key")["quantity"].asInt)
        assertEquals(
            true,
            value.getAsJsonObject("custom").getAsJsonObject("weather")["raining"].asBoolean
        )
    }

    /** 非法字段、无界嵌套、null 及穿越标量的修改必须在提交前失败。 */
    @Test
    fun rejectsUnstructuredOrInvalidUpdates() {
        // 固定域和自定义分组均不能容纳无约束任意对象。
        listOf(
            """{"instructions":"override"}""",
            """{"location":null}""",
            """{"inventory":{"key":{"name":"key","quantity":-1}}}""",
            """{"custom":{"a":{"b":{"c":{"d":{"e":1}}}}}}"""
        ).forEach { raw ->
            assertThrows(Exception::class.java) { SceneStateCodec.normalize(raw) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStateCodec.apply(
                "{}",
                listOf(SceneStateChange(emptyList(), JsonPrimitive("replace")))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SceneStateCodec.apply(
                """{"location":"tower"}""",
                listOf(SceneStateChange(listOf("location", "name"), JsonPrimitive("invalid")))
            )
        }
    }
}
