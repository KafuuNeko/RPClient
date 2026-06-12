package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

class LorebookCodec(
    private val gson: Gson
) {
    fun parseLorebook(json: String): CharacterBookImport {
        val root = JsonParser.parseString(json).asJsonObject
        
        val name = root.optString("name")
        val lorebook = Lorebook(
            name = name.ifBlank { "Imported World Book" },
            description = root.optString("description"),
            scanDepth = root.optInt("scanDepth", 2),
            tokenBudget = root.optInt("tokenBudget", 25),
            recursiveScanning = root.optBoolean("recursiveScanning", false),
            extensionsJson = gson.toJson(root.optJsonObject("extensions") ?: JsonObject())
        )
        
        val entriesObj = root.optJsonObject("entries")
        val entries = mutableListOf<LorebookEntry>()
        entriesObj?.entrySet()?.forEach { (uid, element) ->
            if (element.isJsonObject) {
                entries.add(element.asJsonObject.toLorebookEntry())
            }
        }
        return CharacterBookImport(lorebook, entries)
    }

    fun toLorebookJson(lorebook: Lorebook, entries: List<LorebookEntry>): String {
        val book = JsonObject()
        book.addProperty("name", lorebook.name)
        book.addProperty("description", lorebook.description)
        book.addProperty("scanDepth", lorebook.scanDepth)
        book.addProperty("tokenBudget", lorebook.tokenBudget)
        book.addProperty("recursiveScanning", lorebook.recursiveScanning)
        book.add("extensions", parseObjectOrEmpty(lorebook.extensionsJson))
        
        val entriesObj = JsonObject()
        entries.forEachIndexed { index, entry ->
            entriesObj.add(index.toString(), entry.toStWorldBookEntry(index))
        }
        book.add("entries", entriesObj)
        return gson.toJson(book)
    }

    private fun JsonObject.toLorebookEntry(): LorebookEntry {
        val extensions = optJsonObject("extensions") ?: JsonObject()
        return LorebookEntry(
            lorebookId = 0L,
            name = optString("comment"),
            keywords = gson.toJson(getAsJsonArray("key")?.toStringList().orEmpty()),
            secondaryKeywords = gson.toJson(getAsJsonArray("keysecondary")?.toStringList().orEmpty()),
            constant = optBoolean("constant", false),
            order = optInt("order", 100),
            depth = optInt("depth", 4),
            category = "[]",
            content = optString("content"),
            disabled = optBoolean("disable", false),
            position = optInt("position", LorebookEntry.POSITION_AT_DEPTH),
            role = optInt("role", LorebookEntry.ROLE_SYSTEM),
            probability = optInt("probability", 100),
            ignoreBudget = extensions.optBoolean("ignore_budget", false),
            scanDepth = optNullableInt("scanDepth"),
            selectiveLogic = optInt("selectiveLogic", LorebookEntry.LOGIC_AND_ANY),
            matchWholeWords = optNullableBoolean("matchWholeWords"),
            caseSensitive = optNullableBoolean("caseSensitive"),
            useGroupScoring = optBoolean("useGroupScoring", false),
            group = optString("group"),
            groupOverride = optBoolean("groupOverride", false),
            groupWeight = optNullableInt("groupWeight"),
            preventRecursion = optBoolean("preventRecursion", false),
            delayUntilRecursion = optBoolean("delayUntilRecursion", false),
            sticky = optNullableInt("sticky"),
            cooldown = optNullableInt("cooldown"),
            delay = optNullableInt("delay"),
            outletName = extensions.optString("outlet_name"),
            triggers = gson.toJson(extensions.getAsJsonArray("triggers")?.toStringList().orEmpty()),
            matchPersonaDescription = extensions.optBoolean("match_persona_description", false),
            matchCharacterDescription = extensions.optBoolean("match_character_description", false),
            matchCharacterPersonality = extensions.optBoolean("match_character_personality", false),
            matchCharacterDepthPrompt = extensions.optBoolean("match_character_depth_prompt", false),
            matchScenario = extensions.optBoolean("match_scenario", false),
            matchCreatorNotes = extensions.optBoolean("match_creator_notes", false),
            extensionsJson = gson.toJson(extensions),
            rawJson = gson.toJson(this)
        )
    }

    private fun LorebookEntry.toStWorldBookEntry(uid: Int): JsonObject {
        val entry = parseObjectOrEmpty(rawJson)
        entry.addProperty("uid", uid)
        entry.add("key", parseArrayOrEmpty(keywords))
        entry.add("keysecondary", parseArrayOrEmpty(secondaryKeywords))
        entry.addProperty("comment", name)
        entry.addProperty("content", content)
        entry.addProperty("constant", constant)
        entry.addProperty("vectorized", false)
        entry.addProperty("selective", getSecondaryKeywordList().isNotEmpty())
        entry.addProperty("selectiveLogic", selectiveLogic)
        entry.addProperty("addMemo", true)
        entry.addProperty("order", order)
        entry.addProperty("position", position)
        entry.addProperty("disable", disabled)
        entry.addProperty("excludeRecursion", false)
        entry.addProperty("preventRecursion", preventRecursion)
        entry.addProperty("delayUntilRecursion", delayUntilRecursion)
        entry.addProperty("probability", probability)
        entry.addProperty("useProbability", probability < 100)
        entry.addProperty("depth", depth)
        entry.addProperty("group", group)
        entry.addProperty("groupOverride", groupOverride)
        groupWeight?.let { entry.addProperty("groupWeight", it) }
        scanDepth?.let { entry.addProperty("scanDepth", it) }
        caseSensitive?.let { entry.addProperty("caseSensitive", it) }
        matchWholeWords?.let { entry.addProperty("matchWholeWords", it) }
        entry.addProperty("useGroupScoring", useGroupScoring)
        entry.addProperty("automationId", "")
        entry.addProperty("role", role)
        sticky?.let { entry.addProperty("sticky", it) }
        cooldown?.let { entry.addProperty("cooldown", it) }
        delay?.let { entry.addProperty("delay", it) }
        entry.addProperty("displayIndex", uid)
        
        val extensions = parseObjectOrEmpty(extensionsJson)
        extensions.addProperty("outlet_name", outletName)
        extensions.add("triggers", parseArrayOrEmpty(triggers))
        extensions.addProperty("match_character_description", matchCharacterDescription)
        extensions.addProperty("match_persona_description", matchPersonaDescription)
        extensions.addProperty("match_character_personality", matchCharacterPersonality)
        extensions.addProperty("match_character_depth_prompt", matchCharacterDepthPrompt)
        extensions.addProperty("match_scenario", matchScenario)
        extensions.addProperty("match_creator_notes", matchCreatorNotes)
        entry.add("extensions", extensions)
        
        return entry
    }

    private fun JsonObject.optJsonObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonNull) null else element.asJsonObject
    }

    private fun JsonObject.optString(name: String): String {
        return get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
    }

    private fun JsonObject.optInt(name: String, default: Int): Int {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: default
    }

    private fun JsonObject.optNullableInt(name: String): Int? {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()
    }

    private fun JsonObject.optBoolean(name: String, default: Boolean): Boolean {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull() ?: default
    }

    private fun JsonObject.optNullableBoolean(name: String): Boolean? {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
    }

    private fun JsonArray.toStringList(): List<String> {
        return mapNotNull { element -> element.takeIf { !it.isJsonNull }?.asString }
    }

    private fun parseArrayOrEmpty(json: String): JsonArray {
        return runCatching { JsonParser.parseString(json).asJsonArray }.getOrDefault(JsonArray())
    }

    private fun parseObjectOrEmpty(json: String): JsonObject {
        return runCatching { JsonParser.parseString(json).asJsonObject }.getOrDefault(JsonObject())
    }
}
