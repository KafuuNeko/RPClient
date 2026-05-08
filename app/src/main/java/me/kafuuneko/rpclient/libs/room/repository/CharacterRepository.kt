package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character

class CharacterRepository(private val mAppDatabase: AppDatabase) {
    private val characterDao = mAppDatabase.getCharacterDao()

    /**
     * 获取所有角色。
     *
     * @return 角色列表。
     */
    suspend fun getAllCharacters(): List<Character> {
        return characterDao.getAllCharacters()
    }

    /**
     * 根据角色 id 获取角色详情。
     *
     * @param id 角色 id。
     * @return 匹配的角色；如果不存在则返回 null。
     */
    suspend fun getCharacterById(id: Long): Character? {
        return characterDao.getCharacterById(id)
    }

    /**
     * 创建新的角色。
     *
     * @param name 角色名称。
     * @param avatar 角色头像资源路径或标识。
     * @param characterTags 角色标签，按当前数据结构保存为字符串。
     * @param personality 角色核心设定。
     * @param scenario 场景设定。
     * @param firstMessages 开场白。
     * @param examplesOfDialogue 对话示例。
     * @param postHistoryInstructions 后置提示词。
     * @return 新创建的角色 id。
     */
    suspend fun createCharacter(
        name: String,
        avatar: String = "",
        characterTags: String = "",
        personality: String = "",
        scenario: String = "",
        firstMessages: String = "",
        examplesOfDialogue: String = "",
        postHistoryInstructions: String = ""
    ): Long {
        return characterDao.insertOrReplace(
            Character(
                name = name,
                avatar = avatar,
                characterTags = characterTags,
                personality = personality,
                scenario = scenario,
                firstMessages = firstMessages,
                examplesOfDialogue = examplesOfDialogue,
                postHistoryInstructions = postHistoryInstructions
            )
        )
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
            return characterDao.insertOrReplace(character)
        }
        characterDao.update(character)
        return character.id
    }

    /**
     * 更新已有角色。
     *
     * @param character 要更新的角色。
     */
    suspend fun updateCharacter(character: Character) {
        characterDao.update(character)
    }

    /**
     * 删除指定角色。
     *
     * @param id 角色 id。
     */
    suspend fun deleteCharacter(id: Long) {
        characterDao.deleteCharacterById(id)
    }
}
