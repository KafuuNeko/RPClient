package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogInvalidResponseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicModelCatalogClientTest {
    @Test
    fun responseExposesNextCursorOnlyWhenMorePagesExist() {
        val page = parseAnthropicModelCatalogPage(
            raw = """
                {
                  "data": [
                    {
                      "id": "claude-model",
                      "display_name": "Claude Model"
                    }
                  ],
                  "has_more": true,
                  "last_id": "cursor"
                }
            """.trimIndent(),
        )

        assertEquals("claude-model", page.models.single().id)
        assertEquals("Claude Model", page.models.single().displayName)
        assertEquals("cursor", page.nextAfterId)
    }

    @Test
    fun finalPageDoesNotReuseLastId() {
        val page = parseAnthropicModelCatalogPage(
            raw = """
                {
                  "data": [],
                  "has_more": false,
                  "last_id": "ignored"
                }
            """.trimIndent(),
        )

        assertNull(page.nextAfterId)
    }

    @Test(expected = LLMModelCatalogInvalidResponseException::class)
    fun missingCursorOnNonFinalPageIsRejected() {
        parseAnthropicModelCatalogPage("""{"data":[],"has_more":true}""")
    }
}
