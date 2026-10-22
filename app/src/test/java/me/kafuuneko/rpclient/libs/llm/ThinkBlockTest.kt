package me.kafuuneko.rpclient.libs.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思维链与正文的隔离。
 *
 * 这两者拼在同一条消息里存着（`<think>…</think>` 加正文），
 * 渲染时会拆开，但编辑走的是数据库原文——不剥就等于让用户在推理过程里改字。
 */
class ThinkBlockTest {

    private val withThink = "<think>\n先看左门，再判断电量\n</think>\n\n他把门推开了。"

    @Test
    fun `detects think block`() {
        assertTrue(hasThinkBlock(withThink))
        assertFalse(hasThinkBlock("他把门推开了。"))
        assertFalse(hasThinkBlock("<think>没有闭合"))
    }

    @Test
    fun `extracts reasoning only`() {
        assertEquals("先看左门，再判断电量", extractThinkBlock(withThink))
    }

    @Test
    fun `strips reasoning leaving body`() {
        // 编辑框里只该看到这句
        assertEquals("他把门推开了。", stripThinkBlock(withThink))
    }

    @Test
    fun `strip keeps plain content untouched`() {
        assertEquals("他把门推开了。", stripThinkBlock("他把门推开了。"))
    }

    @Test
    fun `strip handles several blocks`() {
        // 有的模型会分多次输出思考段，全都要剥掉
        val multi = "<think>一</think>\n正文甲\n<think>二</think>\n正文乙"
        val stripped = stripThinkBlock(multi)
        assertFalse(stripped.contains("<think>"))
        assertTrue(stripped.contains("正文甲"))
        assertTrue(stripped.contains("正文乙"))
    }

    @Test
    fun `replace keeps original reasoning`() {
        // 用户改的是正文，推理过程不该被覆盖，也不该丢
        val replaced = replaceContentKeepingThink(withThink, "他退后一步。")
        assertEquals("先看左门，再判断电量", extractThinkBlock(replaced))
        assertEquals("他退后一步。", stripThinkBlock(replaced))
    }

    @Test
    fun `replace on plain message adds no think block`() {
        val plain = "他把门推开了。"
        val replaced = replaceContentKeepingThink(plain, "他退后一步。")
        assertFalse(hasThinkBlock(replaced))
        assertEquals("他退后一步。", replaced)
    }

    @Test
    fun `unclosed think tag is stripped entirely`() {
        // 被打断的思考：开标记之后全是没写完的推理，正文还没开始。
        // 第一版对这种「放行」，结果是半截推理永远赖在消息里，删不掉也改不了
        val broken = "<think>没闭合的推理"
        assertFalse(hasThinkBlock(broken))
        assertEquals("", stripThinkBlock(broken))
    }

    @Test
    fun `unclosed block after real content keeps the content`() {
        // 正文在前、思考被打断在后：正文要留住
        val mixed = "他说完了。\n<think>又想起一件事"
        assertEquals("他说完了。", stripThinkBlock(mixed))
    }
}
