package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter

/**
 * 规范化角色列表的排序：已选角色按 sortOrder 升序且紧密重新编号（0, 1, 2...），未选角色按名称字典序排在最后。
 */
internal fun normalizeCharacterOrder(
    characters: List<StoryCharacterOptionItem>
): List<StoryCharacterOptionItem> {
    val selected = characters.filter { it.selected }
        .sortedBy { it.sortOrder }
        .mapIndexed { index, item -> item.copy(sortOrder = index) }
    val unselected = characters.filterNot { it.selected }
        .sortedBy { it.name.lowercase() }
        .map { it.copy(sortOrder = Int.MAX_VALUE) }
    return selected + unselected
}

/**
 * 数据库存储的激活模式 Int 转 UI 枚举 [StoryCharacterActivationMode]。
 */
internal fun Int?.toStoryCharacterActivationMode(): StoryCharacterActivationMode {
    return when (this) {
        StoryCharacter.ACTIVATION_PRIMARY -> StoryCharacterActivationMode.Primary
        StoryCharacter.ACTIVATION_ALWAYS -> StoryCharacterActivationMode.Always
        else -> StoryCharacterActivationMode.Auto
    }
}

/**
 * UI 激活模式枚举转数据库存储的 Int 值。
 */
internal fun StoryCharacterActivationMode.toStorageValue(): Int {
    return when (this) {
        StoryCharacterActivationMode.Primary -> StoryCharacter.ACTIVATION_PRIMARY
        StoryCharacterActivationMode.Always -> StoryCharacter.ACTIVATION_ALWAYS
        StoryCharacterActivationMode.Auto -> StoryCharacter.ACTIVATION_AUTO
    }
}
