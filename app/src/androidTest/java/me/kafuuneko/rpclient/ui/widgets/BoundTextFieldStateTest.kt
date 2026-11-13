package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 验证状态式文本输入在延迟回显和外部载入之间保持完整草稿。 */
class BoundTextFieldStateTest {
    @get:Rule
    val rule = createComposeRule()

    /** 模拟慢于键盘的 MVI 回显，以及用户随后从外部清空、载入其他草稿。 */
    @Test
    fun olderAcknowledgementPreservesNewerKeystrokes() {
        val external = mutableStateOf("")
        val edits = mutableListOf<String>()
        lateinit var field: TextFieldState
        rule.setContent {
            field = rememberBoundTextFieldState(external.value) { edits.add(it) }
        }
        // 回显首个字母时，第二批按键已进入编辑器并交给父状态。
        rule.runOnIdle { field.edit { append("M") } }
        rule.runOnIdle { field.edit { append("ira") } }
        rule.runOnIdle { external.value = "M" }
        rule.runOnIdle {
            assertEquals("Mira", field.text.toString())
            assertEquals(listOf("M", "Mira"), edits)
            external.value = "Mira"
        }

        // 用户撤回至父状态原值也需要发布，否则旧编辑可能成为最终保存值。
        rule.runOnIdle { field.edit { append("!") } }
        rule.runOnIdle { field.edit { delete(length - 1, length) } }
        rule.runOnIdle {
            assertEquals(listOf("Mira!", "Mira"), edits.takeLast(2))
            external.value = "Aiko"
        }
        rule.runOnIdle {
            assertEquals("Aiko", field.text.toString())
            external.value = ""
        }
        rule.runOnIdle { assertEquals("", field.text.toString()) }
    }
}
