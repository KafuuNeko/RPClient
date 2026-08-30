package me.kafuuneko.rpclient.feature.groupchat.model

/** 群聊成员列表的 UI 模型；[muted] 只控制发言资格，不代表移除角色卡。 */
data class GroupChatMemberItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 当前群聊成员是否被禁言。 */
    val muted: Boolean
)
