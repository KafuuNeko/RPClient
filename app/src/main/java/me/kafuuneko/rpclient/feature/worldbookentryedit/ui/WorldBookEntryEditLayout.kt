package me.kafuuneko.rpclient.feature.worldbookentryedit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditDialogState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditLoadState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditMode
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiState
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppPromptEditorDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.DEFAULT_PROMPT_MACROS
import me.kafuuneko.rpclient.ui.widgets.RpChipInputField
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpJsonCodeEditorField
import me.kafuuneko.rpclient.ui.widgets.RpMacroActionBar
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.rememberBoundTextFieldState
import me.kafuuneko.rpclient.ui.widgets.RpNumberStepper
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation

/** 世界书条目编辑导航分段选项卡。 */
private enum class WorldBookEntryEditTab {
    Content,
    Trigger,
    Advanced
}

/**
 * 世界书条目完整兼容字段编辑页 Compose 入口。
 *
 * 核心设计：
 * - 纯声明式渲染：接收 UiState 并将用户操作映射为 UiIntent 回传。
 * - 拦截系统返回：存在未保存修改时拦截并提示二次确认。
 * - 终态保持：Finished 分支渲染上一快照以避免销毁时闪白。
 */
@Composable
fun WorldBookEntryEditLayout(
    uiState: WorldBookEntryEditUiState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is WorldBookEntryEditUiState.Normal) { WorldBookEntryEditUiIntent.Back.emit() }
    when (uiState) {
        WorldBookEntryEditUiState.None -> Unit
        is WorldBookEntryEditUiState.Finished -> WorldBookEntryEditLayout(uiState.previous) {}
        is WorldBookEntryEditUiState.Normal -> {
            WorldBookEntryEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

/**
 * 正常编辑态主布局容器。
 *
 * 流程与职责：
 * - 顶栏呈现标题与保存快捷按钮。
 * - 主体采用分段选项卡（设定正文 / 触发规则 / 高级时序），避免信息过载。
 * - 支持全屏专注写作 Dialog 交互。
 */
@Composable
private fun WorldBookEntryEditNormal(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(WorldBookEntryEditTab.Content) }

    // 页面主容器
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = if (state.mode == WorldBookEntryEditMode.Create) {
                stringResource(R.string.create_world_book_entry)
            } else {
                stringResource(R.string.edit_world_book_entry)
            },
            onBack = { WorldBookEntryEditUiIntent.Back.emit() },
            actions = { TopBarSaveButton(state, emit) }
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = state.form.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    subtitle = stringResource(R.string.world_book_entry_editor_subtitle)
                )
            }
            if (state.loadState == WorldBookEntryEditLoadState.Loading) {
                item { LoadingPanel() }
            } else {
                item {
                    HeroHeaderPanel(
                        form = state.form,
                        loadState = state.loadState,
                        emit = emit
                    )
                }
                item {
                    WorldBookEntryEditTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                when (selectedTab) {
                    WorldBookEntryEditTab.Content -> {
                        item { BasicInfoPanel(state.form, state.loadState, emit) }
                        item {
                            ContentWorkbenchPanel(
                                form = state.form,
                                emit = emit
                            )
                        }
                        item { CategoriesPanel(state.form, emit) }
                    }

                    WorldBookEntryEditTab.Trigger -> {
                        if (state.form.constant) {
                            item { ConstantNoticeBanner() }
                        }
                        item { PrimaryKeywordsPanel(state.form, emit) }
                        item {
                            SecondaryKeywordsAndLogicPanel(
                                form = state.form,
                                loadState = state.loadState,
                                emit = emit
                            )
                        }
                        item { ProbabilityPanel(state.form, state.loadState, emit) }
                        item { ScanDepthAndScopePanel(state.form, state.loadState, emit) }
                    }

                    WorldBookEntryEditTab.Advanced -> {
                        item { PlacementPanel(state.form, state.loadState, emit) }
                        item { InclusionGroupPanel(state.form, state.loadState, emit) }
                        item { TimingPanel(state.form, state.loadState, emit) }
                        item { RecursionAndMatchingPanel(state.form, state.loadState, emit) }
                        item { ExtensionsJsonPanel(state.form, state.loadState, emit) }
                    }
                }

                item { ActionPanel(state, emit) }
            }
        }
    }
}

/** 初始数据加载占位面板。 */
@Composable
private fun LoadingPanel() {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading))
        }
    }
}

/**
 * 顶部 Hero 状态概览看板。
 *
 * 核心特性：
 * - 汇总条目核心参数（常驻/触发、启用状态、角色、深度优先级、触发概率）。
 * - 右侧集成快捷启用 Switch 与删除入口。
 */
@Composable
private fun HeroHeaderPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None
    val roleLabel = when (form.role.trim().toIntOrNull() ?: LorebookEntry.ROLE_SYSTEM) {
        LorebookEntry.ROLE_USER -> stringResource(R.string.role_user)
        LorebookEntry.ROLE_ASSISTANT -> stringResource(R.string.role_assistant)
        else -> stringResource(R.string.role_system)
    }
    val probability = form.probability.toIntOrNull() ?: 100
    val depthVal = form.depth.toIntOrNull() ?: 4
    val orderVal = form.order.toIntOrNull() ?: 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (form.disabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RpIconBubble(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    containerColor = if (form.constant) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    },
                    contentColor = if (form.constant) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = form.name.ifBlank { stringResource(R.string.unnamed_entry) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (probability < 100) {
                            stringResource(R.string.entry_order_depth_prob, orderVal, depthVal, probability)
                        } else {
                            stringResource(R.string.entry_order_depth, orderVal, depthVal)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = !form.disabled,
                    enabled = enabled,
                    onCheckedChange = { WorldBookEntryEditUiIntent.ChangeDisabled(!it).emit() }
                )
                IconButton(
                    enabled = enabled,
                    onClick = { WorldBookEntryEditUiIntent.DeleteEntryClick.emit() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }
            }

            // 状态胶囊徽章列表
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(
                    text = if (form.constant) stringResource(R.string.entry_status_constant) else stringResource(R.string.entry_status_conditional),
                    color = if (form.constant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                StatusBadge(
                    text = if (form.disabled) stringResource(R.string.entry_status_disabled) else stringResource(R.string.entry_status_enabled),
                    color = if (form.disabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary
                )
                StatusBadge(
                    text = roleLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (form.group.isNotBlank()) {
                    StatusBadge(
                        text = stringResource(R.string.entry_group_badge, form.group),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 单个弱化状态徽章胶囊。 */
@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/** 分段选项卡切换器组件。 */
@Composable
private fun WorldBookEntryEditTabBar(
    selectedTab: WorldBookEntryEditTab,
    onTabSelected: (WorldBookEntryEditTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                WorldBookEntryEditTab.Content to stringResource(R.string.tab_entry_content),
                WorldBookEntryEditTab.Trigger to stringResource(R.string.tab_entry_trigger),
                WorldBookEntryEditTab.Advanced to stringResource(R.string.tab_entry_advanced)
            )
            tabs.forEach { (tab, title) ->
                val selected = selectedTab == tab
                Surface(
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------- Tab 1: 设定正文 --------------------

/** 基础名称与开关面板。 */
@Composable
private fun BasicInfoPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(
            label = stringResource(R.string.entry_name),
            value = form.name,
            onChange = { WorldBookEntryEditUiIntent.ChangeName(it).emit() }
        )

        RpSettingsGroup {
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_constant),
                subtitle = stringResource(R.string.entry_constant_desc),
                checked = form.constant,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeConstant(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_disabled),
                subtitle = stringResource(R.string.entry_disabled_desc),
                checked = form.disabled,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeDisabled(it).emit() }
            )
        }
    }
}

/**
 * 增强型设定正文工作台。
 *
 * 核心功能：
 * - 实时显示字符数与 Token 预估指标。
 * - 紧密集成宏变量快捷插入工具栏与全屏专注写作入口。
 */
@Composable
private fun ContentWorkbenchPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val charCount = form.content.length
    val estimatedTokens = (charCount / 3.5).toInt().coerceAtLeast(0)

    Panel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.entry_content),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
            ) {
                Text(
                    text = stringResource(R.string.entry_char_token_count, charCount, estimatedTokens),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        FormTextField(
            label = stringResource(R.string.entry_content),
            value = form.content,
            minLines = 8,
            maxLines = 18,
            showMacroBar = true,
            onExpandFullscreen = { WorldBookEntryEditUiIntent.OpenPromptEditor.emit() },
            onChange = { WorldBookEntryEditUiIntent.ChangeContent(it).emit() }
        )
    }
}

/** 分类标签编辑面板。 */
@Composable
private fun CategoriesPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    RpChipInputField(
        title = stringResource(R.string.categories),
        chips = form.category,
        icon = Icons.AutoMirrored.Rounded.Label,
        onChipsChanged = { WorldBookEntryEditUiIntent.SetCategories(it).emit() },
        addLabel = stringResource(R.string.add_category),
        placeholder = stringResource(R.string.category_input_placeholder),
        editDialogTitle = stringResource(R.string.edit_category_title),
        accentColor = MaterialTheme.colorScheme.tertiary
    )
}

// -------------------- Tab 2: 触发规则 --------------------

/** 常驻激活提示横幅。 */
@Composable
private fun ConstantNoticeBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.entry_constant_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** 主关键词输入面板。 */
@Composable
private fun PrimaryKeywordsPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = !form.constant
    RpChipInputField(
        title = stringResource(R.string.primary_keywords),
        subtitle = if (form.constant) {
            stringResource(R.string.primary_keywords_constant_desc)
        } else {
            stringResource(R.string.primary_keywords_conditional_desc)
        },
        chips = form.keywords,
        icon = Icons.Rounded.Tag,
        enabled = enabled,
        onChipsChanged = { WorldBookEntryEditUiIntent.SetKeywords(it).emit() },
        addLabel = stringResource(R.string.add_keyword),
        placeholder = stringResource(R.string.keyword_input_placeholder),
        editDialogTitle = stringResource(R.string.edit_keyword_title),
        accentColor = MaterialTheme.colorScheme.primary
    )
}

/** 次要关键词与判定逻辑因果一体化卡片。 */
@Composable
private fun SecondaryKeywordsAndLogicPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None && !form.constant

    Panel {
        RpSectionHeader(title = stringResource(R.string.secondary_keywords))

        RpChipInputField(
            title = stringResource(R.string.secondary_keywords),
            chips = form.secondaryKeywords,
            icon = Icons.Rounded.FilterList,
            enabled = enabled,
            onChipsChanged = { WorldBookEntryEditUiIntent.SetSecondaryKeywords(it).emit() },
            addLabel = stringResource(R.string.add_keyword),
            placeholder = stringResource(R.string.keyword_input_placeholder),
            editDialogTitle = stringResource(R.string.edit_keyword_title),
            accentColor = MaterialTheme.colorScheme.secondary
        )

        // 判定逻辑选择器
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.entry_logic),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SelectiveLogicSelector(
                logicValue = form.selectiveLogic,
                enabled = enabled,
                onLogicSelected = { WorldBookEntryEditUiIntent.ChangeSelectiveLogic(it).emit() }
            )
        }
    }
}

/** 触发概率滑块面板。 */
@Composable
private fun ProbabilityPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        RpPercentageSlider(
            title = stringResource(R.string.entry_probability),
            value = form.probability.toIntOrNull() ?: 100,
            helper = stringResource(R.string.entry_probability_helper),
            enabled = loadState == WorldBookEntryEditLoadState.None,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeProbability(it.toString()).emit() }
        )
    }
}

/** 扫描历史深度与扫描源多选矩阵面板。 */
@Composable
private fun ScanDepthAndScopePanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None

    Panel {
        RpSectionHeader(title = stringResource(R.string.entry_scan_depth))
        RpNumberStepper(
            title = stringResource(R.string.entry_scan_depth),
            subtitle = stringResource(R.string.entry_scan_depth_desc),
            value = form.scanDepth,
            min = 0,
            max = 200,
            step = 1,
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeScanDepth(it).emit() }
        )

        RpSectionHeader(
            title = stringResource(R.string.entry_matching_scope),
            action = stringResource(R.string.entry_matching_scope_select_all),
            onAction = {
                WorldBookEntryEditUiIntent.ChangeMatchCharacterDescription(true).emit()
                WorldBookEntryEditUiIntent.ChangeMatchCharacterPersonality(true).emit()
                WorldBookEntryEditUiIntent.ChangeMatchCharacterDepthPrompt(true).emit()
                WorldBookEntryEditUiIntent.ChangeMatchScenario(true).emit()
                WorldBookEntryEditUiIntent.ChangeMatchPersonaDescription(true).emit()
                WorldBookEntryEditUiIntent.ChangeMatchCreatorNotes(true).emit()
            }
        )

        MatchingScopeMatrix(form = form, enabled = enabled, emit = emit)
    }
}

/** 扫描源 FilterChip 多选胶囊矩阵。 */
@Composable
private fun MatchingScopeMatrix(
    form: WorldBookEntryEditForm,
    enabled: Boolean,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val scopes = listOf(
        ScopeChipItem(stringResource(R.string.entry_match_description), form.matchCharacterDescription) {
            WorldBookEntryEditUiIntent.ChangeMatchCharacterDescription(it).emit()
        },
        ScopeChipItem(stringResource(R.string.entry_match_personality), form.matchCharacterPersonality) {
            WorldBookEntryEditUiIntent.ChangeMatchCharacterPersonality(it).emit()
        },
        ScopeChipItem(stringResource(R.string.entry_match_character_note), form.matchCharacterDepthPrompt) {
            WorldBookEntryEditUiIntent.ChangeMatchCharacterDepthPrompt(it).emit()
        },
        ScopeChipItem(stringResource(R.string.entry_match_scenario), form.matchScenario) {
            WorldBookEntryEditUiIntent.ChangeMatchScenario(it).emit()
        },
        ScopeChipItem(stringResource(R.string.entry_match_persona_description), form.matchPersonaDescription) {
            WorldBookEntryEditUiIntent.ChangeMatchPersonaDescription(it).emit()
        },
        ScopeChipItem(stringResource(R.string.entry_match_creator_notes), form.matchCreatorNotes) {
            WorldBookEntryEditUiIntent.ChangeMatchCreatorNotes(it).emit()
        }
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        scopes.forEach { item ->
            FilterChip(
                selected = item.selected,
                onClick = { if (enabled) item.onToggle(!item.selected) },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                leadingIcon = if (item.selected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

/** 扫描源选项数据封装。 */
private data class ScopeChipItem(
    val label: String,
    val selected: Boolean,
    val onToggle: (Boolean) -> Unit
)

// -------------------- Tab 3: 高级时序 --------------------

/** 插入位置与模型角色面板。 */
@Composable
private fun PlacementPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None

    Panel {
        RpSectionHeader(title = stringResource(R.string.entry_position))

        PositionSelector(
            positionValue = form.position,
            enabled = enabled,
            onPositionSelected = { WorldBookEntryEditUiIntent.ChangePosition(it).emit() }
        )

        AnimatedVisibility(
            visible = form.position.trim() == LorebookEntry.POSITION_OUTLET.toString(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormTextField(
                    label = stringResource(R.string.entry_outlet),
                    value = form.outletName,
                    onChange = { WorldBookEntryEditUiIntent.ChangeOutletName(it).emit() }
                )
                val macroPreview = "{{outlet::" + form.outletName.ifBlank { "name" } + "}}"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(R.string.entry_outlet_preview, macroPreview),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // 注入角色
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.entry_role),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            RoleSelector(
                roleValue = form.role,
                enabled = enabled,
                onRoleSelected = { WorldBookEntryEditUiIntent.ChangeRole(it).emit() }
            )
        }

        // 排序与插入深度（使用标准全宽步进器，保障横向空间与可读性）
        RpNumberStepper(
            title = stringResource(R.string.entry_order),
            subtitle = stringResource(R.string.entry_order_desc),
            value = form.order,
            min = 0,
            max = 99999,
            step = 10,
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeOrder(it).emit() }
        )

        RpNumberStepper(
            title = stringResource(R.string.entry_depth),
            subtitle = stringResource(R.string.entry_depth_desc),
            value = form.depth,
            min = 0,
            max = 999,
            step = 1,
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeDepth(it).emit() }
        )
    }
}

/** 互斥包含组配置面板。 */
@Composable
private fun InclusionGroupPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None
    val hasGroup = form.group.isNotBlank()

    Panel {
        RpSectionHeader(title = stringResource(R.string.entry_inclusion_group))
        Text(
            text = stringResource(R.string.entry_inclusion_group_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        FormTextField(
            label = stringResource(R.string.entry_group_name),
            value = form.group,
            onChange = { WorldBookEntryEditUiIntent.ChangeGroup(it).emit() }
        )

        AnimatedVisibility(
            visible = hasGroup,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RpNumberStepper(
                    title = stringResource(R.string.entry_group_weight),
                    subtitle = stringResource(R.string.entry_group_weight_desc),
                    value = form.groupWeight,
                    min = 0,
                    max = 9999,
                    step = 1,
                    enabled = enabled,
                    onValueChange = { WorldBookEntryEditUiIntent.ChangeGroupWeight(it).emit() }
                )

                RpSettingsGroup {
                    RpSettingsSwitchTile(
                        title = stringResource(R.string.entry_use_group_scoring),
                        subtitle = stringResource(R.string.entry_use_group_scoring_desc),
                        checked = form.useGroupScoring,
                        enabled = enabled,
                        onCheckedChange = { WorldBookEntryEditUiIntent.ChangeUseGroupScoring(it).emit() }
                    )
                    RpSettingsDivider(startIndent = false)
                    RpSettingsSwitchTile(
                        title = stringResource(R.string.entry_group_override),
                        subtitle = stringResource(R.string.entry_group_override_desc),
                        checked = form.groupOverride,
                        enabled = enabled,
                        onCheckedChange = { WorldBookEntryEditUiIntent.ChangeGroupOverride(it).emit() }
                    )
                }
            }
        }
    }
}

/** 轮数时序控制面板（Sticky / Cooldown / Delay）。 */
@Composable
private fun TimingPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None

    Panel {
        RpSectionHeader(title = stringResource(R.string.entry_timing_recursion))
        Text(
            text = stringResource(R.string.entry_timing_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        RpNumberStepper(
            title = stringResource(R.string.entry_sticky),
            subtitle = stringResource(R.string.entry_sticky_desc),
            value = form.sticky,
            min = 0,
            max = 100,
            step = 1,
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeSticky(it).emit() }
        )

        RpNumberStepper(
            title = stringResource(R.string.entry_cooldown),
            subtitle = stringResource(R.string.entry_cooldown_desc),
            value = form.cooldown,
            min = 0,
            max = 100,
            step = 1,
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeCooldown(it).emit() }
        )

        RpNumberStepper(
            title = stringResource(R.string.entry_delay),
            subtitle = stringResource(R.string.entry_delay_desc),
            value = form.delay,
            min = 0,
            max = 100,
            step = 1,
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeDelay(it).emit() }
        )
    }
}

/** 递归与匹配规则开关面板。 */
@Composable
private fun RecursionAndMatchingPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None

    Panel {
        RpSectionHeader(title = stringResource(R.string.advanced_definition))

        RpSettingsGroup {
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_whole_words),
                checked = form.matchWholeWords == true,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchWholeWords(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_case_sensitive),
                checked = form.caseSensitive == true,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeCaseSensitive(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_ignore_budget),
                checked = form.ignoreBudget,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeIgnoreBudget(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_prevent_recursion),
                checked = form.preventRecursion,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangePreventRecursion(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_delay_until_recursion),
                checked = form.delayUntilRecursion,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeDelayUntilRecursion(it).emit() }
            )
        }
    }
}

/** 扩展 JSON 代码编辑面板。 */
@Composable
private fun ExtensionsJsonPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None
    Panel {
        RpJsonCodeEditorField(
            label = stringResource(R.string.extensions_json),
            value = form.extensionsJson,
            enabled = enabled,
            minHeight = 120.dp,
            maxHeight = 220.dp,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeExtensionsJson(it).emit() }
        )
    }
}

// -------------------- 辅助选择器组件 --------------------

/** 角色选择胶囊组。 */
@Composable
private fun RoleSelector(
    roleValue: String,
    enabled: Boolean,
    onRoleSelected: (String) -> Unit
) {
    val currentRole = roleValue.trim().toIntOrNull() ?: LorebookEntry.ROLE_SYSTEM
    val roles = listOf(
        LorebookEntry.ROLE_SYSTEM to stringResource(R.string.role_system),
        LorebookEntry.ROLE_USER to stringResource(R.string.role_user),
        LorebookEntry.ROLE_ASSISTANT to stringResource(R.string.role_assistant)
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { (roleId, label) ->
            FilterChip(
                selected = currentRole == roleId,
                onClick = { if (enabled) onRoleSelected(roleId.toString()) },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                leadingIcon = if (currentRole == roleId) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

/** 次要逻辑选择器与动态人话释义。 */
@Composable
private fun SelectiveLogicSelector(
    logicValue: String,
    enabled: Boolean,
    onLogicSelected: (String) -> Unit
) {
    val currentLogic = logicValue.trim().toIntOrNull() ?: LorebookEntry.LOGIC_AND_ANY
    val logicOptions = listOf(
        LorebookEntry.LOGIC_AND_ANY to stringResource(R.string.logic_and_any),
        LorebookEntry.LOGIC_AND_ALL to stringResource(R.string.logic_and_all),
        LorebookEntry.LOGIC_NOT_ANY to stringResource(R.string.logic_not_any),
        LorebookEntry.LOGIC_NOT_ALL to stringResource(R.string.logic_not_all)
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            logicOptions.forEach { (logicId, label) ->
                FilterChip(
                    selected = currentLogic == logicId,
                    onClick = { if (enabled) onLogicSelected(logicId.toString()) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = if (currentLogic == logicId) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Text(
            text = when (currentLogic) {
                LorebookEntry.LOGIC_AND_ALL -> stringResource(R.string.logic_and_all_helper)
                LorebookEntry.LOGIC_NOT_ANY -> stringResource(R.string.logic_not_any_helper)
                LorebookEntry.LOGIC_NOT_ALL -> stringResource(R.string.logic_not_all_helper)
                else -> stringResource(R.string.logic_and_any_helper)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

/** 插入位置 Exposed 下拉菜单选择器。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionSelector(
    positionValue: String,
    enabled: Boolean,
    onPositionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentPosition = positionValue.trim().toIntOrNull() ?: LorebookEntry.POSITION_AT_DEPTH

    val positions = listOf(
        LorebookEntry.POSITION_AT_DEPTH to stringResource(R.string.position_at_depth),
        LorebookEntry.POSITION_BEFORE to stringResource(R.string.position_before_char),
        LorebookEntry.POSITION_AFTER to stringResource(R.string.position_after_char),
        LorebookEntry.POSITION_AN_TOP to stringResource(R.string.position_an_top),
        LorebookEntry.POSITION_AN_BOTTOM to stringResource(R.string.position_an_bottom),
        LorebookEntry.POSITION_EXAMPLE_TOP to stringResource(R.string.position_example_top),
        LorebookEntry.POSITION_EXAMPLE_BOTTOM to stringResource(R.string.position_example_bottom),
        LorebookEntry.POSITION_OUTLET to stringResource(R.string.position_outlet)
    )

    val currentLabel = positions.firstOrNull { it.first == currentPosition }?.second
        ?: stringResource(R.string.position_at_depth)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.entry_position)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            positions.forEach { (posId, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onPositionSelected(posId.toString())
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 底部操作按钮栏。 */
@Composable
private fun ActionPanel(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = state.loadState == WorldBookEntryEditLoadState.None,
            onClick = { WorldBookEntryEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = state.loadState == WorldBookEntryEditLoadState.None,
            onClick = { WorldBookEntryEditUiIntent.SaveEntry.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    state.loadState == WorldBookEntryEditLoadState.Saving -> stringResource(R.string.saving)
                    state.mode == WorldBookEntryEditMode.Create -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

/** 顶栏保存操作按钮。 */
@Composable
private fun TopBarSaveButton(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = state.loadState == WorldBookEntryEditLoadState.None,
        onClick = { WorldBookEntryEditUiIntent.SaveEntry.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                state.loadState == WorldBookEntryEditLoadState.Saving -> stringResource(R.string.saving)
                state.mode == WorldBookEntryEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

/** 统一对话框路由分发。 */
@Composable
private fun DialogSwitch(
    dialogState: WorldBookEntryEditDialogState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    when (dialogState) {
        WorldBookEntryEditDialogState.None -> Unit
        is WorldBookEntryEditDialogState.DeleteConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_world_book_entry_title),
            message = stringResource(R.string.delete_world_book_entry_message, dialogState.entryName),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { WorldBookEntryEditUiIntent.ConfirmDeleteEntry.emit() }
        )
        WorldBookEntryEditDialogState.UnsavedChangesConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { WorldBookEntryEditUiIntent.ConfirmDiscardChanges.emit() }
        )
        is WorldBookEntryEditDialogState.PromptEditor -> AppPromptEditorDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.entry_content),
            value = dialogState.draftText,
            availableMacros = DEFAULT_PROMPT_MACROS,
            onValueChange = { WorldBookEntryEditUiIntent.ChangePromptEditorDraft(it).emit() },
            onConfirm = { WorldBookEntryEditUiIntent.ConfirmPromptEditor.emit() }
        )
    }
}

/** 现代化基础表单文本输入框，支持宏变量插入工具栏与全屏写作拓展。 */
@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines.coerceAtLeast(6) else 1,
    singleLine: Boolean = minLines == 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    outputTransformation: OutputTransformation = rememberPromptMacroOutputTransformation(),
    showMacroBar: Boolean = false,
    macros: List<String> = DEFAULT_PROMPT_MACROS,
    onExpandFullscreen: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    val textFieldState = rememberBoundTextFieldState(value, onChange)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RpScrollableOutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (maxLines > 1) Modifier.heightIn(max = 240.dp) else Modifier),
            state = textFieldState,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            minLines = minLines,
            maxLines = maxLines.coerceAtLeast(minLines),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            outputTransformation = outputTransformation,
            shape = RoundedCornerShape(12.dp)
        )
        if (showMacroBar) {
            RpMacroActionBar(
                macros = macros,
                onInsertMacro = { macro ->
                    val currentText = textFieldState.text.toString()
                    val selection = textFieldState.selection
                    val start = selection.min.coerceIn(0, currentText.length)
                    val end = selection.max.coerceIn(0, currentText.length)
                    val before = currentText.substring(0, start)
                    val insertContent = if (macro == "<START>") {
                        if (before.isNotEmpty() && !before.endsWith("\n")) "\n<START>\n" else "<START>\n"
                    } else {
                        macro
                    }
                    val newCursorPos = start + insertContent.length
                    textFieldState.edit {
                        replace(start, end, insertContent)
                        this.selection = TextRange(newCursorPos)
                    }
                },
                onFullscreenClick = onExpandFullscreen
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookEntryEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookEntryEditLayout(
            uiState = WorldBookEntryEditUiState.Normal(
                mode = WorldBookEntryEditMode.Edit,
                form = WorldBookEntryEditForm(
                    id = 1L,
                    lorebookId = 1L,
                    name = "Old District",
                    keywords = listOf("district", "railway"),
                    secondaryKeywords = listOf("archive"),
                    category = listOf("location"),
                    content = "The old district is divided by three elevated railways."
                )
            ),
            emit = {}
        )
    }
}
