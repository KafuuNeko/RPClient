package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogInvalidResponseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAICompatibleModelCatalogClientTest {
    @Test
    fun standardResponseKeepsModelIdsWithoutAssumingCapabilities() {
        val models = parseOpenAIModelCatalog(
            raw = """
                {
                  "object": "list",
                  "data": [
                    {"id": "chat-model", "owned_by": "provider"},
                    {"id": "embedding-model", "owned_by": "provider"}
                  ]
                }
            """.trimIndent(),
            includeExtendedMetadata = false,
        )

        assertEquals(listOf("chat-model", "embedding-model"), models.map { it.id })
        assertNull(models.first().contextTokens)
        assertEquals(emptySet<String>(), models.first().supportedParameters)
    }

    @Test
    fun openRouterExtensionsArePreservedWhenRequested() {
        val model = parseOpenAIModelCatalog(
            raw = """
                {
                  "data": [
                    {
                      "id": "author/model",
                      "name": "Readable Model",
                      "description": "Description",
                      "context_length": 128000,
                      "supported_parameters": ["temperature", "top_p"],
                      "top_provider": {"max_completion_tokens": 8192}
                    }
                  ]
                }
            """.trimIndent(),
            includeExtendedMetadata = true,
        ).single()

        assertEquals("Readable Model", model.displayName)
        assertEquals(128000, model.contextTokens)
        assertEquals(8192, model.maxOutputTokens)
        assertEquals(setOf("temperature", "top_p"), model.supportedParameters)
    }

    @Test(expected = LLMModelCatalogInvalidResponseException::class)
    fun missingDataArrayIsRejected() {
        parseOpenAIModelCatalog(raw = "{}", includeExtendedMetadata = false)
    }
}
