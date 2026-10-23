package me.kafuuneko.rpclient.libs.regex

import org.junit.Assert.assertEquals
import org.junit.Test

class RegexMessageProcessorTest {
    private val mProcessor = RegexMessageProcessor(RegexScriptRuntime(RegexScriptEngine()))

    @Test
    fun userInputRunsSlashCommandBeforeUserPlacement() {
        val scripts = listOf(
            scoped(script("slash", "/\\/wave/g", "hello", RegexPlacement.SlashCommand)),
            scoped(script("user", "/hello/g", "hi")),
        )

        val result = mProcessor.applyUserInput("/wave", scripts, emptyMap())

        assertEquals("hi", result)
    }

    @Test
    fun generatedSourceUsesUserOrCharacterPipeline() {
        val userScript = scoped(script("user", "/raw/g", "user"))
        val aiScript = scoped(script("ai", "/raw/g", "ai", RegexPlacement.AiResponse))

        assertEquals(
            "user",
            mProcessor.applyGenerated(
                input = "raw",
                source = RegexMessageSource.User,
                scripts = listOf(userScript),
                macros = emptyMap(),
            ),
        )
        assertEquals(
            "ai",
            mProcessor.applyGenerated(
                input = "raw",
                source = RegexMessageSource.Character,
                scripts = listOf(aiScript),
                macros = emptyMap(),
            ),
        )
    }

    @Test
    fun displayKeepsReasoningAndBodyPlacementsSeparate() {
        val scripts = listOf(
            scoped(
                script = script("body", "/answer/g", "shown", RegexPlacement.AiResponse)
                    .copy(markdownOnly = true),
            ),
            scoped(
                script = script("reasoning", "/secret/g", "hidden", RegexPlacement.Reasoning)
                    .copy(markdownOnly = true),
                order = 1,
            ),
        )

        val result = mProcessor.applyDisplay(
            input = "<think>secret</think>answer",
            source = RegexMessageSource.Character,
            scripts = scripts,
            macros = emptyMap(),
        )

        assertEquals("<think>hidden</think>shown", result)
    }

    private fun script(
        id: String,
        find: String,
        replacement: String,
        placement: RegexPlacement = RegexPlacement.UserInput,
    ) = RegexScript(
        id = id,
        scriptName = id,
        findRegex = find,
        replaceString = replacement,
        placement = listOf(placement.value),
    )

    private fun scoped(
        script: RegexScript,
        order: Int = 0,
    ) = ScopedRegexScript(
        script = script,
        scope = RegexScriptScope.Global,
        order = order,
    )
}
