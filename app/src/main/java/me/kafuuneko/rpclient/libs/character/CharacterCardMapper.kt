package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.utils.toJsonString

class CharacterCardMapper(
    private val gson: Gson
) {
    /**
     * 解析 Tavern 生态角色卡 JSON。
     *
     * V2 卡片使用根节点 data；旧 V1 卡片直接把角色字段放在根节点。
     * 未识别的 extensions 会原样保存，确保导入再导出时不破坏第三方工具字段。
     */
    fun parseCharacter(json: String, avatar: String = "", characterLorebookId: Long = 0L): CharacterCardImport {
        val root = JsonParser.parseString(json).asJsonObject
        val data = root.getAsJsonObject("data")
        return if (data != null) {
            CharacterCardImport(
                character = data.toCharacter(avatar, characterLorebookId),
                embeddedLorebook = data.getAsJsonObject("character_book")?.toLorebookImport()
            )
        } else {
            CharacterCardImport(
                character = root.toV1Character(avatar, characterLorebookId),
                embeddedLorebook = null
            )
        }
    }

    fun toV2Json(
        character: Character,
        lorebook: Lorebook? = null,
        entries: List<LorebookEntry> = emptyList()
    ): String {
        // 导出统一使用 Character Card V2；角色绑定世界书会重新嵌入 data.character_book。
        val data = JsonObject()
        data.addProperty("name", character.name)
        data.addProperty("description", character.description)
        data.addProperty("personality", character.personality)
        data.addProperty("scenario", character.scenario)
        data.addProperty("first_mes", character.getFirstMessageList().firstOrNull().orEmpty())
        data.addProperty("mes_example", character.examplesOfDialogue)
        data.addProperty("creator_notes", character.creatorNotes)
        data.addProperty("system_prompt", character.systemPrompt)
        data.addProperty("post_history_instructions", character.postHistoryInstructions)
        data.add("alternate_greetings", parseArrayOrEmpty(character.alternateGreetings))
        data.add("tags", parseArrayOrEmpty(character.characterTags))
        data.addProperty("creator", character.creator)
        data.addProperty("character_version", character.characterVersion)
        data.add("extensions", parseObjectOrEmpty(character.extensionsJson).withDepthPrompt(character))
        if (lorebook != null) {
            data.add("character_book", lorebook.toCharacterBook(entries))
        }

        val root = JsonObject()
        root.addProperty("spec", "chara_card_v2")
        root.addProperty("spec_version", "2.0")
        root.add("data", data)
        return gson.toJson(root)
    }

    private fun JsonObject.toCharacter(
        avatar: String,
        characterLorebookId: Long
    ): Character {
        val extensions = getAsJsonObject("extensions") ?: JsonObject()
        val depthPrompt = extensions.getAsJsonObject("depth_prompt") ?: JsonObject()
        val firstMes = optString("first_mes")
        val alternates = getAsJsonArray("alternate_greetings")?.toStringList().orEmpty()
        return Character(
            name = optString("name"),
            avatar = avatar,
            characterTags = gson.toJsonString(getAsJsonArray("tags")?.toStringList().orEmpty()),
            description = optString("description"),
            creatorNotes = optString("creator_notes"),
            personality = optString("personality"),
            scenario = optString("scenario"),
            firstMessages = listOf(firstMes).filter { it.isNotBlank() }.joinToString("<START>"),
            examplesOfDialogue = optString("mes_example"),
            postHistoryInstructions = optString("post_history_instructions"),
            systemPrompt = optString("system_prompt"),
            creator = optString("creator"),
            characterVersion = optString("character_version"),
            alternateGreetings = gson.toJsonString(alternates),
            extensionsJson = gson.toJson(extensions),
            depthPromptPrompt = depthPrompt.optString("prompt"),
            depthPromptDepth = depthPrompt.optInt("depth", 4),
            depthPromptRole = depthPrompt.optRole("role"),
            characterLorebookId = characterLorebookId
        )
    }

    private fun JsonObject.toV1Character(
        avatar: String,
        characterLorebookId: Long
    ): Character {
        return Character(
            name = optString("name").ifBlank { optString("char_name") },
            avatar = avatar,
            characterTags = gson.toJsonString(getAsJsonArray("tags")?.toStringList().orEmpty()),
            description = optString("description").ifBlank { optString("char_persona") },
            creatorNotes = optString("creatorcomment").ifBlank { optString("creator_notes") },
            personality = optString("personality"),
            scenario = optString("scenario").ifBlank { optString("world_scenario") },
            firstMessages = optString("first_mes").ifBlank { optString("char_greeting") },
            examplesOfDialogue = optString("mes_example").ifBlank { optString("example_dialogue") },
            postHistoryInstructions = "",
            creator = optString("creator"),
            characterLorebookId = characterLorebookId
        )
    }

    private fun JsonObject.toLorebookImport(): CharacterBookImport {
        val name = optString("name")
        val lorebook = Lorebook(
            name = name,
            description = optString("description"),
            scanDepth = optInt("scan_depth", 2),
            tokenBudget = optInt("token_budget", 25),
            recursiveScanning = optBoolean("recursive_scanning", false),
            extensionsJson = gson.toJson(getAsJsonObject("extensions") ?: JsonObject())
        )
        val entries = getAsJsonArray("entries")?.mapIndexed { index, element ->
            val entry = element.asJsonObject
            entry.toLorebookEntry(index)
        }.orEmpty()
        return CharacterBookImport(lorebook, entries)
    }

    private fun JsonObject.toLorebookEntry(index: Int): LorebookEntry {
        val extensions = getAsJsonObject("extensions") ?: JsonObject()
        // ST 世界书条目的许多高级字段没有统一顶层字段，这里优先从 extensions 读取。
        return LorebookEntry(
            lorebookId = 0L,
            name = optString("name").ifBlank { optString("comment") },
            keywords = gson.toJsonString(getAsJsonArray("keys")?.toStringList().orEmpty()),
            secondaryKeywords = gson.toJsonString(getAsJsonArray("secondary_keys")?.toStringList().orEmpty()),
            constant = optBoolean("constant", false),
            order = optInt("insertion_order", optInt("priority", index)),
            depth = extensions.optInt("depth", 4),
            category = "[]",
            content = optString("content"),
            disabled = !optBoolean("enabled", true),
            position = extensions.optInt("position", optPosition()),
            role = extensions.optRole("role"),
            probability = extensions.optInt("probability", 100),
            ignoreBudget = extensions.optBoolean("ignore_budget", false),
            scanDepth = extensions.optNullableInt("scan_depth"),
            selectiveLogic = extensions.optInt("selectiveLogic", LorebookEntry.LOGIC_AND_ANY),
            matchWholeWords = extensions.optNullableBoolean("match_whole_words"),
            caseSensitive = if (has("case_sensitive")) optBoolean("case_sensitive", false) else extensions.optNullableBoolean("case_sensitive"),
            useGroupScoring = extensions.optBoolean("use_group_scoring", false),
            group = extensions.optString("group"),
            groupOverride = extensions.optBoolean("group_override", false),
            groupWeight = extensions.optNullableInt("group_weight"),
            preventRecursion = extensions.optBoolean("prevent_recursion", false),
            delayUntilRecursion = extensions.optBoolean("delay_until_recursion", false),
            sticky = extensions.optNullableInt("sticky"),
            cooldown = extensions.optNullableInt("cooldown"),
            delay = extensions.optNullableInt("delay"),
            outletName = extensions.optString("outlet_name"),
            triggers = gson.toJsonString(extensions.getAsJsonArray("triggers")?.toStringList().orEmpty()),
            matchCharacterDescription = extensions.optBoolean("match_character_description", false),
            matchCharacterPersonality = extensions.optBoolean("match_character_personality", false),
            matchCharacterDepthPrompt = extensions.optBoolean("match_character_depth_prompt", false),
            matchScenario = extensions.optBoolean("match_scenario", false),
            matchCreatorNotes = extensions.optBoolean("match_creator_notes", false),
            extensionsJson = gson.toJson(extensions),
            rawJson = gson.toJson(this)
        )
    }

    private fun Lorebook.toCharacterBook(entries: List<LorebookEntry>): JsonObject {
        val book = JsonObject()
        book.addProperty("name", name)
        book.addProperty("description", description)
        book.addProperty("scan_depth", scanDepth)
        book.addProperty("token_budget", tokenBudget)
        book.addProperty("recursive_scanning", recursiveScanning)
        book.add("extensions", parseObjectOrEmpty(extensionsJson))
        val array = JsonArray()
        entries.forEach { array.add(it.toCharacterBookEntry()) }
        book.add("entries", array)
        return book
    }

    private fun LorebookEntry.toCharacterBookEntry(): JsonObject {
        val entry = JsonObject()
        entry.add("keys", parseArrayOrEmpty(keywords))
        entry.addProperty("content", content)
        entry.add("extensions", toExtensions())
        entry.addProperty("enabled", !disabled)
        entry.addProperty("insertion_order", order)
        caseSensitive?.let { entry.addProperty("case_sensitive", it) }
        entry.addProperty("name", name)
        entry.addProperty("priority", order)
        entry.addProperty("id", id)
        entry.addProperty("comment", name)
        entry.addProperty("selective", getSecondaryKeywordList().isNotEmpty())
        entry.add("secondary_keys", parseArrayOrEmpty(secondaryKeywords))
        entry.addProperty("constant", constant)
        entry.addProperty("position", if (position == LorebookEntry.POSITION_BEFORE) "before_char" else "after_char")
        return entry
    }

    private fun LorebookEntry.toExtensions(): JsonObject {
        val extensions = parseObjectOrEmpty(extensionsJson)
        // 在保留原 extensions 的基础上覆盖当前 App 已支持的字段，避免导出旧值。
        extensions.addProperty("position", position)
        extensions.addProperty("depth", depth)
        extensions.addProperty("role", role)
        extensions.addProperty("probability", probability)
        extensions.addProperty("ignore_budget", ignoreBudget)
        extensions.addProperty("selectiveLogic", selectiveLogic)
        scanDepth?.let { extensions.addProperty("scan_depth", it) }
        matchWholeWords?.let { extensions.addProperty("match_whole_words", it) }
        caseSensitive?.let { extensions.addProperty("case_sensitive", it) }
        extensions.addProperty("group", group)
        extensions.addProperty("group_override", groupOverride)
        groupWeight?.let { extensions.addProperty("group_weight", it) }
        extensions.addProperty("prevent_recursion", preventRecursion)
        extensions.addProperty("delay_until_recursion", delayUntilRecursion)
        sticky?.let { extensions.addProperty("sticky", it) }
        cooldown?.let { extensions.addProperty("cooldown", it) }
        delay?.let { extensions.addProperty("delay", it) }
        extensions.addProperty("outlet_name", outletName)
        extensions.add("triggers", parseArrayOrEmpty(triggers))
        extensions.addProperty("match_character_description", matchCharacterDescription)
        extensions.addProperty("match_character_personality", matchCharacterPersonality)
        extensions.addProperty("match_character_depth_prompt", matchCharacterDepthPrompt)
        extensions.addProperty("match_scenario", matchScenario)
        extensions.addProperty("match_creator_notes", matchCreatorNotes)
        return extensions
    }

    private fun JsonObject.withDepthPrompt(character: Character): JsonObject {
        val depthPrompt = getAsJsonObject("depth_prompt") ?: JsonObject()
        depthPrompt.addProperty("prompt", character.depthPromptPrompt)
        depthPrompt.addProperty("depth", character.depthPromptDepth)
        depthPrompt.addProperty("role", when (character.depthPromptRole) {
            LorebookEntry.ROLE_USER -> "user"
            LorebookEntry.ROLE_ASSISTANT -> "assistant"
            else -> "system"
        })
        add("depth_prompt", depthPrompt)
        return this
    }

    private fun JsonObject.optPosition(): Int {
        return when (optString("position")) {
            "before_char" -> LorebookEntry.POSITION_BEFORE
            "after_char" -> LorebookEntry.POSITION_AFTER
            else -> LorebookEntry.POSITION_AFTER
        }
    }

    private fun JsonObject.optRole(name: String): Int {
        return when (val value = get(name)?.takeIf { !it.isJsonNull }) {
            null -> LorebookEntry.ROLE_SYSTEM
            else -> when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asInt.coerceIn(0, 2)
                value.asString.equals("user", ignoreCase = true) -> LorebookEntry.ROLE_USER
                value.asString.equals("assistant", ignoreCase = true) -> LorebookEntry.ROLE_ASSISTANT
                else -> LorebookEntry.ROLE_SYSTEM
            }
        }
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

data class CharacterCardImport(
    val character: Character,
    val embeddedLorebook: CharacterBookImport?
)

data class CharacterBookImport(
    val lorebook: Lorebook,
    val entries: List<LorebookEntry>
)
