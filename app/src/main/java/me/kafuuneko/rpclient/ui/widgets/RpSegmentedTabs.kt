package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 以角色编辑页为标准的圆角等宽分段 Tab，透明度与模糊使用页面顶部区域配置。
 *
 * @param tabs 按展示顺序排列的选项。
 * @param selectedTab 由调用方状态维护的当前选项。
 * @param onTabSelected 回传用户选择，不在组件内部保存业务状态。
 * @param tabLabel 提供每个选项的本地化标题。
 * @param modifier 整组 Tab 的布局修饰符。
 */
@Composable
fun <T> RpSegmentedTabs(
    tabs: List<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    tabLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    // 与角色编辑页统一使用顶部区域参数，避免内容区与顶部区设置不同而出现样式差异。
    AppSurface(
        kind = PageSurfaceKind.TopBar,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 中性表面与轻投影沿用角色编辑页，强调色和字重在完全透明时仍表达选中态。
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                AppSurface(
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            selected = isSelected
                            role = Role.Tab
                        },
                    onClick = { onTabSelected(tab) },
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tabLabel(tab),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Segmented tabs - Character", showBackground = true, widthDp = 320)
@Composable
private fun RpSegmentedTabsCharacterPreview() {
    AppTheme(dynamicColor = false) {
        RpSegmentedTabs(
            tabs = listOf(
                R.string.tab_character_profile,
                R.string.tab_character_dialogue,
                R.string.tab_character_advanced,
            ),
            selectedTab = R.string.tab_character_profile,
            onTabSelected = {},
            tabLabel = { stringResource(it) },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Segmented tabs - Settings", showBackground = true, widthDp = 320)
@Composable
private fun RpSegmentedTabsSettingsPreview() {
    AppTheme(dynamicColor = false) {
        RpSegmentedTabs(
            tabs = listOf(R.string.general_summary_memory, R.string.conversation_summary_memory),
            selectedTab = R.string.general_summary_memory,
            onTabSelected = {},
            tabLabel = { stringResource(it) },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Segmented tabs - Story dark", showBackground = true, widthDp = 320)
@Composable
private fun RpSegmentedTabsStoryPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        RpSegmentedTabs(
            tabs = listOf(
                R.string.story_context_settings,
                R.string.story_character_references,
                R.string.world_book,
            ),
            selectedTab = R.string.story_context_settings,
            onTabSelected = {},
            tabLabel = { stringResource(it) },
            modifier = Modifier.padding(16.dp),
        )
    }
}
