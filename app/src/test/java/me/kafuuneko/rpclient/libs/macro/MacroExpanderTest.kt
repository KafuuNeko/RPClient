package me.kafuuneko.rpclient.libs.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 验证引用求值、作用域、循环和展开规模这些核心算法边界。 */
class MacroExpanderTest {
    private val mExpander = MacroExpander()

    @Test
    fun resolvesDependenciesAndPreservesLiteralValues() {
        val scope = MacroScope(
            mapOf(
                "original" to MacroValue.Template("{{persona}} / {{draft}}"),
                "persona" to MacroValue.Template("{{user}} knows {{char}}"),
                "user" to MacroValue.Literal("Alex"),
                "char" to MacroValue.Literal("Alice"),
                "draft" to MacroValue.Literal("{{user}} <CHAR> {{original}}"),
            )
        )
        assertEquals(
            "Alex knows Alice / {{user}} <CHAR> {{original}} / {{ UNKNOWN }}",
            mExpander.expand("{{original}} / {{ UNKNOWN }}", scope).text,
        )
    }

    @Test
    fun distinguishesSameNameBindingsInDifferentScopes() {
        val parent = MacroScope(
            mapOf(
                "field" to MacroValue.Template("{{char}}"),
                "char" to MacroValue.Literal("Alice"),
            )
        )
        val child = parent.withValues(
            mapOf(
                "field" to MacroValue.Template("{{field}}", parent),
                "char" to MacroValue.Literal("Bob"),
            )
        )
        assertEquals("Alice/Bob", mExpander.expand("{{field}}/{{char}}", child).text)
    }

    @Test
    fun reportsOnlyReachableCyclesWithTheirReferencePath() {
        val scope = MacroScope(
            mapOf(
                "A" to MacroValue.Template("{{ B }}"),
                "B" to MacroValue.Template("{{a}}"),
                "self" to MacroValue.Template("{{self}}"),
            )
        )
        assertEquals("ordinary text", mExpander.expand("ordinary text", scope).text)
        val cycle = assertThrows(MacroExpansionException.Cycle::class.java) {
            mExpander.expand("{{A}}", scope)
        }
        assertEquals(listOf("a", "b", "a"), cycle.path.map { it.reference.name })
        assertThrows(MacroExpansionException.Cycle::class.java) {
            mExpander.expand("{{self}}", scope)
        }
    }

    @Test
    fun reusesCompletedDependenciesOnlyWithinOneExpansion() {
        var lookups = 0
        val scope = MacroScope(mapOf("a" to MacroValue.Template("{{b}}{{b}}"))) {
            if (it.name == "b") MacroValue.Literal((++lookups).toString()) else null
        }
        assertEquals("11/11", mExpander.expand("{{a}}/{{a}}", scope).text)
        assertEquals(1, lookups)
        assertEquals("22", mExpander.expand("{{a}}", scope).text)
    }

    @Test
    fun handlesDeepGrowingTemplatesWithoutRecursiveStackOrCopiedPrefixes() {
        val length = 20_000
        val values = (0 until length).associate { index ->
            "node$index" to MacroValue.Template("x{{node${index + 1}}}")
        } + ("node$length" to MacroValue.Literal(""))
        val result = mExpander.expand("{{node0}}", MacroScope(values), length)
        assertEquals("x".repeat(length), result.text)
    }

    @Test
    fun stopsExponentialOutputBeforeMaterializingIt() {
        val values = (0 until 40).associate { index ->
            "node$index" to MacroValue.Template("{{node${index + 1}}}{{node${index + 1}}}")
        } + ("node40" to MacroValue.Literal("x"))
        assertThrows(MacroExpansionException.OutputLimit::class.java) {
            mExpander.expand("{{node0}}", MacroScope(values), 4096)
        }
    }
}
