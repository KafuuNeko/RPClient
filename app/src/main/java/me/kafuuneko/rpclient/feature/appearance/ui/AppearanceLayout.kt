package me.kafuuneko.rpclient.feature.appearance.ui

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiIntent
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiState
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundAdjustment
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorState
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import me.kafuuneko.rpclient.libs.theme.AppThemePalette
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.LocalAppDarkTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.LocalPageBottomBarOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageBubbleOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageContentOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageTopBarOpacity
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.pageUserBubbleContentColor
import kotlin.math.roundToInt
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedButton as OutlinedButton

/** 外观选择和背景编辑共用入口，所有持久化操作通过 Intent 交给 ViewModel。 */
@Composable
fun AppearanceLayout(state: AppearanceUiState, emit: (AppearanceUiIntent) -> Unit) {
    if (state is AppearanceUiState.Finished) {
        AppearanceLayout(state = state.previous, emit = {})
        return
    }
    state as AppearanceUiState.Normal
    BackHandler(onBack = { emit(AppearanceUiIntent.Back) })
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(
                    state.editor?.settings?.page?.title() ?: R.string.appearance_title
                ),
                onBack = { emit(AppearanceUiIntent.Back) },
            )
        },
        content = { padding ->
            // 编辑页保留自己的草稿与取消入口，主题预设页只负责选择。
            if (state.editor != null) BackgroundEditor(
                state = state.editor,
                modifier = Modifier.padding(padding),
                emit = emit,
            )
            else AppearanceChoices(state = state, modifier = Modifier.padding(padding), emit = emit)
        },
    )
    state.editor?.let { BackgroundEditorDialog(it, emit) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceChoices(
    state: AppearanceUiState.Normal,
    modifier: Modifier,
    emit: (AppearanceUiIntent) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.appearance_palette_heading),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.appearance_palette_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { emit(AppearanceUiIntent.SelectMode(mode)) },
                        label = { Text(stringResource(mode.title())) },
                    )
                }
            }
        }
        // 两列小卡片展示完整浅深配色中的表面、主色与辅助色。
        AppThemePalette.entries
            .filter { appThemePalette -> appThemePalette != AppThemePalette.Dynamic }
            .chunked(2)
            .forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { palette ->
                            PaletteCard(
                                palette = palette,
                                selected = state.palette == palette,
                                modifier = Modifier.weight(1f),
                                emit = emit,
                            )
                        }
                    }
                }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
            FilterChip(
                selected = state.palette == AppThemePalette.Dynamic,
                onClick = { emit(AppearanceUiIntent.SelectPalette(AppThemePalette.Dynamic)) },
                label = { Text(stringResource(R.string.appearance_dynamic)) },
            )
        }
        item {
            Surface(
                onClick = { emit(AppearanceUiIntent.EditDialogAppearance) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dialog_appearance_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.appearance_backgrounds),
                style = MaterialTheme.typography.titleLarge
            )
        }
        BackgroundPage.entries.forEach { page ->
            item {
                Surface(
                    onClick = { emit(AppearanceUiIntent.EditBackground(page)) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(page.title()),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: AppThemePalette,
    selected: Boolean,
    modifier: Modifier,
    emit: (AppearanceUiIntent) -> Unit,
) {
    val dark = LocalAppDarkTheme.current
    val outline =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { emit(AppearanceUiIntent.SelectPalette(palette)) },
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 局部主题仅用于色板缩略图，不改变外层页面或用户偏好。
        AppTheme(darkTheme = dark, dynamicColor = false, palette = palette) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = 0.72f)
                        .height(13.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = 0.9f)
                        .height(13.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(color, CircleShape),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(palette.title()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            if (selected) Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.appearance_selected),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 预览独立于设置列表，方向切换后保留列表位置和当前草稿。 */
@Composable
private fun BackgroundEditor(
    state: BackgroundEditorState,
    modifier: Modifier,
    emit: (AppearanceUiIntent) -> Unit
) {
    val listState = rememberLazyListState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(modifier.fillMaxSize()) {
        // 竖屏限制预览占用高度，为设置保留可滚动空间；横屏将两区并排。
        if (landscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BackgroundPreview(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                BackgroundAdjustments(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    listState = listState,
                    emit = emit,
                )
            }
        } else {
            val previewHeight = (maxHeight * 0.45f).coerceAtMost(280.dp)
            Column(Modifier.fillMaxSize()) {
                BackgroundPreview(
                    state = state,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 12.dp, end = 20.dp)
                        .fillMaxWidth()
                        .height(previewHeight),
                )
                BackgroundAdjustments(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    listState = listState,
                    emit = emit,
                )
            }
        }
    }
}

/** 仅设置项滚动，预览区域始终可见。 */
@Composable
private fun BackgroundAdjustments(
    state: BackgroundEditorState,
    modifier: Modifier,
    listState: LazyListState,
    emit: (AppearanceUiIntent) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.settings.page == BackgroundPage.General) {
            item {
                Text(
                    text = stringResource(R.string.appearance_general_background_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { emit(AppearanceUiIntent.PickImage) },
                    enabled = !state.working
                ) {
                    Text(stringResource(R.string.appearance_choose_image))
                }
                TextButton(
                    onClick = { emit(AppearanceUiIntent.ResetBackground) },
                    enabled = !state.working
                ) {
                    Text(stringResource(R.string.appearance_reset_background))
                }
            }
        }
        if (state.settings.page == BackgroundPage.Chat) {
            item {
                BackgroundOption(
                    title = R.string.appearance_use_character_background,
                    subtitle = R.string.appearance_character_background_hint,
                    checked = state.settings.useCharacterBackground,
                    enabled = !state.working,
                    onCheckedChange = { enabled ->
                        emit(
                            AppearanceUiIntent.SetUseCharacterBackground(
                                enabled
                            )
                        )
                    },
                )
            }
        }
        if (state.settings.page != BackgroundPage.General) {
            item {
                BackgroundOption(
                    title = R.string.appearance_follow_general,
                    subtitle = R.string.appearance_follow_general_hint,
                    checked = state.settings.followGeneral,
                    enabled = !state.working,
                    onCheckedChange = { enabled -> emit(AppearanceUiIntent.SetFollowGeneral(enabled)) },
                )
            }
        }
        if (state.settings.page == BackgroundPage.General || !state.settings.followGeneral) {
            // 参数数值清晰展示，拖动保持低分辨率预览，松手再渲染最终图。
            if (state.effectiveSettings.fileUuid != null) {
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_blur,
                        value = state.settings.blur.toFloat(),
                        range = 0f..100f,
                        field = BackgroundAdjustment.Blur,
                        state = state,
                        emit = emit,
                    )
                }
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_scrim,
                        value = state.settings.scrim,
                        range = 0f..0.9f,
                        field = BackgroundAdjustment.Scrim,
                        state = state,
                        emit = emit,
                    )
                }
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_horizontal,
                        value = state.settings.focalX,
                        range = 0f..1f,
                        field = BackgroundAdjustment.Horizontal,
                        state = state,
                        emit = emit,
                    )
                }
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_vertical,
                        value = state.settings.focalY,
                        range = 0f..1f,
                        field = BackgroundAdjustment.Vertical,
                        state = state,
                        emit = emit,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.appearance_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 栏位背景独立于图片；0% 不透明、100% 完全透明，不降低文字和按钮透明度。
            item {
                AdjustmentSlider(
                    title = R.string.appearance_top_bar_transparency,
                    value = 1f - state.settings.topBarOpacity,
                    range = 0f..1f,
                    field = BackgroundAdjustment.TopBarTransparency,
                    state = state,
                    emit = emit,
                )
            }
            item {
                AdjustmentSlider(
                    title = R.string.appearance_top_bar_blur,
                    value = state.settings.topBarBlur.toFloat(),
                    range = 0f..100f,
                    field = BackgroundAdjustment.TopBarBlur,
                    state = state,
                    emit = emit,
                    enabled = state.settings.topBarOpacity < 1f,
                )
            }
            item {
                AdjustmentSlider(
                    title = R.string.appearance_bottom_bar_transparency,
                    value = 1f - state.settings.bottomBarOpacity,
                    range = 0f..1f,
                    field = BackgroundAdjustment.BottomBarTransparency,
                    state = state,
                    emit = emit,
                )
            }
            item {
                AdjustmentSlider(
                    title = R.string.appearance_bottom_bar_blur,
                    value = state.settings.bottomBarBlur.toFloat(),
                    range = 0f..100f,
                    field = BackgroundAdjustment.BottomBarBlur,
                    state = state,
                    emit = emit,
                    enabled = state.settings.bottomBarOpacity < 1f,
                )
            }
            if (state.settings.page == BackgroundPage.Story || state.settings.page == BackgroundPage.General) {
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_content_transparency,
                        value = 1f - state.settings.opacity,
                        range = 0f..1f,
                        field = BackgroundAdjustment.ContentTransparency,
                        state = state,
                        emit = emit,
                    )
                }
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_content_blur,
                        value = state.settings.contentBlur.toFloat(),
                        range = 0f..100f,
                        field = BackgroundAdjustment.ContentBlur,
                        state = state,
                        emit = emit,
                        enabled = state.settings.opacity < 1f,
                    )
                }
            } else {
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_content_transparency,
                        value = 1f - state.settings.bubbleOpacity,
                        range = 0f..1f,
                        field = BackgroundAdjustment.BubbleTransparency,
                        state = state,
                        emit = emit,
                    )
                }
                item {
                    AdjustmentSlider(
                        title = R.string.appearance_content_blur,
                        value = state.settings.bubbleBlur.toFloat(),
                        range = 0f..100f,
                        field = BackgroundAdjustment.BubbleBlur,
                        state = state,
                        emit = emit,
                        enabled = state.settings.bubbleOpacity < 1f,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.appearance_surface_blur_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.appearance_transparency_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    text = stringResource(error),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { emit(AppearanceUiIntent.ApplyBackground) },
                    enabled = !state.working
                ) {
                    Text(stringResource(R.string.save))
                }
                TextButton(onClick = { emit(AppearanceUiIntent.Back) }) { Text(stringResource(R.string.cancel)) }
                if (state.working || state.rendering) CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/** 外观开关复用设置页的文字与开关排列，不额外引入卡片层级。 */
@Composable
private fun BackgroundOption(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun BackgroundPreview(state: BackgroundEditorState, modifier: Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        // 横屏高度较小时压缩内部留白，保留菜单、两条消息和底部栏的完整预览。
        val compact = maxHeight < 240.dp
        val padding = if (compact) 8.dp else 16.dp
        PageBackdrop(
            state = PageBackgroundUiState(
                settings = state.effectiveSettings,
                image = state.image,
                surfaceImages = state.surfaceImages,
            ),
        ) {
            // 预览与真实页面共用透明度来源，三块表面分别反映对应滑块。
            Column(Modifier.fillMaxSize()) {
                PageSurface(
                    kind = PageSurfaceKind.TopBar,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageTopBarOpacity.current),
                ) {
                    Text(
                        text = stringResource(R.string.appearance_preview),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(padding),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                // 消息变短也保持整行宽度，避免右侧气泡随内容收窄后左移。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                ) {
                    val isStory = state.settings.page == BackgroundPage.Story
                    val isContent = isStory || state.settings.page == BackgroundPage.General
                    val opacity =
                        if (isContent) LocalPageContentOpacity.current else LocalPageBubbleOpacity.current
                    PageSurface(
                        kind = if (isContent) PageSurfaceKind.Content else PageSurfaceKind.Bubble,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
                    ) {
                        val previewLabelResId =
                            if (isStory) R.string.appearance_preview_story else if (isContent) R.string.appearance_preview_content else R.string.appearance_preview_message
                        Text(
                            text = stringResource(id = previewLabelResId),
                            modifier = Modifier.padding(padding),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!isContent) PageSurface(
                        kind = PageSurfaceKind.Bubble,
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = opacity),
                        contentColor = pageUserBubbleContentColor(),
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_preview_reply),
                            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
                        )
                    }
                }
                PageSurface(
                    kind = PageSurfaceKind.BottomBar,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBottomBarOpacity.current),
                ) {
                    Text(
                        text = stringResource(R.string.appearance_preview_input),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(padding),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    @StringRes title: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    field: BackgroundAdjustment,
    state: BackgroundEditorState,
    emit: (AppearanceUiIntent) -> Unit,
    enabled: Boolean = true,
) {
    val label = stringResource(title)
    Column {
        Row {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (field.isBlur) value.roundToInt().toString() else stringResource(
                    R.string.appearance_percent,
                    (value * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            modifier = Modifier.semantics { contentDescription = label },
            value = value,
            onValueChange = { float ->
                emit(
                    AppearanceUiIntent.ChangeAdjustment(
                        field = field,
                        value = float,
                    ),
                )
            },
            onValueChangeFinished = { emit(AppearanceUiIntent.FinishAdjustment) },
            valueRange = range,
            enabled = !state.working && enabled,
        )
    }
}

@StringRes
private fun BackgroundPage.title(): Int = when (this) {
    BackgroundPage.General -> R.string.appearance_general_background
    BackgroundPage.Chat -> R.string.appearance_chat_background
    BackgroundPage.Group -> R.string.appearance_group_background
    BackgroundPage.Story -> R.string.appearance_story_background
}

@StringRes
private fun AppThemeMode.title(): Int = when (this) {
    AppThemeMode.FollowSystem -> R.string.theme_follow_system
    AppThemeMode.Light -> R.string.theme_light
    AppThemeMode.Dark -> R.string.theme_dark
}

@StringRes
private fun AppThemePalette.title(): Int = when (this) {
    AppThemePalette.Classic -> R.string.appearance_classic
    AppThemePalette.Sakura -> R.string.appearance_sakura
    AppThemePalette.Forest -> R.string.appearance_forest
    AppThemePalette.Twilight -> R.string.appearance_twilight
    AppThemePalette.Parchment -> R.string.appearance_parchment
    AppThemePalette.Graphite -> R.string.appearance_graphite
    AppThemePalette.Dynamic -> R.string.appearance_dynamic
}

@Preview
@Composable
private fun AppearancePreview() {
    AppTheme(dynamicColor = false) {
        AppearanceLayout(
            state = AppearanceUiState.Normal(),
            emit = {})
    }
}
