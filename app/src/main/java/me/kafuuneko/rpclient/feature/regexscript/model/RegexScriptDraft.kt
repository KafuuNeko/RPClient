package me.kafuuneko.rpclient.feature.regexscript.model

import me.kafuuneko.rpclient.libs.regex.RegexFindMacroMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript

/**
 * Regex 编辑器使用的可输入表单。
 *
 * 深度和 Trim Out 暂时以字符串保存，允许用户输入未完成内容并由 ViewModel 即时校验；
 * [rawJson] 随编辑过程保留，用于继续往返未知 SillyTavern 字段。
 */
data class RegexScriptDraft(
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 正则脚本的显示名称。 */
    val scriptName: String = "",
    /** 正则脚本用于查找内容的表达式。 */
    val findRegex: String = "",
    /** 正则脚本命中后使用的替换模板。 */
    val replaceString: String = "",
    /** 执行替换前需要从输入中移除的文本片段。 */
    val trimStrings: String = "",
    /** 测试结果中按插入位置分组的世界书内容。 */
    val placements: Set<Int> = setOf(
        RegexPlacement.UserInput.value,
        RegexPlacement.AiResponse.value
    ),
    /** 当前对象或功能是否禁用。 */
    val disabled: Boolean = false,
    /** 脚本是否只在 Markdown 展示阶段执行。 */
    val markdownOnly: Boolean = false,
    /** 脚本是否只在 Prompt 构建阶段执行。 */
    val promptOnly: Boolean = false,
    /** 用户编辑消息时是否仍执行当前脚本。 */
    val runOnEdit: Boolean = false,
    /** 是否先对正则表达式中的宏进行替换。 */
    val substituteRegex: Int = RegexFindMacroMode.Disabled.value,
    /** 脚本允许处理的最小消息深度。 */
    val minDepth: String = "",
    /** 脚本允许处理的最大消息深度。 */
    val maxDepth: String = "",
    /** 未经业务转换的原始 JSON 文本。 */
    val rawJson: String = "{}"
) {
    /** 将通过校验的表单转换为可执行领域模型。 */
    fun toScript(): RegexScript = RegexScript(
        id = id,
        scriptName = scriptName.trim(),
        findRegex = findRegex.trim(),
        replaceString = replaceString,
        trimStrings = trimStrings.lines().map { it.trim() }.filter { it.isNotEmpty() },
        placement = placements.sorted(),
        disabled = disabled,
        markdownOnly = markdownOnly,
        promptOnly = promptOnly,
        runOnEdit = runOnEdit,
        substituteRegex = substituteRegex,
        minDepth = minDepth.trim().toIntOrNull(),
        maxDepth = maxDepth.trim().toIntOrNull(),
        rawJson = rawJson
    )

    companion object {
        /** 从持久化脚本恢复编辑草稿。 */
        fun from(script: RegexScript): RegexScriptDraft = RegexScriptDraft(
            id = script.id,
            scriptName = script.scriptName,
            findRegex = script.findRegex,
            replaceString = script.replaceString,
            trimStrings = script.trimStrings.joinToString("\n"),
            placements = script.placement.toSet(),
            disabled = script.disabled,
            markdownOnly = script.markdownOnly,
            promptOnly = script.promptOnly,
            runOnEdit = script.runOnEdit,
            substituteRegex = script.substituteRegex,
            minDepth = script.minDepth?.toString().orEmpty(),
            maxDepth = script.maxDepth?.toString().orEmpty(),
            rawJson = script.rawJson
        )
    }
}
