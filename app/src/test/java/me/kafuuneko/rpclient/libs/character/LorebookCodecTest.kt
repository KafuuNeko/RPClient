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
    fun blankImportedBookNameUsesStableDefault() {
        val imported = codec.parseLorebook("""{"name":" ","entries":{}}""")

        assertEquals("Imported world book", imported.lorebook.name)
    }

    @Test
    fun missingBookBudgetFollowsGlobalAndIsOmittedOnExport() {
        val imported = codec.parseLorebook("""{"name":"Imported","entries":{}}""")

        val exported = JsonParser.parseString(
            codec.toLorebookJson(
                imported.lorebook,
                emptyList()
            )
        ).asJsonObject

        assertEquals(0, imported.lorebook.tokenBudget)
        assertFalse(exported.has("tokenBudget"))
    }

    @Test
    fun importedLorebookSurvivesEditAndExportWithoutCompatibilityLoss() {
        val imported = codec.parseLorebook(
            json = """
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
                  "vectorized": true,
                  "addMemo": false,
                  "excludeRecursion": true,
                  "automationId": "third-party-automation",
                  "depth": 2,
                  "role": 0,
                  "group": "campus",
                  "groupOverride": true,
                  "groupWeight": 250,
                  "useGroupScoring": true,
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
            """.trimIndent(),
        )
        val editedBook = WorldBookEditForm.from(imported.lorebook, imported.entries)
            .copy(name = "Renamed")
            .toLorebook()
        val entryForm = WorldBookEntryEditForm.from(imported.entries.single())
        assertEquals("campus", entryForm.group)
        assertEquals("250", entryForm.groupWeight)
        assertTrue(entryForm.useGroupScoring)
        assertTrue(entryForm.groupOverride)
        assertTrue(entryForm.matchCreatorNotes)
        val editedEntry = entryForm
            .copy(content = "Updated content")
            .toLorebookEntryOrNull()
            ?: error("Entry form should be valid")

        val exported = JsonParser.parseString(
            codec.toLorebookJson(
                editedBook,
                listOf(editedEntry)
            )
        ).asJsonObject
        val exportedEntry = exported.getAsJsonObject("entries").getAsJsonObject("0")
        val extensions = exportedEntry.getAsJsonObject("extensions")

        assertEquals("Renamed", exported.get("name").asString)
        assertEquals("Description", exported.get("description").asString)
        assertEquals(7, exported.get("scanDepth").asInt)
        assertEquals(256, exported.get("tokenBudget").asInt)
        assertTrue(exported.get("recursiveScanning").asBoolean)
        assertTrue(
            exported
                .getAsJsonObject("extensions")
                .getAsJsonObject("book_extension")
                .get("enabled")
                .asBoolean,
        )
        assertEquals("Updated content", exportedEntry.get("content").asString)
        assertTrue(exportedEntry.getAsJsonObject("custom_top_level").get("preserve").asBoolean)
        assertTrue(exportedEntry.get("matchWholeWords")?.isJsonNull != false)
        assertFalse(exportedEntry.has("caseSensitive"))
        assertEquals("continue", exportedEntry.getAsJsonArray("triggers")[0].asString)
        assertEquals("campus", exportedEntry.get("group").asString)
        assertEquals(250, exportedEntry.get("groupWeight").asInt)
        assertTrue(exportedEntry.get("useGroupScoring").asBoolean)
        assertTrue(exportedEntry.get("groupOverride").asBoolean)
        assertTrue(exportedEntry.get("vectorized").asBoolean)
        assertFalse(exportedEntry.get("addMemo").asBoolean)
        assertTrue(exportedEntry.get("excludeRecursion").asBoolean)
        assertEquals("third-party-automation", exportedEntry.get("automationId").asString)
        assertEquals("continue", extensions.getAsJsonArray("triggers")[0].asString)
        assertTrue(extensions.get("match_creator_notes").asBoolean)
        assertEquals("kept", extensions.get("custom_extension").asString)
    }
}
