package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiModelCatalogClientTest {
    @Test
    fun onlyGenerateContentModelsAreReturnedWithNormalizedIds() {
        val page = parseGeminiModelCatalogPage(
            raw = """
                {
                  "models": [
                    {
                      "name": "models/gemini-chat",
                      "displayName": "Gemini Chat",
                      "inputTokenLimit": 1000000,
                      "outputTokenLimit": 65536,
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-embedding",
                      "supportedGenerationMethods": ["embedContent"]
                    }
                  ],
                  "nextPageToken": "next-page"
                }
            """.trimIndent(),
        )

        val model = page.models.single()
        assertEquals("gemini-chat", model.id)
        assertEquals("Gemini Chat", model.displayName)
        assertEquals(1000000, model.contextTokens)
        assertEquals(65536, model.maxOutputTokens)
        assertEquals("next-page", page.nextPageToken)
    }

    @Test
    fun supportedActionsAliasIsAccepted() {
        val page = parseGeminiModelCatalogPage(
            raw = """
                {
                  "models": [
                    {
                      "name": "models/gemini-alias",
                      "supportedActions": ["generateContent"]
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals("gemini-alias", page.models.single().id)
    }
}
