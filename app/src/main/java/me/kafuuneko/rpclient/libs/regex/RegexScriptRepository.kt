package me.kafuuneko.rpclient.libs.regex

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.RegexCharacterAuthorization

/**
 * Regex 脚本和角色授权的统一 Room 仓库。
 *
 * Global 与 Character 脚本均保存在 `regex_scripts`；角色脚本通过可空的角色外键区分作用域。
 * 旧数据由 Application 初始化阶段统一迁移，此处不再回退读取 Kotpref 或角色扩展 JSON。
 */
class RegexScriptRepository(
    private val mContext: Context,
    private val mGson: Gson,
    private val mAppDatabase: AppDatabase,
    private val mCodec: RegexScriptCodec
) {
    private val mRegexDao = mAppDatabase.getRegexScriptDao()
    /** 所有脚本 read-modify-write 共用同一把锁，避免导入、编辑和拖拽提交互相覆盖。 */
    private val mMutationMutex = Mutex()

    /** 读取稳定目标的最新脚本。 */
    suspend fun getScripts(target: RegexScriptTarget): List<RegexScript> {
        return getScriptsFromRoom(target)
    }

    /**
     * 对稳定目标执行原子 read-modify-write，并返回提交后的权威列表。
     *
     * 目标列表使用一次 Room 事务整体替换，脚本顺序与数据库状态始终同时提交。
     */
    suspend fun updateScripts(
        target: RegexScriptTarget,
        transform: (List<RegexScript>) -> List<RegexScript>,
    ): List<RegexScript> {
        return mMutationMutex.withLock {
            mAppDatabase.withTransaction {
                val characterId = target.characterId
                if (characterId != null
                    && mAppDatabase.getCharacterDao().getCharacterById(characterId) == null
                ) {
                    return@withTransaction emptyList()
                }
                val updated = transform(getScriptsFromRoom(target))
                    .toList()
                    .normalizeRegexScriptIds()
                replaceScripts(target, updated)
                updated
            }
        }
    }

    /** 判断指定角色的内嵌脚本是否已由用户明确授权。 */
    suspend fun isCharacterAuthorized(characterId: Long): Boolean {
        return mRegexDao.isCharacterAuthorized(characterId)
    }

    /** 更新角色 Regex 执行授权；角色删除时对应记录会由数据库级联清理。 */
    suspend fun setCharacterAuthorized(characterId: Long, authorized: Boolean) {
        if (authorized) {
            mAppDatabase.withTransaction {
                if (mAppDatabase.getCharacterDao().getCharacterById(characterId) != null) {
                    mRegexDao.authorizeCharacter(RegexCharacterAuthorization(characterId))
                }
            }
        } else {
            mRegexDao.revokeCharacterAuthorization(characterId)
        }
    }

    /**
     * 收集本轮可执行脚本，并按 Global、输入角色顺序生成稳定执行链。
     *
     * 未授权的角色脚本继续保存在 Room，但不会进入返回列表。
     */
    suspend fun activeScripts(characters: List<Character>): List<ScopedRegexScript> {
        return mAppDatabase.withTransaction {
            val distinctCharacters = characters.distinctBy { character -> character.id }
            val characterIds = distinctCharacters.map { character -> character.id }
            val globalScripts = mRegexDao.getGlobalScripts().map { regexScriptEntity -> regexScriptEntity.toDomain() }
            val authorizedIds = if (characterIds.isEmpty()) {
                emptySet()
            } else {
                mRegexDao.getAuthorizedCharacterIds(characterIds).toSet()
            }
            val characterScripts = if (characterIds.isEmpty()) {
                emptyMap()
            } else {
                mRegexDao.getCharacterScripts(characterIds)
                    .groupBy { regexScriptEntity -> requireNotNull(regexScriptEntity.characterId) }
            }

            buildList {
                globalScripts.forEachIndexed { index, script ->
                    add(
                        element = ScopedRegexScript(
                            script = script,
                            scope = RegexScriptScope.Global,
                            ownerName = "Global",
                            order = index,
                        ),
                    )
                }
                var characterOrder = 0
                distinctCharacters.forEach { character ->
                    if (character.id !in authorizedIds) return@forEach
                    characterScripts[character.id].orEmpty().forEach { entity ->
                        add(
                            element = ScopedRegexScript(
                                script = entity.toDomain(),
                                scope = RegexScriptScope.Character,
                                ownerId = character.id.toString(),
                                ownerName = character.name,
                                order = characterOrder++,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** 解析外部 JSON 文件中的脚本。 */
    fun importScripts(json: String): List<RegexScript> = mCodec.parseList(json)

    /** 导出带缩进的 SillyTavern 兼容脚本 JSON。 */
    fun exportScripts(scripts: List<RegexScript>): String =
        mCodec.toJson(scripts = scripts, pretty = true)

    /** 从文档 URI 读取并解析脚本。 */
    fun importFromUri(uri: Uri): List<RegexScript> {
        val json = mContext.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Cannot read regex script file")
        return importScripts(json)
    }

    /** 将脚本写入用户选择的文档 URI。 */
    fun exportToUri(uri: Uri, scripts: List<RegexScript>) {
        mContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(exportScripts(scripts))
        } ?: error("Cannot open regex script export destination")
    }

    private suspend fun getScriptsFromRoom(target: RegexScriptTarget): List<RegexScript> {
        val entities = when (target.scope) {
            RegexScriptScope.Global -> mRegexDao.getGlobalScripts()
            RegexScriptScope.Character -> mRegexDao.getCharacterScripts(
                characterId = requireNotNull(target.characterId),
            )
        }
        return entities.map { it.toDomain() }
    }

    private suspend fun replaceScripts(
        target: RegexScriptTarget,
        scripts: List<RegexScript>,
    ) {
        val characterId = when (target.scope) {
            RegexScriptScope.Global -> {
                mRegexDao.deleteGlobalScripts()
                null
            }
            RegexScriptScope.Character -> requireNotNull(target.characterId).also { id ->
                mRegexDao.deleteCharacterScripts(id)
            }
        }
        if (scripts.isNotEmpty()) {
            mRegexDao.insertScripts(
                scripts = scripts.mapIndexed { index, script ->
                    script.toEntity(characterId, index, mGson)
                },
            )
        }
    }
}
