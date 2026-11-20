package me.kafuuneko.rpclient.libs.prompt.preset

import me.kafuuneko.rpclient.libs.memory.MissingMemorySlotException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** 便携格式兼容性覆盖未知字段、空覆盖、重复键和无损解析边界。 */
class PromptPresetCodecTest {
    @Test
    fun roundTripKeepsUnknownKeysExplicitEmptyAndWhitespaceWithoutMaterializingDefaults() {
        val original = PortablePromptPreset(
            name = "Night", description = "description\nwith whitespace",
            overrides = linkedMapOf(
                "main" to "", "future_key" to "  {{unknown}}\n ",
                "character_memory_context" to "<facts>{{memory}}</facts>",
            ),
        )
        val decoded = PromptPresetCodec.decode(PromptPresetCodec.encode(original))
        // 未知字段保留用于下一版本；缺失字段继续跟随当前版本默认值。
        assertEquals(original, decoded)
        val snapshot = PromptTemplateSnapshot.resolve(9, 1, decoded.overrides)
        assertEquals("", snapshot.mainPrompt)
        assertEquals(
            PromptType.CharacterMemoryItem.defaultValue,
            snapshot.values[PromptType.CharacterMemoryItem]
        )
        assertFalse(decoded.overrides.containsKey("character_memory_item"))
    }

    @Test
    fun rejectsWrongVersionDuplicateFieldsWrongTypesAndTrailingData() {
        val valid = PromptPresetCodec.encode(PortablePromptPreset("Test", "", mapOf("main" to "")))
        val invalid = listOf(
            valid.replace("\"version\": 1", "\"version\": 2"),
            valid.replace("\"version\": 1", "\"version\": \"1\""),
            valid.replace("\"version\": 1", "\"version\": 1, \"version\": 1"),
            valid.replace("\"main\": \"\"", "\"main\": \"\", \"main\": \"lost\""),
            valid.replace("\"main\": \"\"", "\"main\": null"),
            valid.replace("\"main\": \"\"", "\"main\": 12"),
            valid + "{}",
            valid.replace("\"overrides\"", "\"credentials\""),
        )
        // 不使用 Gson 的宽松类型转换，损坏文件不能被当作有效空配置导入。
        invalid.forEach { json ->
            assertThrows(Exception::class.java) { PromptPresetCodec.decode(json) }
        }
    }

    @Test
    fun rejectsMissingBodySlotAndExcessiveUtf8Bytes() {
        val error = assertThrows(MissingMemorySlotException::class.java) {
            PromptPresetCodec.decode("""{"format":"rpclient.prompt-preset","version":1,"name":"Test","description":"","overrides":{"character_memory_item":"{{char}}"}}""")
        }
        assertEquals(PromptType.CharacterMemoryItem, error.type)
        // 按字节限制导入，不能让多字节正文绕过上限。
        assertThrows(IllegalArgumentException::class.java) {
            PromptPresetCodec.encode(
                PortablePromptPreset(
                    "Test",
                    "",
                    mapOf("future" to "茶".repeat(PromptPresetCodec.MAX_BYTES / 2))
                )
            )
        }
    }
}
