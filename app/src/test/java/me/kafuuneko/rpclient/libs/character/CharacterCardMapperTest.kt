package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterCardMapperTest {
    private val gson = Gson()
    private val mapper = CharacterCardMapper(gson)

    @Test
    fun v2JsonImportsCharacterBookAndPreservesExtensions() {
        val json = """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "Iris",
                "description": "Desc",
                "personality": "Kind",
                "scenario": "At sea",
                "first_mes": "Hello",
                "mes_example": "<START>\nIris: Example",
                "creator_notes": "Metadata only",
                "system_prompt": "Main",
                "post_history_instructions": "After",
                "alternate_greetings": ["Alt"],
                "tags": ["tag"],
                "creator": "Maker",
                "character_version": "1.2",
                "extensions": {
                  "third_party": {"kept": true},
                  "depth_prompt": {"prompt": "Stay close", "depth": 2, "role": "assistant"}
                },
                "character_book": {
                  "name": "Iris Book",
                  "description": "Lore",
                  "scan_depth": 3,
                  "token_budget": 25,
                  "recursive_scanning": true,
                  "extensions": {"book_extra": true},
                  "entries": [
                    {
                      "keys": ["harbor"],
                      "secondary_keys": ["fog"],
                      "content": "Harbor lore",
                      "enabled": true,
                      "insertion_order": 42,
                      "constant": false,
                      "case_sensitive": true,
                      "extensions": {
                        "position": 4,
                        "depth": 3,
                        "role": "user",
                        "probability": 80,
                        "custom_entry": true
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = mapper.parseCharacter(json)
        val entry = parsed.embeddedLorebook!!.entries.single()

        assertEquals("Iris", parsed.character.name)
        assertEquals("Maker", parsed.character.creator)
        assertEquals("1.2", parsed.character.characterVersion)
        assertEquals("Stay close", parsed.character.depthPromptPrompt)
        assertEquals(2, parsed.character.depthPromptDepth)
        assertEquals(LorebookEntry.ROLE_ASSISTANT, parsed.character.depthPromptRole)
        assertEquals("Iris Book", parsed.embeddedLorebook.lorebook.name)
        assertEquals("""["harbor"]""", entry.keywords)
        assertEquals(LorebookEntry.ROLE_USER, entry.role)
        assertEquals(80, entry.probability)
        assertEquals(true, JsonParser.parseString(parsed.character.extensionsJson).asJsonObject.has("third_party"))

        val exported = JsonParser.parseString(
            mapper.toV2Json(parsed.character, parsed.embeddedLorebook.lorebook, parsed.embeddedLorebook.entries)
        ).asJsonObject
        val data = exported.getAsJsonObject("data")

        assertEquals("chara_card_v2", exported.get("spec").asString)
        assertEquals(true, data.getAsJsonObject("extensions").has("third_party"))
        assertEquals(true, data.getAsJsonObject("character_book").getAsJsonArray("entries").size() == 1)
    }

    @Test
    fun pngRoundTripsCharacterCardMetadata() {
        val json = """{"spec":"chara_card_v2","data":{"name":"Png Card"}}"""
        val png = CharacterCardPngCodec.writeCharacterJson(OnePixelPng, json)
        val decoded = CharacterCardPngCodec.readCharacterJson(png)
        val parsed = mapper.parseCharacter(decoded)

        assertEquals("Png Card", parsed.character.name)
    }

    private companion object {
        val OnePixelPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
    }
}
