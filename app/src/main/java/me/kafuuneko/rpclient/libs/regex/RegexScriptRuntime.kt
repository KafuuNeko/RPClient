package me.kafuuneko.rpclient.libs.regex

/**
 * Regex 引擎的业务运行入口。
 *
 * 负责构造执行上下文，并将 `<think>` 内外文本分别交给 Reasoning 与正文 placement；
 * 调用方无需重复实现推理块拆分逻辑。
 */
class RegexScriptRuntime(
    private val mEngine: RegexScriptEngine
) {
    /** 在明确 placement 和阶段下执行脚本链。 */
    fun execute(
        input: String,
        scripts: List<ScopedRegexScript>,
        placement: RegexPlacement,
        mode: RegexExecutionMode,
        macros: Map<String, String>,
        depth: Int? = null,
        isEdit: Boolean = false
    ): RegexExecutionResult {
        return mEngine.execute(
            input = input,
            scripts = scripts,
            context = RegexExecutionContext(
                placement = placement,
                mode = mode,
                isEdit = isEdit,
                depth = depth,
                macros = macros
            )
        )
    }

    /** 对 AI 消息执行正文与 Reasoning 分区处理。 */
    fun executeAiMessage(
        input: String,
        scripts: List<ScopedRegexScript>,
        mode: RegexExecutionMode,
        macros: Map<String, String>,
        depth: Int? = null,
        isEdit: Boolean = false
    ): RegexExecutionResult {
        return executeMessage(
            input = input,
            scripts = scripts,
            bodyPlacement = RegexPlacement.AiResponse,
            mode = mode,
            macros = macros,
            depth = depth,
            isEdit = isEdit
        )
    }

    /** 对聊天显示文本执行 markdownOnly 脚本，结果不会写回数据库。 */
    fun executeDisplayMessage(
        input: String,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
        depth: Int? = null,
        bodyPlacement: RegexPlacement = RegexPlacement.AiResponse
    ): RegexExecutionResult {
        return executeMessage(
            input = input,
            scripts = scripts,
            bodyPlacement = bodyPlacement,
            mode = RegexExecutionMode.Markdown,
            macros = macros,
            depth = depth,
            isEdit = false
        )
    }

    /** 保留 `<think>` 标签，仅分别改写标签外正文和标签内推理内容。 */
    private fun executeMessage(
        input: String,
        scripts: List<ScopedRegexScript>,
        bodyPlacement: RegexPlacement,
        mode: RegexExecutionMode,
        macros: Map<String, String>,
        depth: Int?,
        isEdit: Boolean
    ): RegexExecutionResult {
        if (!input.contains("<think>", ignoreCase = true)) {
            return execute(
                input,
                scripts,
                bodyPlacement,
                mode,
                macros,
                depth,
                isEdit
            )
        }
        val hits = mutableListOf<RegexExecutionHit>()
        val errors = mutableListOf<RegexExecutionError>()
        val output = buildString {
            var cursor = 0
            while (cursor < input.length) {
                val open = input.indexOf("<think>", cursor, ignoreCase = true)
                if (open < 0) {
                    val result = execute(
                        input.substring(cursor),
                        scripts,
                        bodyPlacement,
                        mode,
                        macros,
                        depth,
                        isEdit
                    )
                    append(result.text)
                    hits += result.hits
                    errors += result.errors
                    break
                }
                val body = execute(
                    input.substring(cursor, open),
                    scripts,
                    bodyPlacement,
                    mode,
                    macros,
                    depth,
                    isEdit
                )
                append(body.text)
                hits += body.hits
                errors += body.errors
                append(input.substring(open, open + THINK_OPEN.length))
                val contentStart = open + THINK_OPEN.length
                val close = input.indexOf("</think>", contentStart, ignoreCase = true)
                val contentEnd = if (close < 0) input.length else close
                val reasoning = execute(
                    input.substring(contentStart, contentEnd),
                    scripts,
                    RegexPlacement.Reasoning,
                    mode,
                    macros,
                    depth,
                    isEdit
                )
                append(reasoning.text)
                hits += reasoning.hits
                errors += reasoning.errors
                if (close < 0) break
                append(input.substring(close, close + THINK_CLOSE.length))
                cursor = close + THINK_CLOSE.length
            }
        }
        return RegexExecutionResult(output, hits, errors)
    }

    companion object {
        /** 构造 Regex 替换支持的常用角色、用户、场景和群组宏。 */
        fun macros(
            userName: String,
            characterName: String,
            userDescription: String = "",
            scenario: String = "",
            groupNames: String = ""
        ): Map<String, String> = mapOf(
            "user" to userName,
            "char" to characterName,
            "persona" to userDescription,
            "scenario" to scenario,
            "group" to groupNames,
            "charIfNotGroup" to characterName
        )

        private const val THINK_OPEN = "<think>"
        private const val THINK_CLOSE = "</think>"
    }
}
