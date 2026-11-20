package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.CharacterSearchField
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpCheckBadge
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors

/**
 * 通用角色选择器弹窗中的角色展示项。
 *
 * @param id 角色唯一标识
 * @param name 角色名称
 * @param description 角色简介摘要
 * @param tags 角色标签列表
 * @param avatarImage 已按需解码的角色头像位图，为空时显示首字母占位
 */
data class CharacterPickerDialogItem(
    val id: Long,
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val avatarImage: ImageBitmap? = null,
)

/** 通用角色选择器弹窗的交互模式。 */
enum class CharacterPickerMode {
    /** 单选模式：点击候选行立即选中并关闭弹窗。 */
    Single,

    /** 多选模式：支持勾选多个角色、按已选筛选并在确认后统一提交。 */
    Multiple
}

/**
 * 通用可检索角色选择器弹窗，供单聊、群聊、故事创建页与群聊成员添加复用。
 *
 * - 搜索过滤与“仅看已选”筛选结果由调用方状态树提供，组件仅负责渲染与回传交互；
 * - 单选模式使用单选圆点并在点选后立即提交，减少多余确认步骤；
 * - 多选模式提供“全部 / 已选”快速筛选与底部确认操作，适配数十至数百张角色卡场景。
 *
 * @param title 弹窗标题
 * @param mode 选择模式（单选或多选）
 * @param totalCount 当前候选角色总数
 * @param visibleCharacters 经当前搜索和筛选条件过滤后可见的角色列表
 * @param selectedIds 当前已选中的角色 ID 集合
 * @param query 当前搜索关键词
 * @param showSelectedOnly 是否仅展示已选中的角色（多选模式有效）
 * @param badgeIcon 标题栏图标
 * @param confirmText 多选模式确认按钮文案
 * @param confirmEnabled 多选模式确认按钮是否可点击
 * @param emptyText 无任何候选角色时的空态提示
 * @param onQueryChange 搜索关键词变化回调
 * @param onShowSelectedOnlyChange 切换“全部 / 已选”筛选回调
 * @param onVisibleCharactersChanged 可见角色列表项及目标像素尺寸变化回调，供状态层按需解码头像
 * @param onCharacterClick 点击某个角色行的回调
 * @param onConfirm 多选模式确认回调
 * @param onDismissRequest 关闭弹窗回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterPickerDialog(
    title: String,
    mode: CharacterPickerMode,
    totalCount: Int,
    visibleCharacters: List<CharacterPickerDialogItem>,
    selectedIds: Set<Long>,
    query: String,
    showSelectedOnly: Boolean = false,
    badgeIcon: ImageVector = if (mode == CharacterPickerMode.Single) {
        Icons.Rounded.Person
    } else {
        Icons.Rounded.Groups
    },
    confirmText: String = stringResource(R.string.confirm),
    confirmEnabled: Boolean = true,
    emptyText: String = stringResource(R.string.no_characters_for_chat),
    onQueryChange: (String) -> Unit,
    onShowSelectedOnlyChange: ((Boolean) -> Unit)? = null,
    onVisibleCharactersChanged: ((Set<Long>, Int) -> Unit)? = null,
    onCharacterClick: (Long) -> Unit,
    onConfirm: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    // 根据模式生成实时副标题，让用户在大量角色中随时感知总量或已选进度
    val subtitle = when (mode) {
        CharacterPickerMode.Single -> stringResource(
            R.string.picker_characters_total_count,
            totalCount,
        )

        CharacterPickerMode.Multiple -> stringResource(
            R.string.picker_selected_of_total_count,
            selectedIds.size,
            totalCount,
        )
    }
    val listState = rememberLazyListState()
    val avatarSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val showFilterChips = mode == CharacterPickerMode.Multiple &&
        onShowSelectedOnlyChange != null &&
        totalCount > 0

    // 仅在列表 ID 序列或目标尺寸变化时重启监听，避免头像位图回填触发不必要的协程重建
    val visibleCharacterOrder = remember(visibleCharacters) {
        visibleCharacters.map { item -> item.id }
    }
    LaunchedEffect(listState, visibleCharacterOrder, avatarSizePx, onVisibleCharactersChanged) {
        val callback = onVisibleCharactersChanged ?: return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { itemInfo -> itemInfo.key as? Long }
                .toSet()
        }
            .distinctUntilChanged()
            .collect { visibleIds ->
                callback(visibleIds, avatarSizePx)
            }
    }

    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        subtitleMaxLines = 1,
        badgeIcon = badgeIcon,
        compactHeader = true,
        confirmText = confirmText,
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = confirmEnabled,
        onConfirm = if (mode == CharacterPickerMode.Multiple) onConfirm else null,
        onDismiss = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 560.dp),
    ) {
        // 搜索框、筛选条与虚拟化列表共享弹窗剩余高度，软键盘弹出时自动压缩列表区域
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (totalCount > 0) {
                CharacterSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                )
            }
            if (showFilterChips) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = !showSelectedOnly,
                        onClick = { onShowSelectedOnlyChange(false) },
                        label = {
                            Text(
                                text = stringResource(R.string.picker_filter_all_count, totalCount),
                                maxLines = 1,
                            )
                        },
                        colors = pageFilterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = showSelectedOnly,
                        onClick = { onShowSelectedOnlyChange(true) },
                        label = {
                            Text(
                                text = stringResource(
                                    R.string.picker_filter_selected_count,
                                    selectedIds.size,
                                ),
                                maxLines = 1,
                            )
                        },
                        colors = pageFilterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
            // 按总列表、仅看已选和搜索结果分别给出明确的空态引导
            when {
                totalCount == 0 -> PickerEmptyText(text = emptyText)

                visibleCharacters.isEmpty() && showSelectedOnly && query.isBlank() -> PickerEmptyText(
                    text = stringResource(R.string.picker_no_selected_characters),
                )

                visibleCharacters.isEmpty() -> PickerEmptyText(
                    text = stringResource(R.string.no_matching_characters),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .draggableLazyListScrollIndicator(listState),
                    contentPadding = PaddingValues(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = visibleCharacters,
                        key = { item -> item.id },
                    ) { character ->
                        CharacterPickerRow(
                            character = character,
                            mode = mode,
                            selected = character.id in selectedIds,
                            onClick = { onCharacterClick(character.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 渲染选择器内的紧凑角色行，使用头像色块、简介摘要、标签与对应模式的选中指示器。 */
@Composable
private fun CharacterPickerRow(
    character: CharacterPickerDialogItem,
    mode: CharacterPickerMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = getMacaronColor(character.name)
    val initial = character.name.firstOrNull()?.uppercase() ?: "?"
    // 选中态通过边框与柔和主题底色同时区分，在亮暗及透明弹窗主题下均清晰可辨
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            },
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpAvatar(
                text = initial,
                color = accent,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                image = character.avatarImage,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = character.description.ifBlank { stringResource(R.string.no_description) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (character.tags.isNotEmpty()) {
                    RpTagRow(tags = character.tags, maxCount = 3)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (mode == CharacterPickerMode.Single) {
                RadioButton(selected = selected, onClick = null)
            } else {
                RpCheckBadge(selected = selected, size = 22.dp)
            }
        }
    }
}

/** 渲染角色选择器内的空态提示文案。 */
@Composable
private fun PickerEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(name = "CharacterPicker - Multiple", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterPickerMultiplePreview() {
    AppTheme(dynamicColor = false) {
        CharacterPickerDialog(
            title = "Select members",
            mode = CharacterPickerMode.Multiple,
            totalCount = previewPickerCharacters.size,
            visibleCharacters = previewPickerCharacters,
            selectedIds = setOf(1L, 3L),
            query = "",
            showSelectedOnly = false,
            onQueryChange = {},
            onShowSelectedOnlyChange = {},
            onCharacterClick = {},
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}

@Preview(name = "CharacterPicker - Single", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterPickerSinglePreview() {
    AppTheme(dynamicColor = false) {
        CharacterPickerDialog(
            title = "Select character",
            mode = CharacterPickerMode.Single,
            totalCount = previewPickerCharacters.size,
            visibleCharacters = previewPickerCharacters,
            selectedIds = setOf(1L),
            query = "",
            onQueryChange = {},
            onCharacterClick = {},
            onDismissRequest = {},
        )
    }
}

private val previewPickerCharacters = listOf(
    CharacterPickerDialogItem(
        id = 1L,
        name = "Lyra",
        description = "A star navigator mapping forgotten constellations.",
        tags = listOf("Sci-Fi", "Explorer"),
    ),
    CharacterPickerDialogItem(
        id = 2L,
        name = "Mina",
        description = "A calm archivist with an encyclopedic memory.",
        tags = listOf("Mystery"),
    ),
    CharacterPickerDialogItem(
        id = 3L,
        name = "Rowan",
        description = "An impulsive mechanic carrying a brass lantern.",
        tags = listOf("Steampunk", "Ally"),
    ),
)
