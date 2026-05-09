package me.kafuuneko.rpclient.libs.room.repository

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.toStringList

class CharacterRepository(
    appDatabase: AppDatabase,
    private val mGson: Gson
) {
    private val mCharacterDao = appDatabase.getCharacterDao()

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
        if (character.id == 0L) {
            return mCharacterDao.insertOrReplace(character)
        }
        mCharacterDao.update(character)
        return character.id
    }

    /**
     * 更新已有角色。
     *
     * @param character 要更新的角色。
     */
    suspend fun updateCharacter(character: Character) {
        mCharacterDao.update(character)
    }

    /**
     * 删除指定角色。
     *
     * @param id 角色 id。
     */
    suspend fun deleteCharacter(id: Long) {
        mCharacterDao.deleteCharacterById(id)
    }

    /**
     * 获取角色的所有开场白列表。
     *
     * @param id 角色 id。
     * @return 开场白列表；如果角色不存在或开场白为空则返回空列表。
     */
    suspend fun getCharacterFirstMessages(id: Long): List<String> {
        return getCharacterById(id)?.getFirstMessageList() ?: emptyList()
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
}
