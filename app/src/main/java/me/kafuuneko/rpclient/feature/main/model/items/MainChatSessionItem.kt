package me.kafuuneko.rpclient.feature.main.model.items

/** 首页最近单聊会话卡片所需的扁平展示数据。 */
data class MainChatSessionItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 关联角色的唯一 ID。 */
    val characterId: String,
    /** 关联角色的显示名称快照。 */
    val characterName: String,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 经过长度限制、供列表快速浏览的内容预览。 */
    val preview: String,
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int,
    /** 记录最近一次更新的时间戳，单位为毫秒。 */
    val updatedAt: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    override val latestTime: Long
) : MainHomeContentItem
