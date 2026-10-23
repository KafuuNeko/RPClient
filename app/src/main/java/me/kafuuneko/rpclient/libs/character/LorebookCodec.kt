package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * SillyTavern 独立世界书 JSON 编解码器。
 *
 * 导入时将 entries 对象转换为本地实体；导出时以 [LorebookEntry.rawJson] 为底稿
 * 覆盖当前已支持字段，从而尽量保留未知的第三方扩展。
 */
class LorebookCodec(
    private val mGson: Gson
) {
    /** 解析独立世界书，返回尚未写入数据库的书籍与条目集合。 */
    fun parseLorebook(json: String): CharacterBookImport {
        val root = JsonParser.parseString(json).asJsonObject

        val name = root.optString("name")
        val lorebook = Lorebook(
            name = name.ifBlank { DefaultNames.IMPORTED_WORLD_BOOK },
            description = root.optString("description"),
            scanDepth = root.optInt(name = "scanDepth", default = 2),
            tokenBudget = root.optInt(name = "tokenBudget", default = 0),
            recursiveScanning = root.optBoolean(name = "recursiveScanning", default = false),
            extensionsJson = mGson.toJson(root.optJsonObject("extensions") ?: JsonObject()),
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

    /** 将本地世界书导出为 SillyTavern 使用的 entries 对象结构。 */
    fun toLorebookJson(lorebook: Lorebook, entries: List<LorebookEntry>): String {
        val book = JsonObject()
        book.addProperty("name", lorebook.name)
        book.addProperty("description", lorebook.description)
        book.addProperty("scanDepth", lorebook.scanDepth)
        // 0 是本地“跟随全局”哨兵值，导出时省略以免第三方客户端将其解释成零预算。
        if (lorebook.tokenBudget > 0) book.addProperty("tokenBudget", lorebook.tokenBudget)
        book.addProperty("recursiveScanning", lorebook.recursiveScanning)
        book.add("extensions", parseObjectOrEmpty(lorebook.extensionsJson))

        val entriesObj = JsonObject()
        entries.forEachIndexed { index, entry ->
            entriesObj.add(index.toString(), entry.toStWorldBookEntry(index))
        }
        book.add("entries", entriesObj)
        return mGson.toJson(book)
    }

    private fun JsonObject.toLorebookEntry(): LorebookEntry {
        val extensions = optJsonObject("extensions") ?: JsonObject()
        return LorebookEntry(
            lorebookId = 0L,
            name = optString("comment"),
            keywords = mGson.toJson(getAsJsonArray("key")?.toStringList().orEmpty()),
            secondaryKeywords = mGson.toJson(getAsJsonArray("keysecondary")?.toStringList().orEmpty()),
            constant = optBoolean(name = "constant", default = false),
            order = optInt(name = "order", default = 100),
            depth = optInt(name = "depth", default = 4),
            category = "[]",
            content = optString("content"),
            disabled = optBoolean(name = "disable", default = false),
            position = optInt("position", LorebookEntry.POSITION_AT_DEPTH),
            role = optInt("role", LorebookEntry.ROLE_SYSTEM),
            probability = optInt(name = "probability", default = 100),
            ignoreBudget = optBoolean(
                name = "ignoreBudget",
                default = extensions.optBoolean(name = "ignore_budget", default = false),
            ),
            scanDepth = optNullableInt("scanDepth"),
            selectiveLogic = optInt("selectiveLogic", LorebookEntry.LOGIC_AND_ANY),
            matchWholeWords = optNullableBoolean("matchWholeWords"),
            caseSensitive = optNullableBoolean("caseSensitive"),
            useGroupScoring = optBoolean(name = "useGroupScoring", default = false),
            group = optString("group"),
            groupOverride = optBoolean(name = "groupOverride", default = false),
            groupWeight = optNullableInt("groupWeight"),
            preventRecursion = optBoolean(name = "preventRecursion", default = false),
            delayUntilRecursion = optBoolean(name = "delayUntilRecursion", default = false),
            sticky = optNullableInt("sticky"),
            cooldown = optNullableInt("cooldown"),
            delay = optNullableInt("delay"),
            outletName = extensions.optString("outlet_name"),
            triggers = mGson.toJson(
                (getAsJsonArray("triggers") ?: extensions.getAsJsonArray("triggers"))
                    ?.toStringList()
                    .orEmpty(),
            ),
            matchPersonaDescription = extensions.optBoolean(name = "match_persona_description", default = false),
            matchCharacterDescription = extensions.optBoolean(name = "match_character_description", default = false),
            matchCharacterPersonality = extensions.optBoolean(name = "match_character_personality", default = false),
            matchCharacterDepthPrompt = extensions.optBoolean(name = "match_character_depth_prompt", default = false),
            matchScenario = extensions.optBoolean(name = "match_scenario", default = false),
            matchCreatorNotes = extensions.optBoolean(name = "match_creator_notes", default = false),
            extensionsJson = mGson.toJson(extensions),
            rawJson = mGson.toJson(this),
        )
    }

    /**
     * 将本地条目覆盖回 SillyTavern 世界书对象。
     *
     * 以导入时保存的 [LorebookEntry.rawJson] 为底稿，只覆盖 RPClient 已理解的字段，
     * 未知第三方字段继续保留；高级匹配字段同时写入 extensions，兼容仍从扩展读取的客户端。
     */
    private fun LorebookEntry.toStWorldBookEntry(uid: Int): JsonObject {
        val entry = parseObjectOrEmpty(rawJson)
        entry.addProperty("uid", uid)
        entry.add("key", parseArrayOrEmpty(keywords))
        entry.add("keysecondary", parseArrayOrEmpty(secondaryKeywords))
        entry.addProperty("comment", name)
        entry.addProperty("content", content)
        entry.addProperty("constant", constant)
        if (!entry.has("vectorized")) entry.addProperty("vectorized", false)
        entry.addProperty("selective", getSecondaryKeywordList().isNotEmpty())
        entry.addProperty("selectiveLogic", selectiveLogic)
        if (!entry.has("addMemo")) entry.addProperty("addMemo", true)
        entry.addProperty("order", order)
        entry.addProperty("position", position)
        entry.addProperty("disable", disabled)
        entry.addProperty("ignoreBudget", ignoreBudget)
        if (!entry.has("excludeRecursion")) entry.addProperty("excludeRecursion", false)
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
        entry.add("triggers", parseArrayOrEmpty(triggers))
        if (!entry.has("automationId")) entry.addProperty("automationId", "")
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
        return runCatching { get(name)?.takeIf { element -> !element.isJsonNull }?.asInt }.getOrNull() ?: default
    }

    private fun JsonObject.optNullableInt(name: String): Int? {
        return runCatching { get(name)?.takeIf { element -> !element.isJsonNull }?.asInt }.getOrNull()
    }

    private fun JsonObject.optBoolean(name: String, default: Boolean): Boolean {
        return runCatching { get(name)?.takeIf { element -> !element.isJsonNull }?.asBoolean }.getOrNull() ?: default
    }

    private fun JsonObject.optNullableBoolean(name: String): Boolean? {
        return runCatching { get(name)?.takeIf { element -> !element.isJsonNull }?.asBoolean }.getOrNull()
    }

    private fun JsonArray.toStringList(): List<String> {
        return mapNotNull { element -> element.takeIf { element -> !element.isJsonNull }?.asString }
    }

    private fun parseArrayOrEmpty(json: String): JsonArray {
        return runCatching { JsonParser.parseString(json).asJsonArray }.getOrDefault(JsonArray())
    }

    private fun parseObjectOrEmpty(json: String): JsonObject {
        return runCatching { JsonParser.parseString(json).asJsonObject }.getOrDefault(JsonObject())
    }
}
