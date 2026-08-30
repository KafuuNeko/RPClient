package me.kafuuneko.rpclient.feature.chatcreate.model

/** 新建单聊页角色选择器所需的最小快照。 */
data class ChatCreateCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 角色用于分类和搜索的标签列表。 */
    val tags: List<String>
)
