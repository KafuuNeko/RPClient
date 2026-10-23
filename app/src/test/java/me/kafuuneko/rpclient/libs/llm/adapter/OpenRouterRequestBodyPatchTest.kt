package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterRequestBodyPatchTest {
    @Test
    fun shortcutPreservesUnknownProviderPreferences() {
        val original =
            """{"session_id":"${'$'}rpclient.routing_session_id","provider":{"sort":"throughput","quantizations":["fp8"]},"preset":"roleplay"}"""

        val updated = original
            .withOpenRouterPreferredProviderEnabled(enabled = true)
            .withOpenRouterPreferredProvider("deepinfra")
            .withOpenRouterFallbacks(allowed = false)
        val root = JsonParser.parseString(updated).asJsonObject

        assertEquals("roleplay", root.get("preset").asString)
        assertEquals(
            "${'$'}rpclient.routing_session_id",
            root.get("session_id").asString,
        )
        assertEquals("throughput", root.getAsJsonObject("provider").get("sort").asString)
        assertEquals(
            "fp8",
            root.getAsJsonObject("provider").getAsJsonArray("quantizations")[0].asString,
        )
        assertFalse(updated.readOpenRouterRoutingPreferences().allowFallbacks)
        assertEquals("deepinfra", updated.readOpenRouterRoutingPreferences().preferredProvider)
    }

    @Test
    fun automaticModeRemovesOnlyShortcutFields() {
        val updated =
            """{"provider":{"order":["deepinfra"],"allow_fallbacks":false,"sort":"price"}}"""
                .withOpenRouterPreferredProviderEnabled(enabled = false)
        val provider = JsonParser.parseString(updated).asJsonObject.getAsJsonObject("provider")

        assertFalse(provider.has("order"))
        assertFalse(provider.has("allow_fallbacks"))
        assertTrue(provider.has("sort"))
    }

    @Test
    fun invalidFallbackTypeUsesSafeDisplayDefault() {
        val patch = """{"provider":{"allow_fallbacks":1}}"""
        val preferences = patch.readOpenRouterRoutingPreferences()

        assertTrue(preferences.allowFallbacks)
        assertFalse(patch.hasValidOpenRouterRoutingPreferences())
    }

    @Test
    fun validatesKnownFieldsWhileAllowingMergePatchNulls() {
        assertTrue(
            """{"provider":{"order":["deepinfra","together"]}}"""
                .hasValidOpenRouterRoutingPreferences(),
        )
        assertTrue(
            """{"provider":{"order":null,"allow_fallbacks":null}}"""
                .hasValidOpenRouterRoutingPreferences(),
        )
        assertFalse(
            """{"provider":{"order":[]}}"""
                .hasValidOpenRouterRoutingPreferences(),
        )
        assertFalse(
            """{"provider":{"order":[""]}}"""
                .hasValidOpenRouterRoutingPreferences(),
        )
    }
}
