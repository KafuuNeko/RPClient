package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.macro.MacroReference
import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.macro.promptMacroExpander
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

    // 保留快照中的模板原文，由统一展开器按实际引用求值。
    private val mMemberValues = members.associate { member ->
        val character = member.character
        character.id to buildValues(character, character.name, mPersona.name)
    }
    private val mDefaultValues =
        mMemberValues[mSpeaker?.id] ?: buildValues(mSpeaker, mSpeaker?.name, mPersona.name)

    /**
     * 返回角色卡、模板和 Regex 共用的宏。
     * @param character 片段所属角色，默认为本轮发言者。
     * @return 当前角色的原始模板与字面姓名绑定。
     */
    fun forCharacter(character: Character? = mSpeaker): Map<String, String> =
        mMemberValues[character?.id] ?: mDefaultValues

    /** 绑定人物字段原文，依赖关系由统一展开器处理。 */
    private fun buildValues(
        character: Character?,
        characterName: String?,
        userName: String
    ): Map<String, String> {
        // 姓名是终值，人设和有效场景保留为可组合模板。
        val names = buildMap {
            put("user", userName)
            if (characterName != null) put("char", characterName)
            put("group", mNames)
            put("groupnotmuted", mUnmuted)
            put("charifnotgroup", "")
        }
        val scenario = mSession.scenario.ifBlank { character?.scenario.orEmpty() }
        return names + mapOf(
            "persona" to mPersona.description,
            "scenario" to scenario,
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
        // 历史角色优先沿用发言快照，Regex 使用同一身份快照。
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

/** 群聊只提供当前入口已有的宏集合，人物字段按模板值求值。 */
internal fun groupMacroScope(
    values: Map<String, String>,
    resolve: (MacroReference) -> MacroValue? = { null },
): MacroScope = MacroScope(
    values.mapValues { (name, value) ->
        if (name == "persona" || name == "scenario") MacroValue.Template(value)
        else MacroValue.Literal(value)
    },
    mResolve = resolve,
)

/** 展开模板引用，字面姓名和其他终值不会重新扫描。 */
internal fun renderGroupMacros(template: String, values: Map<String, String>): String =
    promptMacroExpander.expand(template, groupMacroScope(values)).text
