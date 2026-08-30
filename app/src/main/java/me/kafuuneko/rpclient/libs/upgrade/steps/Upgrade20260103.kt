package me.kafuuneko.rpclient.libs.upgrade.steps

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.mergeLegacyGlobalAndPreset
import me.kafuuneko.rpclient.libs.regex.normalizeRegexScriptIds
import me.kafuuneko.rpclient.libs.regex.toEntity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.RegexCharacterAuthorization
import me.kafuuneko.rpclient.libs.upgrade.AppUpgrade

/**
 * 将 20260103 之前分散保存的 Regex 数据统一迁入 Room。
 *
 * 旧 Global 与 Preset 来自 SharedPreferences，Character 脚本来自角色扩展 JSON；
 * 迁移后 Room 成为脚本与角色授权的唯一权威来源。
 */
class Upgrade20260103(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson,
    private val mRegexCodec: RegexScriptCodec
) : AppUpgrade {
    override val targetVersionCode: Int = 20260103

    private val mRegexDao = mAppDatabase.getRegexScriptDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()

    /**
     * Room 写入和角色扩展清理在同一事务中完成。目标作用域已有脚本时不重复导入，
     * 从而允许数据库提交后、检查点写入前进程退出所造成的安全重试。
     */
    override suspend fun migrate() {
        val legacy = readLegacyRegexSnapshot()
        mAppDatabase.withTransaction {
            importApplicationScripts(legacy)
            importCharacterScripts()
            importCharacterAuthorizations(legacy.authorizedCharacterIds)
        }
    }

    /**
     * 删除仅供旧版本使用的偏好。
     *
     * SharedPreferences 删除天然幂等；同步提交确保方法返回时清理结果已经落盘。
     */
    override suspend fun cleanup() {
        val committed = AppModel.preferences.edit()
            .remove(PREF_GLOBAL_SCRIPTS)
            .remove(PREF_PRESET_SCRIPTS)
            .remove(PREF_PRESET_AUTHORIZED)
            .remove(PREF_AUTHORIZED_CHARACTER_IDS)
            .remove(PREF_OLD_SCOPE_MIGRATION_VERSION)
            .commit()
        check(committed) {
            "Failed to persist 20260103 legacy Regex cleanup"
        }
    }

    private suspend fun importApplicationScripts(legacy: LegacyRegexSnapshot) {
        if (mRegexDao.getGlobalScripts().isNotEmpty()) return
        val merged = mergeLegacyGlobalAndPreset(
            globalScripts = mRegexCodec.parseList(legacy.globalScriptsJson),
            presetScripts = mRegexCodec.parseList(legacy.presetScriptsJson),
            presetAuthorized = legacy.presetAuthorized
        )
        if (merged.isNotEmpty()) {
            mRegexDao.insertScripts(
                merged.mapIndexed { index, script ->
                    script.toEntity(characterId = null, sortOrder = index, gson = mGson)
                }
            )
        }
    }

    private suspend fun importCharacterScripts() {
        mCharacterDao.getAllCharacters().forEach { character ->
            val extraction = mRegexCodec.extractFromCharacterExtensions(character.extensionsJson)
            if (!extraction.hadRegexScripts) return@forEach
            if (mRegexDao.getCharacterScripts(character.id).isEmpty()) {
                insertCharacterScripts(character.id, extraction.scripts)
            }
            mCharacterDao.update(
                character.copy(extensionsJson = extraction.extensionsJson)
            )
        }
    }

    private suspend fun insertCharacterScripts(
        characterId: Long,
        scripts: List<RegexScript>
    ) {
        val normalized = scripts.normalizeRegexScriptIds()
        if (normalized.isNotEmpty()) {
            mRegexDao.insertScripts(
                normalized.mapIndexed { index, script ->
                    script.toEntity(
                        characterId = characterId,
                        sortOrder = index,
                        gson = mGson
                    )
                }
            )
        }
    }

    private suspend fun importCharacterAuthorizations(authorizedIds: Set<Long>) {
        if (authorizedIds.isEmpty()) return
        val existingCharacterIds = mCharacterDao.getAllCharacters()
            .mapTo(mutableSetOf()) { it.id }
        val authorizations = authorizedIds
            .filter { it in existingCharacterIds }
            .map(::RegexCharacterAuthorization)
        if (authorizations.isNotEmpty()) {
            mRegexDao.authorizeCharacters(authorizations)
        }
    }

    private fun readLegacyRegexSnapshot(): LegacyRegexSnapshot {
        val preferences = AppModel.preferences
        val authorizedIds = runCatching {
            JsonParser.parseString(
                preferences.getString(PREF_AUTHORIZED_CHARACTER_IDS, "[]").orEmpty()
            ).asJsonArray.mapNotNull { element ->
                runCatching { element.asLong }.getOrNull()
            }.toSet()
        }.getOrDefault(emptySet())
        return LegacyRegexSnapshot(
            globalScriptsJson = preferences.getString(PREF_GLOBAL_SCRIPTS, "[]").orEmpty(),
            presetScriptsJson = preferences.getString(PREF_PRESET_SCRIPTS, "[]").orEmpty(),
            presetAuthorized = preferences.getBoolean(PREF_PRESET_AUTHORIZED, false),
            authorizedCharacterIds = authorizedIds
        )
    }

    private data class LegacyRegexSnapshot(
        /** 旧版本全局正则脚本的序列化快照。 */
        val globalScriptsJson: String,
        /** 旧版本预设正则脚本的序列化快照。 */
        val presetScriptsJson: String,
        /** 旧版本预设脚本是否已经获得执行授权。 */
        val presetAuthorized: Boolean,
        /** 已授权执行相关正则脚本的角色 ID 集合。 */
        val authorizedCharacterIds: Set<Long>
    )

    private companion object {
        const val PREF_GLOBAL_SCRIPTS = "globalRegexScriptsJson"
        const val PREF_PRESET_SCRIPTS = "presetRegexScriptsJson"
        const val PREF_PRESET_AUTHORIZED = "presetRegexScriptsAuthorized"
        const val PREF_AUTHORIZED_CHARACTER_IDS = "authorizedCharacterRegexIdsJson"
        const val PREF_OLD_SCOPE_MIGRATION_VERSION = "regexScopeMigrationVersion"
    }
}
