package me.kafuuneko.rpclient.libs.regex

class RegexScriptRuntime(
    private val mEngine: RegexScriptEngine
) {
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
