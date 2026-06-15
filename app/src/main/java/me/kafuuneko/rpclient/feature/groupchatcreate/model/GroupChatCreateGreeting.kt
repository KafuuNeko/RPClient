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
    val id: Long,
    val name: String,
    val greetings: List<String>
)

/** 创建页开场白区域的完整可交互状态。 */
data class GroupChatCreateGreetingState(
    val mode: GroupChatGreetingMode = GroupChatGreetingMode.RandomPerCharacter,
    val characters: List<GroupChatGreetingCharacterItem> = emptyList(),
    val selectedCharacterId: Long? = null,
    val selectedGreetingIndex: Int? = null,
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
