package me.kafuuneko.rpclient.feature.main.model.items

import me.kafuuneko.rpclient.libs.room.model.RecentCharacterOverview

/** 首页角色入口的展示数据，头像仅传递文件 UUID。 */
data class MainRecentCharacterItem(
    val characterId: Long,
    val characterName: String,
    val avatarUuid: String,
    val latestTitle: String,
    val latestTime: Long,
    val contentCount: Int,
)

/**
 * 将角色查询概览映射为首页条目。
 *
 * @return 不依赖数据库对象的角色入口。
 */
internal fun RecentCharacterOverview.toUiModel(): MainRecentCharacterItem = MainRecentCharacterItem(
    characterId = characterId,
    characterName = characterName,
    avatarUuid = avatarUuid,
    latestTitle = latestTitle,
    latestTime = latestTime,
    contentCount = contentCount,
)
