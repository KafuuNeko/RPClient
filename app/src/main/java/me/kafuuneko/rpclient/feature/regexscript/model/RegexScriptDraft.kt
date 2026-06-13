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
    val id: String,
    val scriptName: String = "",
    val findRegex: String = "",
    val replaceString: String = "",
    val trimStrings: String = "",
    val placements: Set<Int> = setOf(
        RegexPlacement.UserInput.value,
        RegexPlacement.AiResponse.value
    ),
    val disabled: Boolean = false,
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = false,
    val substituteRegex: Int = RegexFindMacroMode.Disabled.value,
    val minDepth: String = "",
    val maxDepth: String = "",
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
