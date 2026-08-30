package me.kafuuneko.rpclient.feature.groupchatcreate.model

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
