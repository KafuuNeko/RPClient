package me.kafuuneko.rpclient.feature.groupchat.model

/** 群聊设置中可添加角色的最小展示信息。 */
data class GroupChatAvailableCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前角色是否已经属于目标群聊。 */
    val alreadyMember: Boolean
)
