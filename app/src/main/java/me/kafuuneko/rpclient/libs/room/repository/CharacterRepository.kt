package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.normalizeRegexScriptIds
import me.kafuuneko.rpclient.libs.regex.toEntity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.CharacterLLMProviderAssociation
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.toStringList

/** 角色实体读写及标签、开场白序列化的业务仓库。 */
class CharacterRepository(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson,
    private val mRegexCodec: RegexScriptCodec,
    private val mImages: MessageImageRepository
) {
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mCharacterLLMProviderAssociationDao =
        mAppDatabase.getCharacterLLMProviderAssociationDao()
    private val mRegexDao = mAppDatabase.getRegexScriptDao()

    /**
     * 获取所有角色。
     *
     * @return 角色列表。
     */
    suspend fun getAllCharacters(): List<Character> {
        return mCharacterDao.getAllCharacters()
    }

    /**
     * 根据角色 id 获取角色详情。
     *
     * @param id 角色 id。
     * @return 匹配的角色；如果不存在则返回 null。
     */
    suspend fun getCharacterById(id: Long): Character? {
        return mCharacterDao.getCharacterById(id)
    }

    /**
     * 保存角色。
     *
     * 当 id 为 0 时创建新角色；否则更新已有角色。
     *
     * @param character 要保存的角色。
     * @return 保存后的角色 id。
     */
    suspend fun saveCharacter(character: Character): Long {
        return mAppDatabase.withTransaction {
            saveCharacterInTransaction(character)
        }
    }

    /**
     * 保存角色及其回复模型配置绑定。
     *
     * [llmProviderId] 为 0 时删除关联记录，使角色跟随全局模型配置；角色数据和关联关系在
     * 同一事务内提交，避免只保存其中一部分。
     *
     * @param character 要保存的角色。
     * @param llmProviderId 显式绑定的模型配置 ID；0 表示跟随全局设置。
     * @return 保存后的角色 ID。
     */
    suspend fun saveCharacterWithLLMProvider(
        character: Character,
        llmProviderId: Long
    ): Long {
        return mAppDatabase.withTransaction {
            val characterId = saveCharacterInTransaction(character)
            if (llmProviderId == 0L) {
                mCharacterLLMProviderAssociationDao.deleteByCharacterId(characterId)
            } else {
                mCharacterLLMProviderAssociationDao.insertOrReplace(
                    CharacterLLMProviderAssociation(
                        characterId = characterId,
                        llmProviderId = llmProviderId
                    )
                )
            }
            characterId
        }
    }

    /**
     * 获取角色显式绑定的回复模型配置 ID。
     *
     * @param characterId 角色 ID。
     * @return 模型配置 ID；没有关联记录时返回 0，表示跟随全局设置。
     */
    suspend fun getLLMProviderId(characterId: Long): Long {
        return mCharacterLLMProviderAssociationDao.getLLMProviderId(characterId) ?: 0L
    }

    /**
     * 更新已有角色。
     *
     * @param character 要更新的角色。
     */
    suspend fun updateCharacter(character: Character) {
        saveCharacter(character)
    }

    /**
     * 在事务内重读角色并只修改扩展 JSON，保留同时期提交的其他角色字段。
     *
     * @return 更新后的角色；角色已不存在时返回 null。
     */
    suspend fun updateCharacterExtensions(
        id: Long,
        transform: (String) -> String
    ): Character? {
        return mAppDatabase.withTransaction {
            val current = mCharacterDao.getCharacterById(id) ?: return@withTransaction null
            val updated = current.copy(extensionsJson = transform(current.extensionsJson))
            saveCharacterInTransaction(updated)
            mCharacterDao.getCharacterById(id)
        }
    }

    /**
     * 删除指定角色。
     *
     * @param id 角色 id。
     */
    suspend fun deleteCharacter(id: Long) {
        mImages.mutate {
            // 角色删除会级联单聊，群聊用户图片不属于该角色，必须保留。
            mAppDatabase.getChatSessionDao().getSessionsByCharacterId(id).forEach { session ->
                mImages.deleteInTransaction(this, MessageType.Single,
                    mAppDatabase.getChatMessageDao().getMessageIdsBySessionId(session.id))
            }
            mCharacterLLMProviderAssociationDao.deleteByCharacterId(id)
            mCharacterDao.deleteCharacterById(id)
        }
    }

    /**
     * 获取角色的所有开场白列表。
     *
     * @param id 角色 id。
     * @return 开场白列表；如果角色不存在或开场白为空则返回空列表。
     */
    suspend fun getCharacterFirstMessages(id: Long): List<String> {
        return getCharacterById(id)?.getChatFirstMessageList() ?: emptyList()
    }

    /**
     * 更新角色的开场白列表。
     * 自动使用 "<START>" 将列表拼接为字符串并保存。
     *
     * @param id 角色 id。
     * @param messages 开场白列表。
     * @return 更新是否成功（如果角色不存在则返回 false）。
     */
    suspend fun updateCharacterFirstMessages(id: Long, messages: List<String>): Boolean {
        val character = getCharacterById(id) ?: return false
        val newFirstMessages = messages.joinToString("<START>")
        updateCharacter(character.copy(firstMessages = newFirstMessages))
        return true
    }

    /**
     * 获取角色的所有标签列表。
     * 自动将 JSON 字符串解析为列表。
     *
     * @param id 角色 id。
     * @return 标签列表；如果角色不存在或解析失败则返回空列表。
     */
    suspend fun getCharacterTags(id: Long): List<String> {
        val character = getCharacterById(id) ?: return emptyList()
        return mGson.toStringList(character.characterTags)
    }

    /**
     * 更新角色的标签列表。
     * 自动将其转换为 JSON 字符串并保存。
     *
     * @param id 角色 id。
     * @param tags 标签列表。
     * @return 更新是否成功（如果角色不存在则返回 false）。
     */
    suspend fun updateCharacterTags(id: Long, tags: List<String>): Boolean {
        val character = getCharacterById(id) ?: return false
        updateCharacter(character.copy(characterTags = mGson.toJsonString(tags)))
        return true
    }

    /**
     * 保存角色并在同一事务内提取显式提供的 `extensions.regex_scripts`。
     *
     * 本地 Character 只保留未知扩展；Regex 表是脚本的唯一权威来源。扩展中没有该字段时保留
     * 既有脚本，显式空数组则清空对应角色脚本。
     */
    private suspend fun saveCharacterInTransaction(character: Character): Long {
        val extraction = mRegexCodec.extractFromCharacterExtensions(character.extensionsJson)
        val persistedCharacter = if (extraction.hadRegexScripts) {
            character.copy(extensionsJson = extraction.extensionsJson)
        } else {
            character
        }
        val characterId = if (persistedCharacter.id == 0L) {
            mCharacterDao.insertOrReplace(persistedCharacter)
        } else {
            mCharacterDao.update(persistedCharacter)
            persistedCharacter.id
        }
        if (extraction.hadRegexScripts) {
            mRegexDao.deleteCharacterScripts(characterId)
            val scripts = extraction.scripts.normalizeRegexScriptIds()
            if (scripts.isNotEmpty()) {
                mRegexDao.insertScripts(
                    scripts.mapIndexed { index, script ->
                        script.toEntity(characterId, index, mGson)
                    }
                )
            }
        }
        return characterId
    }
}
