package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookCodecTest {
    private val codec = LorebookCodec(Gson())

    @Test
    fun importedLorebookSurvivesEditAndExportWithoutCompatibilityLoss() {
        val imported = codec.parseLorebook(
            """
            {
              "name": "Imported",
              "description": "Description",
              "scanDepth": 7,
              "tokenBudget": 256,
              "recursiveScanning": true,
              "extensions": {
                "book_extension": {
                  "enabled": true
                }
              },
              "entries": {
                "42": {
                  "uid": 42,
                  "key": ["alpha"],
                  "keysecondary": ["beta"],
                  "comment": "Entry",
                  "content": "Content",
                  "constant": false,
                  "order": 90,
                  "position": 4,
                  "disable": false,
                  "depth": 2,
                  "role": 0,
                  "triggers": ["continue"],
                  "matchWholeWords": null,
                  "custom_top_level": {
                    "preserve": true
                  },
                  "extensions": {
                    "match_creator_notes": true,
                    "custom_extension": "kept"
                  }
                }
              }
            }
            """.trimIndent()
        )
        val editedBook = WorldBookEditForm.from(imported.lorebook, imported.entries)
            .copy(name = "Renamed")
            .toLorebook()
        val editedEntry = WorldBookEntryEditForm.from(imported.entries.single())
            .copy(content = "Updated content")
            .toLorebookEntryOrNull()
            ?: error("Entry form should be valid")

        val exported = JsonParser.parseString(
            codec.toLorebookJson(editedBook, listOf(editedEntry))
        ).asJsonObject
        val exportedEntry = exported.getAsJsonObject("entries").getAsJsonObject("0")
        val extensions = exportedEntry.getAsJsonObject("extensions")

        assertEquals("Renamed", exported.get("name").asString)
        assertEquals("Description", exported.get("description").asString)
        assertEquals(7, exported.get("scanDepth").asInt)
        assertEquals(256, exported.get("tokenBudget").asInt)
        assertTrue(exported.get("recursiveScanning").asBoolean)
        assertTrue(
            exported.getAsJsonObject("extensions")
                .getAsJsonObject("book_extension")
                .get("enabled")
                .asBoolean
        )
        assertEquals("Updated content", exportedEntry.get("content").asString)
        assertTrue(exportedEntry.getAsJsonObject("custom_top_level").get("preserve").asBoolean)
        assertTrue(exportedEntry.get("matchWholeWords")?.isJsonNull != false)
        assertFalse(exportedEntry.has("caseSensitive"))
        assertEquals("continue", exportedEntry.getAsJsonArray("triggers")[0].asString)
        assertEquals("continue", extensions.getAsJsonArray("triggers")[0].asString)
        assertTrue(extensions.get("match_creator_notes").asBoolean)
        assertEquals("kept", extensions.get("custom_extension").asString)
    }
}
