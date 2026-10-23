package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_GROK_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBodyPatchTest {
    @Test
    fun recursivelyMergesObjectsAndReplacesArrays() {
        val result = JsonParser.parseString(
            mergeRequestBodyJson(
                baseJson = """{"provider":{"order":["a"],"sort":"price"},"plugins":[{"id":"x"}]}""",
                patchJson = """{"provider":{"order":["deepinfra"],"allow_fallbacks":false},"plugins":[{"id":"y"}]}""",
                protectedPaths = emptySet(),
            ),
        ).asJsonObject

        assertEquals("price", result.getAsJsonObject("provider").get("sort").asString)
        assertEquals("deepinfra", result.getAsJsonObject("provider").getAsJsonArray("order")[0].asString)
        assertFalse(result.getAsJsonObject("provider").get("allow_fallbacks").asBoolean)
        assertEquals("y", result.getAsJsonArray("plugins")[0].asJsonObject.get("id").asString)
    }

    @Test
    fun nullRemovesAField() {
        val result = JsonParser.parseString(
            mergeRequestBodyJson(
                baseJson = """{"provider":{"sort":"price","quantizations":["fp8"]}}""",
                patchJson = """{"provider":{"sort":null}}""",
                protectedPaths = emptySet(),
            ),
        ).asJsonObject

        assertFalse(result.getAsJsonObject("provider").has("sort"))
        assertTrue(result.getAsJsonObject("provider").has("quantizations"))
    }

    @Test
    fun rejectsProtectedNestedPathButAllowsSibling() {
        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"generationConfig":{"maxOutputTokens":1}}""",
                protectedPaths = setOf("generationConfig.maxOutputTokens"),
            ).isFailure,
        )
        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"generationConfig":{"candidateCount":2}}""",
                protectedPaths = setOf("generationConfig.maxOutputTokens"),
            ).isSuccess,
        )
        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"generationConfig":null}""",
                protectedPaths = setOf("generationConfig.maxOutputTokens"),
            ).isFailure,
        )
    }

    @Test
    fun rejectsNonObjectRoot() {
        assertTrue(validateRequestBodyPatch("[]", emptySet()).isFailure)
    }

    @Test
    fun allowsUserOwnedOpenRouterSessionAndReasoningFields() {
        val protectedPaths = protectedRequestBodyPaths(LLMProviderProtocol.OpenAICompatible)

        assertTrue(validateRequestBodyPatch("""{"session_id":"override"}""", protectedPaths).isSuccess)
        assertTrue(validateRequestBodyPatch("""{"reasoning":{"effort":"high"}}""", protectedPaths).isSuccess)
        assertTrue(validateRequestBodyPatch("""{"reasoning_effort":"high"}""", protectedPaths).isSuccess)
        assertTrue(validateRequestBodyPatch("""{"thinking":{"type":"enabled"}}""", protectedPaths).isSuccess)
        assertTrue(validateRequestBodyPatch("""{"provider":{"order":["deepinfra"]}}""", protectedPaths).isSuccess)
    }

    @Test
    fun streamUsageOptionIsControlledByProviderCapability() {
        val protectedPaths = protectedRequestBodyPaths(LLMProviderProtocol.OpenAICompatible)

        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"stream_options":{"include_usage":true}}""",
                protectedPaths = protectedPaths,
            ).isFailure,
        )
        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"stream_options":{"other_option":true}}""",
                protectedPaths = protectedPaths,
            ).isSuccess,
        )
    }

    @Test
    fun allowsDefaultProviderReasoningTemplatesForEveryProtocol() {
        val openAIProtectedPaths = protectedRequestBodyPaths(protocol = LLMProviderProtocol.OpenAICompatible)
        listOf(
            DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON,
            DEFAULT_GROK_REQUEST_BODY_PATCH_JSON,
            DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON,
        ).forEach { template ->
            assertTrue(validateRequestBodyPatch(template, openAIProtectedPaths).isSuccess)
        }
        assertTrue(
            validateRequestBodyPatch(
                patchJson = DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON,
                protectedPaths = protectedRequestBodyPaths(LLMProviderProtocol.AnthropicMessages),
            ).isSuccess,
        )
        assertTrue(
            validateRequestBodyPatch(
                patchJson = DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON,
                protectedPaths = protectedRequestBodyPaths(LLMProviderProtocol.Gemini),
            ).isSuccess,
        )
    }

    @Test
    fun resolvesExactSystemVariableWithoutSubstringSubstitution() {
        val result = JsonParser.parseString(
            mergeRequestBodyJson(
                baseJson = """{"metadata":{"keep":true}}""",
                patchJson =
                    """{"metadata":{"session":"${'$'}rpclient.routing_session_id","literal":"prefix ${'$'}rpclient.routing_session_id"}}""",
                protectedPaths = emptySet(),
                systemVariables = mapOf(LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID to JsonPrimitive("routing-42")),
            ),
        ).asJsonObject.getAsJsonObject("metadata")

        assertEquals("routing-42", result.get("session").asString)
        assertEquals(
            "prefix ${'$'}rpclient.routing_session_id",
            result.get("literal").asString,
        )
    }

    @Test
    fun missingSystemVariableRemovesObjectFieldWithMergePatchSemantics() {
        val result = JsonParser.parseString(
            mergeRequestBodyJson(
                baseJson = """{"metadata":{"session":"old","keep":true}}""",
                patchJson =
                    """{"metadata":{"session":"${'$'}rpclient.routing_session_id"}}""",
                protectedPaths = emptySet(),
            ),
        ).asJsonObject.getAsJsonObject("metadata")

        assertFalse(result.has("session"))
        assertTrue(result.get("keep").asBoolean)
    }

    @Test
    fun rejectsUnknownFullSystemVariable() {
        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"value":"${'$'}rpclient.unknown"}""",
                protectedPaths = emptySet(),
            ).isFailure,
        )
        assertTrue(
            validateRequestBodyPatch(
                patchJson = """{"value":"prefix ${'$'}rpclient.unknown"}""",
                protectedPaths = emptySet(),
            ).isSuccess,
        )
    }
}
