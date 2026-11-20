package me.kafuuneko.rpclient.feature.groupchatcreate.model

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.libs.prompt.macro.resolveCharacterUserMacros
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
    /** 由 ViewModel 映射的开场白预览，与原文索引对应。 */
    val greetingPreviews: List<String> = greetings,
    /** 与其它创建页保持一致的标签检索字段。 */
    val tags: List<String> = emptyList(),
    /** 已按需解码的角色头像位图，为空时显示首字母占位。 */
    val avatarImage: ImageBitmap? = null,
)

/**
 * 根据检索关键词和可选已选集合过滤可见角色。
 *
 * @param query 搜索关键词
 * @param selectedIds 非空时仅保留处于该集合内的角色
 * @return 过滤后的角色列表
 */
internal fun List<GroupChatCreateCharacterItem>.visibleFor(
    query: String,
    selectedIds: Set<Long>? = null,
): List<GroupChatCreateCharacterItem> {
    val normalized = query.trim()
    return filter { character ->
        (selectedIds == null || character.id in selectedIds) &&
            matchesCharacterQuery(
                name = character.name,
                description = character.description,
                tags = character.tags,
                query = normalized,
            )
    }
}

/**
 * 将持久化角色转换为当前页面的候选展示项。
 * @param userName 本次草稿使用的有效用户名。
 * @return 同时保留开场白原文和宏展开预览的角色项。
 */
internal fun Character.toGroupChatCreateCharacterItem(userName: String): GroupChatCreateCharacterItem {
    // 从角色卡原文生成预览，切换人设时不在上一次展开结果上替换。
    val greetings = getChatFirstMessageList()
    return GroupChatCreateCharacterItem(
        id = id,
        name = name,
        description = resolveCharacterUserMacros(
            template = description,
            characterName = name,
            userName = userName,
        ),
        selected = false,
        characterLorebookId = characterLorebookId,
        greetings = greetings,
        greetingPreviews = greetings.map { greeting ->
            resolveCharacterUserMacros(
                template = greeting,
                characterName = name,
                userName = userName
            )
        },
        tags = getCharacterTagList(),
    )
}
