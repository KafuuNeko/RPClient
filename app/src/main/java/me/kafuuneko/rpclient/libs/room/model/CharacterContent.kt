package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.character.CharacterContentKind

/** 同一数据库快照中的角色名称和关联内容。 */
data class CharacterContent(val name: String, val items: List<CharacterContentItem>)

/** 角色页只读取导航与排序所需的元数据，不加载消息和章节正文。 */
data class CharacterContentItem(
    val kind: CharacterContentKind,
    val contentId: Long,
    val title: String,
    val latestTime: Long,
)

/** 首页每个有关联内容的角色只占一行。 */
data class RecentCharacterOverview(
    val characterId: Long,
    val characterName: String,
    val avatarUuid: String,
    val latestTitle: String,
    val latestTime: Long,
    val contentCount: Int,
)
