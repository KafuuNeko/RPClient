package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData

/**
 * 群聊一次请求或展示快照中的宏取值。
 *
 * - 成员卡使用卡片作者，历史消息优先使用发送时的署名。
 * - 世界书绑定唯一群成员时沿用该卡，共享或会话级世界书沿用本轮发言者。
 */
internal class GroupChatMacros(
    private val mSession: GroupChatSession,
    members: List<GroupChatMemberData>,
    private val mPersona: UserPersona,
    private val mSpeaker: Character? = members.singleOrNull()?.character,
) {
    private val mCharacters = members.associate { it.character.id to it.character }
    private val mNames = members.joinToString(", ") { it.character.name }
    private val mUnmuted =
        members.filterNot { it.relation.muted }.joinToString(", ") { it.character.name }

    /** 本轮进入提示词和世界书扫描的角色卡，保持成员顺序。 */
    val cardMembers = mSession.cardMembers(members, mSpeaker)
    private val mLorebookOwners = members.filter { it.character.characterLorebookId > 0 }
        .groupBy { it.character.characterLorebookId }

    // 常见历史仅有少数几位发言者，复用已展开的人设和场景，避免逐条重新解析长角色卡。
    private val mMemberValues = members.associate { member ->
        val character = member.character
        character.id to buildValues(character, character.name, mPersona.name)
    }
    private val mDefaultValues =
        mMemberValues[mSpeaker?.id] ?: buildValues(mSpeaker, mSpeaker?.name, mPersona.name)

    /**
     * 返回角色卡、模板和 Regex 共用的宏。
     * @param character 片段所属角色，默认为本轮发言者。
     * @return 已展开姓名、人设与有效场景的字典。
     */
    fun forCharacter(character: Character? = mSpeaker): Map<String, String> =
        mMemberValues[character?.id] ?: mDefaultValues

    /** 人设和场景内部只展开姓名与群成员，避免互相递归。 */
    private fun buildValues(
        character: Character?,
        characterName: String?,
        userName: String
    ): Map<String, String> {
        val names = buildMap {
            put("user", userName)
            if (characterName != null) put("char", characterName)
            put("group", mNames)
            put("groupnotmuted", mUnmuted)
            put("charifnotgroup", "")
        }
        val scenario = mSession.scenario.ifBlank { character?.scenario.orEmpty() }
        return names + mapOf(
            "persona" to renderGroupMacros(mPersona.description, names),
            "scenario" to renderGroupMacros(scenario, names),
        )
    }

    /**
     * 取得条目所属角色的宏，多人共享或独立选择的公共世界书使用本轮角色。
     * @param id 世界书 ID。
     * @return 世界书扫描、正文与格式模板共用的宏。
     */
    fun forLorebook(id: Long): Map<String, String> {
        val owner = mLorebookOwners[id]?.singleOrNull()?.character
        return forCharacter(owner ?: mSpeaker)
    }

    /**
     * 角色改名或离群后仍优先使用快照，旧用户署名不随当前人设切换。
     * @param message 正在套用 Regex 脚本的消息。
     * @return 提供给脚本的宏字典。
     */
    fun forMessage(message: GroupChatMessage): Map<String, String> = when (message.source) {
        GroupChatMessage.Source.Character -> {
            val author = mCharacters[message.speakerCharacterId]
            val name = message.speakerNameSnapshot.ifBlank { author?.name }
            if (author != null && name == author.name) forCharacter(author)
            else buildValues(author, name, mPersona.name)
        }

        GroupChatMessage.Source.User -> {
            val name = message.speakerName(mPersona.name)
            if (name == mPersona.name) mDefaultValues else buildValues(
                mSpeaker,
                mSpeaker?.name,
                name
            )
        }

        GroupChatMessage.Source.System -> forCharacter()
    }
}

/** 计算本轮注入的卡片及其绑定世界书，共用同一套静音和发言者例外规则。 */
internal fun GroupChatSession.cardMembers(
    members: List<GroupChatMemberData>,
    speaker: Character?,
): List<GroupChatMemberData> = when (characterCardMode) {
    GroupChatSession.CharacterCardMode.Swap -> members.filter { it.character.id == speaker?.id }
    GroupChatSession.CharacterCardMode.Join -> members.filter { includeMutedCards || !it.relation.muted || it.character.id == speaker?.id }
}

private val groupMacro = Regex(
    """\{\{\s*([a-z][a-z0-9_]*)\s*\}\}|<(user|char|bot|group|charifnotgroup)>""",
    RegexOption.IGNORE_CASE,
)

/** 单次扫描模板，旧式标签共用同一字典；插入的值不会作为模板再次执行。 */
internal fun renderGroupMacros(template: String, values: Map<String, String>): String =
    substituteGroupMacros(template) { key -> values[key] }

/** 格式槽只在模板中匹配，插入的正文可以安全包含同名槽或未知宏。 */
internal fun formatGroupField(
    template: String,
    slot: String,
    content: String,
    values: Map<String, String>,
    appendIfMissing: Boolean = false,
): String {
    if (content.isBlank() || template.isBlank()) return content
    var found = false
    val formatted = substituteGroupMacros(template) { key ->
        if (key == slot) {
            found = true
            content
        } else values[key]
    }
    if (found) return formatted
    return if (appendIfMissing && formatted.isNotBlank()) "$formatted\n$content" else content
}

private fun substituteGroupMacros(template: String, value: (String) -> String?): String =
    groupMacro.replace(template) { match ->
        val legacy = match.groupValues[2]
        val key = match.groupValues[1].ifEmpty { legacy }.lowercase()
        value(if (legacy.equals("bot", ignoreCase = true)) "char" else key) ?: match.value
    }
