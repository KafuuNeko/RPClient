package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetCategory
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetField
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetScope
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetSection

/** 场景筛选、自定义过滤与分组展开状态独立于编辑草稿，保存字段后继续保留。 */
data class PromptPresetBrowserState(
    val scope: PromptPresetScope = PromptPresetScope.All,
    val onlyCustomized: Boolean = false,
    val expandedCategories: Set<PromptPresetCategory> = setOf(PromptPresetCategory.Basic),
    val groups: List<PromptPresetGroupState> = emptyList(),
    val scopedTotalCount: Int = 0,
    val scopedCustomCount: Int = 0,
) {
    /** 当前可见分组是否已全部展开，用于切换顶栏展开/折叠按钮图标。 */
    val allExpanded: Boolean
        get() = groups.isNotEmpty() && groups.all { group -> group.expanded }
}

/** 一个用途分类的可见内容、自定义计数与展开状态。 */
data class PromptPresetGroupState(
    val category: PromptPresetCategory,
    val expanded: Boolean,
    val sections: List<PromptPresetSectionState>,
) {
    val itemCount: Int get() = sections.sumOf { section -> section.items.size }
    val customCount: Int
        get() = sections.sumOf { section -> section.items.count { item -> item.isCustomized } }
}

/** 分类内部按操作区分的小节。 */
data class PromptPresetSectionState(
    val section: PromptPresetSection,
    val items: List<PromptPresetFieldState>,
)

/** 列表字段承载定义、自定义标记、空内容状态与单行截断预览。 */
data class PromptPresetFieldState(
    val field: PromptPresetField,
    val isCustomized: Boolean,
    val isEmpty: Boolean,
    val previewText: String,
)
