package me.kafuuneko.rpclient.libs.regex

/**
 * 纯 Regex 执行引擎。
 *
 * 引擎不访问数据库或全局配置，只根据输入、脚本和上下文稳定地产生结果；
 * 单条脚本失败会被记录并隔离，后续脚本仍继续执行。
 */
class RegexScriptEngine {
    /** 按作用域优先级和脚本顺序执行所有适用脚本。 */
    fun execute(
        input: String,
        scripts: List<ScopedRegexScript>,
        context: RegexExecutionContext
    ): RegexExecutionResult {
        if (input.isEmpty()) return RegexExecutionResult(input)
        var output = input
        val hits = mutableListOf<RegexExecutionHit>()
        val errors = mutableListOf<RegexExecutionError>()

        scripts.sortedWith(
            compareBy<ScopedRegexScript> { it.scope.priority() }
                .thenBy { it.order }
        ).forEach { scoped ->
            val script = scoped.script
            if (!script.shouldRun(context)) return@forEach
            val before = output
            val result = runCatching {
                applyScript(before, script, context.macros)
            }
            result.onSuccess { application ->
                output = application.text
                if (application.matched) {
                    hits += RegexExecutionHit(
                        scriptId = script.id,
                        scriptName = script.scriptName,
                        scope = scoped.scope,
                        ownerName = scoped.ownerName,
                        placement = context.placement,
                        mode = context.mode,
                        changed = application.text != before
                    )
                }
            }.onFailure { throwable ->
                errors += RegexExecutionError(
                    scriptId = script.id,
                    scriptName = script.scriptName,
                    message = throwable.message ?: "Invalid regex script"
                )
            }
        }

        return RegexExecutionResult(output, hits, errors)
    }

    /** 仅编译 Find Regex，用于编辑器即时校验而不修改任何文本。 */
    fun validate(script: RegexScript, macros: Map<String, String> = emptyMap()): String? {
        if (script.findRegex.isBlank()) return "Find Regex cannot be empty"
        return runCatching {
            compile(script, macros)
        }.exceptionOrNull()?.message
    }

    /** 根据启停、placement、编辑状态、深度和临时模式判断脚本是否参与本轮执行。 */
    private fun RegexScript.shouldRun(context: RegexExecutionContext): Boolean {
        if (disabled || findRegex.isBlank() || !supports(context.placement)) return false
        if (context.isEdit && !runOnEdit) return false
        context.depth?.let { depth ->
            if (minDepth != null && minDepth >= -1 && depth < minDepth) return false
            if (maxDepth != null && maxDepth >= 0 && depth > maxDepth) return false
        }
        return when (context.mode) {
            RegexExecutionMode.Source -> !markdownOnly && !promptOnly
            RegexExecutionMode.Markdown -> markdownOnly && !promptOnly
            RegexExecutionMode.Prompt -> promptOnly && !markdownOnly
        }
    }

    /** 编译并应用单条脚本，同时区分“未命中”和“命中但文本未改变”。 */
    private fun applyScript(
        input: String,
        script: RegexScript,
        macros: Map<String, String>
    ): ScriptApplication {
        val compiled = compile(script, macros)
        val firstMatch = compiled.regex.find(input)
            ?: return ScriptApplication(input, matched = false)
        if (compiled.sticky && firstMatch.range.first != 0) {
            return ScriptApplication(input, matched = false)
        }
        val transform: (MatchResult) -> CharSequence = { match ->
            buildReplacement(script, match, macros)
        }
        val output = if (compiled.global && compiled.sticky) {
            replaceSticky(input, compiled.regex, transform)
        } else if (compiled.global) {
            compiled.regex.replace(input, transform)
        } else {
            input.replaceRange(firstMatch.range, transform(firstMatch))
        }
        return ScriptApplication(output, matched = true)
    }

    /**
     * 模拟 JavaScript 同时启用 `g` 与 `y` 时的连续粘性匹配。
     *
     * 每次匹配必须从上一次结束位置开始，遇到第一个空隙立即停止。
     */
    private fun replaceSticky(
        input: String,
        regex: Regex,
        transform: (MatchResult) -> CharSequence
    ): String = buildString {
        var cursor = 0
        while (cursor <= input.length) {
            val match = regex.find(input, cursor) ?: break
            if (match.range.first != cursor) break
            append(transform(match))
            val nextCursor = match.range.last + 1
            if (nextCursor > cursor) {
                cursor = nextCursor
            } else if (cursor < input.length) {
                append(input[cursor])
                cursor += 1
            } else {
                break
            }
        }
        append(input.substring(cursor))
    }

    /** 按 Find Regex 宏模式展开表达式并生成 Kotlin Regex。 */
    private fun compile(
        script: RegexScript,
        macros: Map<String, String>
    ): CompiledRegex {
        val find = when (RegexFindMacroMode.fromValue(script.substituteRegex)) {
            RegexFindMacroMode.Disabled -> script.findRegex
            RegexFindMacroMode.Raw -> resolveMacros(script.findRegex, macros)
            RegexFindMacroMode.Escaped -> resolveMacros(script.findRegex, macros, ::escapeRegexMacro)
        }
        val parsed = parseRegex(find)
        return CompiledRegex(
            regex = Regex(parsed.pattern, parsed.options),
            global = 'g' in parsed.flags,
            sticky = 'y' in parsed.flags
        )
    }

    /** 解析 JavaScript `/pattern/flags` 写法；普通字符串按无 flags 的表达式处理。 */
    private fun parseRegex(value: String): ParsedRegex {
        if (!value.startsWith('/')) {
            return ParsedRegex(value, emptySet(), emptySet())
        }
        val delimiter = value.lastUnescapedSlash()
        require(delimiter > 0) { "Find Regex must use /pattern/flags syntax" }
        val pattern = value.substring(1, delimiter).replace("\\/", "/")
        val flags = value.substring(delimiter + 1).toSet()
        require(flags.size == value.substring(delimiter + 1).length) {
            "Regex flags cannot be duplicated"
        }
        require(flags.all { it in SUPPORTED_FLAGS }) {
            "Unsupported regex flags: ${flags.filterNot { it in SUPPORTED_FLAGS }.joinToString("")}"
        }
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        return ParsedRegex(pattern, options, flags)
    }

    /** 展开 `{{match}}`、编号/命名捕获组、Trim Out 和替换文本宏。 */
    private fun buildReplacement(
        script: RegexScript,
        match: MatchResult,
        macros: Map<String, String>
    ): String {
        val normalized = MATCH_MACRO.replace(script.replaceString) { "\$0" }
        val withGroups = GROUP_REFERENCE.replace(normalized) { reference ->
            val value = when {
                reference.groupValues[1].isNotEmpty() -> {
                    val index = reference.groupValues[1].toInt()
                    if (index in 0 until match.groups.size) {
                        match.groups[index]?.value
                    } else {
                        null
                    }
                }
                else -> runCatching {
                    match.groups[reference.groupValues[2]]?.value
                }.getOrNull()
            }.orEmpty()
            script.trimStrings.fold(value) { current, trim ->
                current.replace(resolveMacros(trim, macros), "")
            }
        }
        return resolveMacros(withGroups, macros)
    }

    /** 以不区分大小写的键查找并替换 SillyTavern 风格宏。 */
    private fun resolveMacros(
        text: String,
        macros: Map<String, String>,
        transform: (String) -> String = { it }
    ): String {
        return MACRO.replace(text) { match ->
            val key = match.groupValues[1].lowercase()
            macros.entries.firstOrNull { it.key.lowercase() == key }
                ?.value
                ?.let(transform)
                ?: match.value
        }
    }

    /** 将宏值转义为可安全嵌入 Find Regex 的字面量。 */
    private fun escapeRegexMacro(value: String): String {
        return buildString {
            value.forEach { character ->
                append(
                    when (character) {
                        '\n' -> "\\n"
                        '\r' -> "\\r"
                        '\t' -> "\\t"
                        '\u000B' -> "\\v"
                        '\u000C' -> "\\f"
                        '\u0000' -> "\\0"
                        '.', '^', '$', '*', '+', '?', '{', '}', '[', ']', '\\', '/', '|', '(', ')' ->
                            "\\$character"
                        else -> character
                    }
                )
            }
        }
    }

    /** 从末尾查找未被反斜杠转义的 `/`，用于分隔 pattern 与 flags。 */
    private fun String.lastUnescapedSlash(): Int {
        for (index in lastIndex downTo 1) {
            if (this[index] != '/') continue
            var backslashes = 0
            var cursor = index - 1
            while (cursor >= 0 && this[cursor] == '\\') {
                backslashes += 1
                cursor -= 1
            }
            if (backslashes % 2 == 0) return index
        }
        return -1
    }

    private fun RegexScriptScope.priority(): Int = when (this) {
        RegexScriptScope.Global -> 0
        RegexScriptScope.Preset -> 1
        RegexScriptScope.Character -> 2
    }

    private data class ParsedRegex(
        val pattern: String,
        val options: Set<RegexOption>,
        val flags: Set<Char>
    )

    private data class CompiledRegex(
        val regex: Regex,
        val global: Boolean,
        val sticky: Boolean
    )

    private data class ScriptApplication(
        val text: String,
        val matched: Boolean
    )

    private companion object {
        val SUPPORTED_FLAGS = setOf('g', 'i', 'm', 's', 'u', 'y')
        val MATCH_MACRO = Regex("""\{\{match\}\}""", RegexOption.IGNORE_CASE)
        val GROUP_REFERENCE = Regex("""\$(\d+)|\$<([^>]+)>""")
        val MACRO = Regex("""\{\{([^{}]+)\}\}""", RegexOption.IGNORE_CASE)
    }
}
