package me.kafuuneko.rpclient.libs.prompt

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
    // 校验人设描述非空
    if (userDescription.isBlank()) return ""
    // 先行替换人设描述中的 {{char}} / {{user}} 宏
    val resolvedDescription = resolveCharacterUserMacros(
        template = userDescription,
        characterName = characterName,
        userName = userName,
    )
    if (template.isBlank()) return resolvedDescription
    // 替换模板中的 {{char}} / {{user}} 宏
    val resolvedTemplate = resolveCharacterUserMacros(
        template = template,
        characterName = characterName,
        userName = userName,
    )
    // 查找并替换 {{persona}} 插槽宏
    val personaMacro = Regex("""\{\{\s*persona\s*\}\}""", RegexOption.IGNORE_CASE)
    return if (personaMacro.containsMatchIn(resolvedTemplate)) {
        resolvedTemplate.replace(personaMacro) { resolvedDescription }
    } else {
        resolvedDescription
    }
}
