package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** 验证记忆资料的模板边界，防止事实被当作嵌套模板执行。 */
class MemoryPromptRendererTest {
    private val mScope = MacroScope(
        mapOf(
            "char" to MacroValue.Literal("Lyra"),
            "user" to MacroValue.Literal("Alex"),
        ),
    )

    @Test
    fun factMarkupIsEscapedAndMacrosRemainLiteralAcrossBothLayers() {
        val snapshot = PromptTemplateSnapshot.resolve(
            8,
            2,
            mapOf(
                "character_memory_item" to "<item>{{char}}: {{ FACT }}</item>",
                "character_memory_context" to "{{user}}\n{{ MEMORY }}",
            ),
        )
        // 展开单条事实时先转义标记，防止宏表达式与闭合标签被二次解释。
        val item = MemoryPromptRenderer.renderItem(
            fact = "{{char}} <USER> </item> & \"quoted\"",
            snapshot = snapshot,
            scope = mScope,
        )
        // 事实正文包含外层结束标签和宏样式，二次包装也不能把它们解释为模板。
        val actual = MemoryPromptRenderer.renderContext(listOf(item), snapshot, mScope)
        assertEquals(
            "Alex\n<item>Lyra: {{char}} &lt;USER&gt; &lt;/item&gt; &amp; &quot;quoted&quot;</item>",
            actual,
        )
        assertFalse(actual.contains("Alex </item>"))
    }

    @Test
    fun emptyOverridesOmitContentWithoutRestoringDefaultsOrAddingEmptyBlocks() {
        val defaults = PromptTemplateSnapshot.defaults()
        val emptyItem = PromptTemplateSnapshot.resolve(2, 1, mapOf("character_memory_item" to ""))
        val emptyContext =
            PromptTemplateSnapshot.resolve(3, 1, mapOf("character_memory_context" to "  \n"))
        val item = MemoryPromptRenderer.renderItem("remember", defaults, mScope)
        assertEquals("", MemoryPromptRenderer.renderItem("remember", emptyItem, mScope))
        assertEquals("", MemoryPromptRenderer.renderContext(listOf("", " "), defaults, mScope))
        assertEquals("", MemoryPromptRenderer.renderContext(listOf(item), emptyContext, mScope))
    }

    @Test
    fun requiredSlotsUseTheSameSyntaxAsMacroExpansion() {
        MemoryPromptRenderer.validate(PromptType.CharacterMemoryItem, "{{ FACT }}")
        MemoryPromptRenderer.validate(PromptType.CharacterMemoryContext, "{{memory}}")
        MemoryPromptRenderer.validate(PromptType.CharacterMemoryItem, "")
        // 同名参数宏不是正文槽，缺槽时必须定位到字段而不是悄悄漏发事实。
        assertThrows(MissingMemorySlotException::class.java) {
            MemoryPromptRenderer.validate(PromptType.CharacterMemoryItem, "{{fact::other}}")
        }
        assertThrows(MissingMemorySlotException::class.java) {
            MemoryPromptRenderer.validate(PromptType.CharacterMemoryContext, "{{fact}}")
        }
    }
}
