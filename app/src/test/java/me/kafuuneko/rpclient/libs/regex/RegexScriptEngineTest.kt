package me.kafuuneko.rpclient.libs.regex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexScriptEngineTest {
    private val engine = RegexScriptEngine()

    @Test
    fun scriptsRunInScopeAndListOrder() {
        val scripts = listOf(
            scoped(
                script = script("global", "/a/g", "b"),
                scope = RegexScriptScope.Global,
                order = 0
            ),
            scoped(
                script = script("character", "/b/g", "c"),
                scope = RegexScriptScope.Character,
                order = 0
            ),
        )

        val result = engine.execute("a", scripts, context())

        assertEquals("c", result.text)
        assertEquals(listOf("global", "character"), result.hits.map { it.scriptId })
    }

    @Test
    fun supportsFlagsCapturesMatchMacroAndTrimOut() {
        val script = script(
            id = "capture",
            find = "/(?<word>foo)-(BAR)/gi",
            replacement = "{{match}}:$<word>:$2",
        ).copy(trimStrings = listOf("o"))

        val result = engine.execute("Foo-BAR foo-bar", listOf(scoped(script)), context())

        assertEquals("F-BAR:F:BAR f-bar:f:bar", result.text)
    }

    @Test
    fun supportsMultilineDotAllAndUnicodeFlags() {
        val scripts = listOf(
            scoped(script = script("multiline", "/^foo$/gm", "line"), order = 0),
            scoped(script = script("dotall", "/a.b/gs", "joined"), order = 1),
            scoped(script = script("unicode", "/\\u{1F60A}/gu", "face"), order = 2),
        )

        val result = engine.execute(
            input = "foo\na\nb 😊",
            scripts = scripts,
            context = context(),
        )

        assertEquals("line\njoined face", result.text)
    }

    @Test
    fun rejectsUnicodeCodePointEscapeOutsideValidRange() {
        val invalid = script("invalid-unicode", "/\\u{110000}/u", "replacement")

        val validationError = engine.validate(invalid)

        assertTrue(validationError?.contains("Unicode code point escape") == true)
    }

    @Test
    fun findMacrosSupportRawEscapedAndDisabledModes() {
        val raw =
            script("raw", "/{{char}}/g", "x").copy(substituteRegex = RegexFindMacroMode.Raw.value)
        val escaped = script(
            "escaped",
            "/{{char}}/g",
            "y"
        ).copy(substituteRegex = RegexFindMacroMode.Escaped.value)
        val disabled = script("disabled", "/\\{\\{char\\}\\}/g", "z")
        val macros = mapOf("char" to "A+B")

        assertEquals("x", engine.execute("AAAB", listOf(scoped(raw)), context(macros)).text)
        assertEquals("y", engine.execute("A+B", listOf(scoped(escaped)), context(macros)).text)
        assertEquals("A+B", engine.execute("A+B", listOf(scoped(disabled)), context(macros)).text)
        assertEquals(
            "z",
            engine.execute("{{char}}", listOf(scoped(disabled)), context(macros)).text
        )
    }

    @Test
    fun replacementMacrosIgnoreCaseAndPreserveUnknownMacros() {
        val script = script(
            id = "replacement-macros",
            find = "/input/g",
            replacement = "{{UsEr}}/{{missing}}",
        )

        val result = engine.execute(
            input = "input",
            scripts = listOf(scoped(script)),
            context = context(macros = mapOf("user" to "Alice")),
        )

        assertEquals("Alice/{{missing}}", result.text)
    }

    @Test
    fun modeDepthAndRunOnEditAreRespected() {
        val script = script("mode", "/x/g", "y").copy(
            promptOnly = true,
            runOnEdit = false,
            minDepth = 1,
            maxDepth = 2,
        )
        val scoped = listOf(scoped(script))

        assertEquals("x", engine.execute("x", scoped, context()).text)
        assertEquals(
            "x",
            engine.execute(
                input = "x",
                scripts = scoped,
                context = context(mode = RegexExecutionMode.Prompt, depth = 0),
            ).text,
        )
        assertEquals(
            "y",
            engine.execute(
                input = "x",
                scripts = scoped,
                context = context(mode = RegexExecutionMode.Prompt, depth = 1),
            ).text,
        )
        assertEquals(
            "x",
            engine.execute(
                input = "x",
                scripts = scoped,
                context = context(mode = RegexExecutionMode.Prompt, depth = 1, isEdit = true),
            ).text,
        )
    }

    @Test
    fun conflictingTemporaryModesDoNotRun() {
        val script = script("conflict", "/x/g", "y").copy(
            markdownOnly = true,
            promptOnly = true,
        )
        val scripts = listOf(scoped(script))

        assertEquals(
            "x",
            engine.execute("x", scripts, context(mode = RegexExecutionMode.Markdown)).text,
        )
        assertEquals(
            "x",
            engine.execute("x", scripts, context(mode = RegexExecutionMode.Prompt)).text,
        )
    }

    @Test
    fun invalidScriptDoesNotBlockFollowingScripts() {
        val scripts = listOf(
            scoped(script = script("bad", "/[/g", "x"), order = 0),
            scoped(script = script("good", "/a/g", "b"), order = 1),
        )

        val result = engine.execute("a", scripts, context())

        assertEquals("b", result.text)
        assertEquals(1, result.errors.size)
        assertTrue(result.hits.any { it.scriptId == "good" })
    }

    @Test
    fun unmatchedScriptsAreNotReportedAsHits() {
        val result = engine.execute(
            input = "a",
            scripts = listOf(scoped(script("unmatched", "/z/g", "x"))),
            context = context(),
        )

        assertEquals("a", result.text)
        assertTrue(result.hits.isEmpty())
    }

    @Test
    fun stickyGlobalReplacementStopsAtFirstGap() {
        val result = engine.execute(
            input = "aa-aa",
            scripts = listOf(scoped(script("sticky", "/a/gy", "b"))),
            context = context(),
        )

        assertEquals("bb-aa", result.text)
    }

    @Test
    fun runtimeSeparatesReasoningFromAiResponsePlacement() {
        val runtime = RegexScriptRuntime(engine)
        val reasoning = script("reasoning", "/secret/g", "hidden").copy(
            placement = listOf(RegexPlacement.Reasoning.value),
        )
        val answer = script(
            "answer",
            "/answer/g",
            "reply"
        ).copy(placement = listOf(RegexPlacement.AiResponse.value))

        val result = runtime.executeAiMessage(
            input = "<think>secret</think>answer",
            scripts = listOf(scoped(reasoning), scoped(script = answer, order = 1)),
            mode = RegexExecutionMode.Source,
            macros = emptyMap(),
        )

        assertEquals("<think>hidden</think>reply", result.text)
    }

    private fun script(
        id: String,
        find: String,
        replacement: String,
    ) = RegexScript(
        id = id,
        scriptName = id,
        findRegex = find,
        replaceString = replacement,
        placement = listOf(RegexPlacement.UserInput.value),
    )

    private fun scoped(
        script: RegexScript,
        scope: RegexScriptScope = RegexScriptScope.Global,
        order: Int = 0,
    ) = ScopedRegexScript(script = script, scope = scope, order = order)

    private fun context(
        macros: Map<String, String> = emptyMap(),
        mode: RegexExecutionMode = RegexExecutionMode.Source,
        depth: Int? = null,
        isEdit: Boolean = false,
    ) = RegexExecutionContext(
        placement = RegexPlacement.UserInput,
        mode = mode,
        depth = depth,
        isEdit = isEdit,
        macros = macros,
    )
}
