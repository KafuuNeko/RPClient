package me.kafuuneko.rpclient.feature.groupchatcreate.model

import me.kafuuneko.rpclient.libs.prompt.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.utils.matchesCharacterQuery

/** 创建群聊时使用的角色选项，并记录角色卡绑定的世界书。 */
data class GroupChatCreateCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 当前列表项是否已被选中。 */
    val selected: Boolean,
    /** 角色卡直接绑定的世界书 ID；未绑定时为 0。 */
    val characterLorebookId: Long = 0L,
    /** 当前角色或群聊可选择的开场白列表。 */
    val greetings: List<String> = emptyList(),
    /** 与其它创建页保持一致的标签检索字段。 */
    val tags: List<String> = emptyList()
)

/** 根据检索关键词过滤可见角色。 */
internal fun List<GroupChatCreateCharacterItem>.visibleFor(
    query: String
): List<GroupChatCreateCharacterItem> {
    val normalized = query.trim()
    return filter {
        matchesCharacterQuery(it.name, it.description, it.tags, normalized)
    }
}

/** 将持久化角色转换为当前页面的候选展示项。 */
internal fun Character.toGroupChatCreateCharacterItem(userName: String): GroupChatCreateCharacterItem {
    return GroupChatCreateCharacterItem(
        id = id,
        name = name,
        description = resolveCharacterUserMacros(
            template = description,
            characterName = name,
            userName = userName
        ),
        selected = false,
        characterLorebookId = characterLorebookId,
        greetings = getChatFirstMessageList(),
        tags = getCharacterTagList()
    )
}
