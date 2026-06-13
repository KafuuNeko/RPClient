package me.kafuuneko.rpclient.libs.regex

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository

class RegexScriptRepository(
    private val mGson: Gson,
    private val mCharacterRepository: CharacterRepository,
    private val mCodec: RegexScriptCodec
) {
    fun getGlobalScripts(): List<RegexScript> =
        mCodec.parseList(readSafely { AppModel.globalRegexScriptsJson })

    fun saveGlobalScripts(scripts: List<RegexScript>) {
        AppModel.globalRegexScriptsJson = mCodec.toJson(scripts)
    }

    fun getPresetScripts(): List<RegexScript> =
        mCodec.parseList(readSafely { AppModel.presetRegexScriptsJson })

    fun savePresetScripts(scripts: List<RegexScript>) {
        AppModel.presetRegexScriptsJson = mCodec.toJson(scripts)
    }

    fun isPresetAuthorized(): Boolean =
        runCatching { AppModel.presetRegexScriptsAuthorized }.getOrDefault(false)

    fun setPresetAuthorized(authorized: Boolean) {
        AppModel.presetRegexScriptsAuthorized = authorized
    }

    fun getCharacterScripts(character: Character): List<RegexScript> {
        val extensions = parseExtensions(character.extensionsJson)
        return extensions.get("regex_scripts")
            ?.takeIf { it.isJsonArray }
            ?.let { mCodec.parseList(mGson.toJson(it)) }
            .orEmpty()
    }

    suspend fun saveCharacterScripts(characterId: Long, scripts: List<RegexScript>) {
        val character = mCharacterRepository.getCharacterById(characterId) ?: return
        val extensions = parseExtensions(character.extensionsJson)
        extensions.add(
            "regex_scripts",
            JsonParser.parseString(mCodec.toJson(scripts)).asJsonArray
        )
        mCharacterRepository.updateCharacter(
            character.copy(extensionsJson = mGson.toJson(extensions))
        )
    }

    fun isCharacterAuthorized(characterId: Long): Boolean =
        characterId in authorizedCharacterIds()

    fun setCharacterAuthorized(characterId: Long, authorized: Boolean) {
        val ids = authorizedCharacterIds().toMutableSet()
        if (authorized) ids += characterId else ids -= characterId
        AppModel.authorizedCharacterRegexIdsJson = mGson.toJson(ids.sorted())
    }

    fun activeScripts(characters: List<Character>): List<ScopedRegexScript> {
        return buildList {
            getGlobalScripts().forEachIndexed { index, script ->
                add(
                    ScopedRegexScript(
                        script = script,
                        scope = RegexScriptScope.Global,
                        ownerName = "Global",
                        order = index
                    )
                )
            }
            if (isPresetAuthorized()) {
                getPresetScripts().forEachIndexed { index, script ->
                    add(
                        ScopedRegexScript(
                            script = script,
                            scope = RegexScriptScope.Preset,
                            ownerName = "Prompt Preset",
                            order = index
                        )
                    )
                }
            }
            var characterOrder = 0
            characters.distinctBy { it.id }.forEach { character ->
                if (!isCharacterAuthorized(character.id)) return@forEach
                getCharacterScripts(character).forEach { script ->
                    add(
                        ScopedRegexScript(
                            script = script,
                            scope = RegexScriptScope.Character,
                            ownerId = character.id.toString(),
                            ownerName = character.name,
                            order = characterOrder++
                        )
                    )
                }
            }
        }
    }

    fun importScripts(json: String): List<RegexScript> = mCodec.parseList(json)

    fun exportScripts(scripts: List<RegexScript>): String = mCodec.toJson(scripts, pretty = true)

    private fun authorizedCharacterIds(): Set<Long> {
        val json = readSafely { AppModel.authorizedCharacterRegexIdsJson }
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull {
                runCatching { it.asLong }.getOrNull()
            }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun parseExtensions(json: String): JsonObject {
        return runCatching {
            JsonParser.parseString(json).asJsonObject.deepCopy()
        }.getOrDefault(JsonObject())
    }

    private fun readSafely(block: () -> String): String =
        runCatching(block).getOrDefault("[]")
}
