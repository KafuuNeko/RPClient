package me.kafuuneko.rpclient.feature.groupchatcreate.model

import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingSelection

/** 创建群聊时支持的开场白来源。 */
enum class GroupChatGreetingMode {
    RandomPerCharacter,

    Manual,

    Custom,

    None
}

/** 手动或自定义开场白可选择的群成员。 */
data class GroupChatGreetingCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前角色或群聊可选择的开场白列表。 */
    val greetings: List<String>
)

/** 创建页开场白区域的完整可交互状态。 */
data class GroupChatCreateGreetingState(
    /** 当前流程采用的处理模式。 */
    val mode: GroupChatGreetingMode = GroupChatGreetingMode.RandomPerCharacter,
    /** 当前页面或流程可使用的角色列表。 */
    val characters: List<GroupChatGreetingCharacterItem> = emptyList(),
    /** 当前选中角色的 ID。 */
    val selectedCharacterId: Long? = null,
    /** 当前选中的开场白位置。 */
    val selectedGreetingIndex: Int? = null,
    /** 用户为当前群聊输入的自定义开场白。 */
    val customGreeting: String = ""
) {
    val selectedCharacter: GroupChatGreetingCharacterItem?
        get() = characters.firstOrNull { it.id == selectedCharacterId }

    val canCreate: Boolean
        get() = when (mode) {
            GroupChatGreetingMode.RandomPerCharacter,
            GroupChatGreetingMode.None -> true

            GroupChatGreetingMode.Manual ->
                selectedCharacter?.greetings?.getOrNull(selectedGreetingIndex ?: -1) != null

            GroupChatGreetingMode.Custom ->
                selectedCharacter != null && customGreeting.isNotBlank()
        }
}

/**
 * 成员变化后收敛开场白候选和选择，避免删除成员后留下悬空角色或候选索引。
 *
 * @param characters 当前全量角色项列表
 */
internal fun GroupChatCreateGreetingState.reconcile(
    characters: List<GroupChatCreateCharacterItem>,
): GroupChatCreateGreetingState {
    // 仅保留已勾选为群成员的角色作为开场白候选
    val candidates = characters.filter { it.selected }.map { groupChatCreateCharacterItem ->
        GroupChatGreetingCharacterItem(
            id = groupChatCreateCharacterItem.id,
            name = groupChatCreateCharacterItem.name,
            greetings = groupChatCreateCharacterItem.greetings,
        )
    }
    val current = candidates.firstOrNull { it.id == selectedCharacterId }
    // 按模式推导首选开场角色
    val preferred = when (mode) {
        GroupChatGreetingMode.Manual ->
            current?.takeIf { it.greetings.isNotEmpty() }
                ?: candidates.firstOrNull { it.greetings.isNotEmpty() }

        else -> current ?: candidates.firstOrNull()
    }
    // 校验并收敛开场白索引
    val greetingIndex = selectedGreetingIndex
        ?.takeIf { it in preferred?.greetings.orEmpty().indices }
        ?: preferred?.greetings?.indices?.firstOrNull()
    return copy(
        characters = candidates,
        selectedCharacterId = preferred?.id,
        selectedGreetingIndex = greetingIndex,
    )
}

/** 将开场白状态映射为领域层开场白选择实体。 */
internal fun GroupChatCreateGreetingState.toSelection(): GroupChatGreetingSelection {
    return when (mode) {
        GroupChatGreetingMode.RandomPerCharacter ->
            GroupChatGreetingSelection.RandomPerCharacter

        GroupChatGreetingMode.Manual -> GroupChatGreetingSelection.Manual(
            characterId = requireNotNull(selectedCharacterId),
            greetingIndex = requireNotNull(selectedGreetingIndex),
        )

        GroupChatGreetingMode.Custom -> GroupChatGreetingSelection.Custom(
            characterId = requireNotNull(selectedCharacterId),
            content = customGreeting,
        )

        GroupChatGreetingMode.None -> GroupChatGreetingSelection.None
    }
}
