package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState

/** 故事设置中单个已选角色的持久化快照。 */
data class StorySettingsCharacterSnapshot(
    val characterId: Long,
    val activationMode: StoryCharacterActivationMode,
)

/**
 * 故事设置表单的持久化字段快照，用于与当前草稿比对是否存在未保存修改。
 *
 * - 覆盖点击保存时一并写入数据库的记忆、摘要、作者注释、人设、已选角色顺序与激活模式及已选世界书条目。
 * - 不包含当前分页与展示预览等纯界面状态。
 */
data class StorySettingsSnapshot(
    val memory: String,
    val summary: String,
    val authorNote: String,
    val userPersonaId: Long,
    val selectedCharacters: List<StorySettingsCharacterSnapshot>,
    val selectedLorebookEntryIds: Set<Long>,
)

/**
 * 提取当前故事设置页面中参与保存的持久化快照。
 *
 * @return 与当前设置草稿对应的 [StorySettingsSnapshot]
 */
internal fun StoryEditorPageState.Settings.toSettingsSnapshot(): StorySettingsSnapshot {
    // 提取已选角色的有效顺序与激活模式，忽略未选角色及仅用于展示的描述预览。
    val selectedCharacters = characters
        .filter { character -> character.selected }
        .sortedBy { character -> character.sortOrder }
        .map { character ->
            StorySettingsCharacterSnapshot(
                characterId = character.id,
                activationMode = character.activationMode,
            )
        }
    // 提取当前启用的全部世界书条目集合，忽略分组展示结构。
    val selectedLorebookEntryIds = lorebookGroups
        .asSequence()
        .flatMap { group -> group.entries.asSequence() }
        .filter { entry -> entry.selected }
        .map { entry -> entry.id }
        .toSet()
    return StorySettingsSnapshot(
        memory = memory,
        summary = summary,
        authorNote = authorNote,
        userPersonaId = persona.selectedId,
        selectedCharacters = selectedCharacters,
        selectedLorebookEntryIds = selectedLorebookEntryIds,
    )
}

/**
 * 将当前故事设置状态记录为已持久化的基线快照。
 *
 * @return 已同步 [StoryEditorPageState.Settings.savedSnapshot] 的设置状态
 */
internal fun StoryEditorPageState.Settings.withCurrentAsSavedSnapshot(): StoryEditorPageState.Settings {
    return copy(savedSnapshot = toSettingsSnapshot())
}

/**
 * 判断故事设置草稿相对已持久化基线快照是否存在未保存修改。
 *
 * @return 存在尚未保存的设置差异时返回 true
 */
internal fun StoryEditorPageState.Settings.hasUnsavedChanges(): Boolean {
    val baseline = savedSnapshot ?: return false
    return toSettingsSnapshot() != baseline
}
