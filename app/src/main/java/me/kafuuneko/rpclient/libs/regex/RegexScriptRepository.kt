package me.kafuuneko.rpclient.libs.regex

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository

/**
 * Regex 脚本的持久化与授权仓库。
 *
 * 全局和预设脚本保存于 Kotpref；角色脚本保存于角色卡 `extensions.regex_scripts`。
 * 预设和角色卡脚本默认不执行，只有用户授权后才会由 [activeScripts] 返回。
 */
class RegexScriptRepository(
    private val mGson: Gson,
    private val mCharacterRepository: CharacterRepository,
    private val mCodec: RegexScriptCodec
) {
    /** 读取全局脚本，损坏配置按空列表处理。 */
    fun getGlobalScripts(): List<RegexScript> =
        mCodec.parseList(readSafely { AppModel.globalRegexScriptsJson })

    /** 覆盖保存全局脚本及其当前排序。 */
    fun saveGlobalScripts(scripts: List<RegexScript>) {
        AppModel.globalRegexScriptsJson = mCodec.toJson(scripts)
    }

    /** 读取当前 Prompt 预设脚本。 */
    fun getPresetScripts(): List<RegexScript> =
        mCodec.parseList(readSafely { AppModel.presetRegexScriptsJson })

    /** 覆盖保存当前 Prompt 预设脚本。 */
    fun savePresetScripts(scripts: List<RegexScript>) {
        AppModel.presetRegexScriptsJson = mCodec.toJson(scripts)
    }

    /** 判断预设脚本是否已由用户显式授权。 */
    fun isPresetAuthorized(): Boolean =
        runCatching { AppModel.presetRegexScriptsAuthorized }.getOrDefault(false)

    /** 更新预设脚本授权状态。 */
    fun setPresetAuthorized(authorized: Boolean) {
        AppModel.presetRegexScriptsAuthorized = authorized
    }

    /** 从角色扩展字段读取内嵌脚本，不改变角色卡原始其他扩展。 */
    fun getCharacterScripts(character: Character): List<RegexScript> {
        val extensions = parseExtensions(character.extensionsJson)
        return extensions.get("regex_scripts")
            ?.takeIf { it.isJsonArray }
            ?.let { mCodec.parseList(mGson.toJson(it)) }
            .orEmpty()
    }

    /** 将角色脚本写回扩展字段，并保留角色卡中的其他未知扩展。 */
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

    /** 判断指定角色卡的内嵌脚本是否获准执行。 */
    fun isCharacterAuthorized(characterId: Long): Boolean =
        characterId in authorizedCharacterIds()

    /** 增删角色授权集合；授权状态与角色卡内容分离保存。 */
    fun setCharacterAuthorized(characterId: Long, authorized: Boolean) {
        val ids = authorizedCharacterIds().toMutableSet()
        if (authorized) ids += characterId else ids -= characterId
        AppModel.authorizedCharacterRegexIdsJson = mGson.toJson(ids.sorted())
    }

    /**
     * 收集本轮可执行脚本，并生成全局、预设、角色卡的稳定顺序。
     *
     * 未授权的预设或角色脚本仍被原样保存，但不会出现在返回列表中。
     */
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

    /** 解析外部 JSON 文件中的脚本。 */
    fun importScripts(json: String): List<RegexScript> = mCodec.parseList(json)

    /** 导出带缩进的脚本 JSON，供文件分享或迁移。 */
    fun exportScripts(scripts: List<RegexScript>): String = mCodec.toJson(scripts, pretty = true)

    /** 容错解析已授权角色 ID 集合。 */
    private fun authorizedCharacterIds(): Set<Long> {
        val json = readSafely { AppModel.authorizedCharacterRegexIdsJson }
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull {
                runCatching { it.asLong }.getOrNull()
            }.toSet()
        }.getOrDefault(emptySet())
    }

    /** 解析角色扩展对象；损坏数据以空对象兜底，避免管理页崩溃。 */
    private fun parseExtensions(json: String): JsonObject {
        return runCatching {
            JsonParser.parseString(json).asJsonObject.deepCopy()
        }.getOrDefault(JsonObject())
    }

    private fun readSafely(block: () -> String): String =
        runCatching(block).getOrDefault("[]")
}
