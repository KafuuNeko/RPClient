package me.kafuuneko.rpclient.feature.charactercontent.model

import me.kafuuneko.rpclient.libs.character.CharacterContentKind
import me.kafuuneko.rpclient.libs.room.model.CharacterContentItem as CharacterContentRecord

/** 页面筛选与导航所需的轻量条目。 */
data class CharacterContentItem(
    val type: CharacterContentKind,
    val contentId: Long,
    val title: String,
    val latestTime: Long,
)

/**
 * 将查询结果转换为页面条目，避免 Compose 直接依赖 Room 查询模型。
 *
 * @return 仅包含展示和导航字段的不可变条目。
 */
internal fun CharacterContentRecord.toUiModel(): CharacterContentItem = CharacterContentItem(
    type = kind,
    contentId = contentId,
    title = title,
    latestTime = latestTime,
)
