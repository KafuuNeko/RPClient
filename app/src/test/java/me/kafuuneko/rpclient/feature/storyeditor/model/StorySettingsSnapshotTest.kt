package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySettingsSection
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证故事设置未保存变更检测与持久化配置字段保持一致。 */
class StorySettingsSnapshotTest {
    /** 纯界面分区切换或未选角色展示变化不应触发离开保存提示。 */
    @Test
    fun uiOnlyChangesDoNotMarkSettingsDirty() {
        val base = sampleSettingsState()

        assertFalse(base.hasUnsavedChanges())
        assertFalse(
            base.copy(selectedSection = StorySettingsSection.Characters).hasUnsavedChanges()
        )
        assertFalse(base.copy(isSaving = true).hasUnsavedChanges())
        val updatedUnselectedPreview = base.copy(
            characters = base.characters.map { item ->
                if (!item.selected) item.copy(description = "Updated preview") else item
            },
        )
        assertFalse(updatedUnselectedPreview.hasUnsavedChanges())
    }

    /** 记忆、摘要、作者注释或人设修改应触发保存提示，改回原值后恢复未修改。 */
    @Test
    fun contextAndPersonaChangesAreDetected() {
        val base = sampleSettingsState()

        // 验证上下文文本与人设选择修改均会被识别。
        assertTrue(base.copy(memory = "Memory updated").hasUnsavedChanges())
        assertTrue(base.copy(summary = "Summary updated").hasUnsavedChanges())
        assertTrue(base.copy(authorNote = "Note updated").hasUnsavedChanges())
        assertTrue(base.copy(persona = PersonaSelection(selectedId = 2L)).hasUnsavedChanges())
        // 确认应用已落库的生成摘要并同步基线后，不应误报未保存。
        val afterConfirmedSummary = base.copy(
            summary = "Generated summary",
            savedSnapshot = base.savedSnapshot?.copy(summary = "Generated summary"),
        )
        assertFalse(afterConfirmedSummary.hasUnsavedChanges())
        assertFalse(
            base.copy(memory = "Temp")
                .copy(memory = "Initial memory")
                .hasUnsavedChanges(),
        )
    }

    /** 已选角色集合、顺序或激活模式变化均应识别为未保存修改。 */
    @Test
    fun characterSelectionOrderAndModeChangesAreDetected() {
        val base = sampleSettingsState()

        // 变更已选角色的激活模式。
        val changedMode = base.copy(
            characters = base.characters.map { item ->
                if (item.id == 1L) {
                    item.copy(activationMode = StoryCharacterActivationMode.Always)
                } else {
                    item
                }
            },
        )
        // 交换两个已选角色的注入顺序。
        val swappedOrder = base.copy(
            characters = base.characters.map { item ->
                when (item.id) {
                    1L -> item.copy(sortOrder = 1)
                    2L -> item.copy(sortOrder = 0)
                    else -> item
                }
            },
        )
        // 新增勾选未选角色。
        val addedCharacter = base.copy(
            characters = base.characters.map { item ->
                if (item.id == 3L) item.copy(selected = true, sortOrder = 2) else item
            },
        )
        assertTrue(changedMode.hasUnsavedChanges())
        assertTrue(swappedOrder.hasUnsavedChanges())
        assertTrue(addedCharacter.hasUnsavedChanges())
    }

    /** 世界书条目启用集合变化应识别为未保存修改。 */
    @Test
    fun lorebookEntrySelectionChangesAreDetected() {
        val base = sampleSettingsState()
        val toggledEntry = base.copy(
            lorebookGroups = base.lorebookGroups.map { group ->
                group.copy(
                    entries = group.entries.map { entry ->
                        if (entry.id == 102L) entry.copy(selected = true) else entry
                    },
                )
            },
        )

        assertTrue(toggledEntry.hasUnsavedChanges())
    }

    private fun sampleSettingsState(): StoryEditorPageState.Settings {
        // 构造带已选角色、未选角色与世界书条目的故事设置基准态。
        return StoryEditorPageState.Settings(
            persona = PersonaSelection(selectedId = 1L),
            memory = "Initial memory",
            summary = "Initial summary",
            authorNote = "Initial note",
            characters = listOf(
                StoryCharacterOptionItem(
                    id = 1L,
                    name = "Lyra",
                    description = "Navigator",
                    selected = true,
                    activationMode = StoryCharacterActivationMode.Primary,
                    sortOrder = 0,
                ),
                StoryCharacterOptionItem(
                    id = 2L,
                    name = "Mina",
                    description = "Archivist",
                    selected = true,
                    activationMode = StoryCharacterActivationMode.Auto,
                    sortOrder = 1,
                ),
                StoryCharacterOptionItem(
                    id = 3L,
                    name = "Rowan",
                    description = "Engineer",
                    selected = false,
                    activationMode = StoryCharacterActivationMode.Auto,
                    sortOrder = Int.MAX_VALUE,
                ),
            ),
            lorebookGroups = listOf(
                StoryLorebookGroupItem(
                    id = 10L,
                    name = "Station Lore",
                    entries = listOf(
                        StoryLorebookEntryItem(
                            id = 101L,
                            name = "Docking Bay",
                            contentPreview = "Old docking bay",
                            keywords = listOf("dock"),
                            constant = false,
                            selected = true,
                        ),
                        StoryLorebookEntryItem(
                            id = 102L,
                            name = "Reactor",
                            contentPreview = "Core sector",
                            keywords = listOf("reactor"),
                            constant = false,
                            selected = false,
                        ),
                    ),
                ),
            ),
        ).withCurrentAsSavedSnapshot()
    }
}
