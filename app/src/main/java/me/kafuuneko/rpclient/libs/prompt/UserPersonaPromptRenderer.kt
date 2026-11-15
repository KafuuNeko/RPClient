package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue

/**
 * 使用统一模板包装用户人设，并按当前会话作用域展开名称宏。
 *
 * 人设为空时不注入任何内容；模板为空或未包含 `{{persona}}` 时回退为人设正文，
 * 避免错误配置导致用户人设被静默丢弃。没有唯一角色的上下文可传入 null，保留
 * 人设正文与模板中的 `{{char}}` 供后续诊断。
 */
fun renderUserPersonaTemplate(
    template: String,
    userName: String,
    userDescription: String,
    characterName: String?,
): String {
    if (userDescription.isBlank()) return ""
    val values = buildMap {
        put("user", MacroValue.Literal(userName))
        characterName?.let { put("char", MacroValue.Literal(it)) }
        put("persona", MacroValue.Template(userDescription))
    }
    val hasSlot = promptMacroExpander.references(template).any { it.name == "persona" }
    val wrapper = if (hasSlot) template else "{{persona}}"
    return promptMacroExpander.expand(wrapper, MacroScope(values)).text
}
