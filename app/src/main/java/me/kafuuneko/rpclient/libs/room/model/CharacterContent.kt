package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.character.CharacterContentKind

/** 同一数据库快照中的角色名称和关联内容。 */
data class CharacterContent(val name: String, val items: List<CharacterContentItem>)

/** 角色页的导航元数据和最新内容预览，不包含消息历史、图片附件或完整章节。 */
data class CharacterContentItem(
    val kind: CharacterContentKind,
    val contentId: Long,
    val title: String,
    val latestTime: Long,
    val previewContent: String?,
    val previewHasImages: Boolean,
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
