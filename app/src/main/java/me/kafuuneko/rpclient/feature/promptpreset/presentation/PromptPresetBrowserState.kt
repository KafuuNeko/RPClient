package me.kafuuneko.rpclient.feature.promptpreset.presentation

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetCategory
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetField
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetScope
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetSection

/** 场景筛选与分组展开状态独立于编辑草稿，保存字段后继续保留。 */
data class PromptPresetBrowserState(
    val scope: PromptPresetScope = PromptPresetScope.All,
    val expandedCategories: Set<PromptPresetCategory> = setOf(PromptPresetCategory.Basic),
    val groups: List<PromptPresetGroupState> = emptyList(),
)

/** 一个用途分类的可见内容与展开状态。 */
data class PromptPresetGroupState(
    val category: PromptPresetCategory,
    val expanded: Boolean,
    val sections: List<PromptPresetSectionState>,
) {
    val itemCount: Int get() = sections.sumOf { it.items.size }
}

/** 分类内部按操作区分的小节。 */
data class PromptPresetSectionState(
    val section: PromptPresetSection,
    val items: List<PromptPresetFieldState>,
)

/** 列表只承载字段定义和内容状态，不复制正文预览。 */
data class PromptPresetFieldState(
    val field: PromptPresetField,
    @param:StringRes val statusRes: Int,
)
