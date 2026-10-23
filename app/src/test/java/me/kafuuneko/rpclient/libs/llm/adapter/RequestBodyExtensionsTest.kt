package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RequestBodyExtensionsTest {
    @Test
    fun advancedJsonCanInjectSessionAndReuseIt() {
        val protectedPaths = protectedRequestBodyPaths(LLMProviderProtocol.OpenAICompatible)
        val result = JsonParser.parseString(
            mergeRequestBodyExtensionsJson(
                baseJson = """{"model":"test","messages":[]}""",
                patchJson =
                    """{"session_id":"${'$'}rpclient.routing_session_id","metadata":{"conversation":"${'$'}rpclient.routing_session_id"}}""",
                protectedPaths = protectedPaths,
                routingSessionId = "routing-42",
            ),
        ).asJsonObject

        assertEquals("routing-42", result.get("session_id").asString)
        assertEquals(
            "routing-42",
            result.getAsJsonObject("metadata").get("conversation").asString,
        )
    }

    @Test
    fun emptyAdvancedJsonDoesNotInjectSession() {
        val result = JsonParser.parseString(
            mergeRequestBodyExtensionsJson(
                baseJson = """{"model":"test","messages":[]}""",
                patchJson = "{}",
                protectedPaths = protectedRequestBodyPaths(LLMProviderProtocol.OpenAICompatible),
                routingSessionId = "routing-42",
            ),
        ).asJsonObject

        assertFalse(result.has("session_id"))
    }

}
