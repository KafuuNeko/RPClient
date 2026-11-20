package me.kafuuneko.rpclient.feature.contentsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiIntent
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.ui.theme.LocalAppDarkTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedButton
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 状态横幅中的紧凑操作项模型。 */
internal data class SearchBannerAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

/**
 * 搜索结果卡片，沿用首页内容卡的马卡龙强调条、图标气泡和表面层次。
 * - 顶部对象区点击后按常规方式打开对应会话或故事。
 * - 中部摘录卡片高亮匹配片段，点击后直接定位到命中消息或章节。
 * - 底部操作区提供单对象内检索入口，仅在全局视图下展示。
 *
 * @param hit 当前搜索命中模型。
 * @param showWithin 是否展示“查看此内容中的匹配”底部入口。
 * @param enabled 当前结果是否可打开或用于缩小检索范围。
 * @param modifier 卡片外部布局修饰符。
 * @param emit 页面意图分发回调。
 */
@Composable
internal fun SearchResultCard(
    hit: SearchHit,
    showWithin: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    val accentKey = remember(hit.kind, hit.names, hit.title) { hitAccentKey(hit) }
    val accentColor = remember(accentKey) { getMacaronColor(accentKey) }
    // 复用首页卡片的柔和渐变与马卡龙强调条，保持跨页面视觉识别一致。
    val ambientBrush = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.08f),
                accentColor.copy(alpha = 0.02f),
                Color.Transparent,
            ),
        )
    }
    val hasLocateZone = hit.snippet.isNotEmpty() || hit.sourceId > 0L

    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(ambientBrush),
        ) {
            // 左侧圆角强调条随整卡高度自适应伸展。
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(2.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                SearchResultHeader(
                    hit = hit,
                    accentColor = accentColor,
                    compactBottom = hasLocateZone || showWithin,
                    enabled = enabled,
                    onClick = { ContentSearchUiIntent.Open(hit = hit, locate = false).emit() },
                )
                if (hit.snippet.isNotEmpty()) {
                    SearchResultSnippetBox(
                        hit = hit,
                        bottomPadding = if (showWithin) 10.dp else 12.dp,
                        enabled = enabled,
                        onClick = { ContentSearchUiIntent.Open(hit = hit, locate = true).emit() },
                    )
                } else if (hit.sourceId > 0L) {
                    SearchResultLocateRow(
                        sourceLabel = hit.sourceLabel,
                        bottomPadding = if (showWithin) 10.dp else 12.dp,
                        enabled = enabled,
                        onClick = { ContentSearchUiIntent.Open(hit = hit, locate = true).emit() },
                    )
                }
                if (showWithin) {
                    SearchResultWithinFooter(
                        enabled = enabled,
                        onClick = { ContentSearchUiIntent.SearchWithin(hit).emit() },
                    )
                }
            }
        }
    }
}

/** 结果卡片顶部对象信息区：展示类型气泡、标题与元信息标签，点击常规打开。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultHeader(
    hit: SearchHit,
    accentColor: Color,
    compactBottom: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val participantNames = remember(hit.names) {
        hit.names
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }
    val formattedTime = remember(hit.latestTime) { formatHitTimestamp(hit.latestTime) }

    // 标题区域独立响应点击，保证常规打开与正文定位互不冲突。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = if (compactBottom) 8.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RpIconBubble(
            icon = kindIcon(hit.kind),
            size = 36.dp,
            containerColor = accentColor.copy(alpha = 0.14f),
            contentColor = accentColor,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = hit.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RpTagPill(text = stringResource(kindLabel(hit.kind)))
                if (participantNames.isNotEmpty()) {
                    RpMetaPill(text = participantNames)
                }
                if (formattedTime != null) {
                    RpMetaPill(text = formattedTime)
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
        )
    }
}

/** 命中片段摘录卡片，保留原始片段文本节点并突出关键词与历史定位入口。 */
@Composable
private fun SearchResultSnippetBox(
    hit: SearchHit,
    bottomPadding: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val darkTheme = LocalAppDarkTheme.current
    val highlightBackground = if (darkTheme) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    val highlightForeground = if (darkTheme) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val snippet = remember(
        hit.snippet,
        hit.matchStart,
        hit.matchEnd,
        highlightBackground,
        highlightForeground,
    ) {
        buildHighlightedSnippet(
            snippet = hit.snippet,
            matchStart = hit.matchStart,
            matchEnd = hit.matchEnd,
            background = highlightBackground,
            foreground = highlightForeground,
        )
    }

    // 独立嵌套摘录底板增强层级区分，整块可点击以跳转至命中源位置。
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (hit.sourceLabel.isNotBlank()) {
                SearchSnippetSourceLabel(label = hit.sourceLabel)
            }
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 在摘录中标示对应消息或章节的来源。 */
@Composable
private fun SearchSnippetSourceLabel(label: String) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 保留箭头空间，过长的来源名称在同一行内截断。
        Icon(
            imageVector = Icons.Rounded.FormatQuote,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = accent,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = accent.copy(alpha = 0.72f),
        )
    }
}

/** 无正文片段但存在精确源定位（如匹配章节标题）时的直达操作条。 */
@Composable
private fun SearchResultLocateRow(
    sourceLabel: String,
    bottomPadding: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 使用轻主色底板提示可直达具体来源位置。
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
            .heightIn(min = 42.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatQuote,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            if (sourceLabel.isNotBlank()) {
                Text(
                    text = sourceLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = stringResource(R.string.content_search_open_match),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 卡片底部的“查看此内容中的匹配”紧凑动作条。 */
@Composable
private fun SearchResultWithinFooter(enabled: Boolean, onClick: () -> Unit) {
    // 以内缩细线分隔主体与二次钻取入口，保持卡片底部紧凑对称。
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ManageSearch,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.content_search_within),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        )
    }
}

/**
 * 统一的语义状态横幅卡片，用于索引维护、部分结果、停止提示及错误恢复。
 * - 小屏与大字体场景下通过 [FlowRow] 自动折行操作按钮，避免横向拥挤或裁切。
 *
 * @param icon 状态图标。
 * @param message 状态说明文案。
 * @param accentColor 图标与边框语义强调色。
 * @param showSpinner 是否以环形进度替代静态图标（用于构建或清理索引中）。
 * @param actions 可选快捷恢复操作列表。
 * @param modifier 外部修饰符。
 */
@Composable
internal fun SearchStatusBanner(
    icon: ImageVector,
    message: String,
    accentColor: Color,
    showSpinner: Boolean = false,
    actions: List<SearchBannerAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    // 语义区域启用 Polite 播报，确保状态变化可被无障碍读屏感知。
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = accentColor.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusBannerBadge(
                    icon = icon,
                    accentColor = accentColor,
                    showSpinner = showSpinner,
                )
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (actions.isNotEmpty()) {
                StatusBannerActions(actions = actions, accentColor = accentColor)
            }
        }
    }
}

/** 状态横幅左侧语义徽章，支持静态图标或维护态环形进度。 */
@Composable
private fun StatusBannerBadge(
    icon: ImageVector,
    accentColor: Color,
    showSpinner: Boolean,
) {
    if (showSpinner) {
        // 索引构建或清理中展示紧凑进度环，保持与静态图标相同的容器占位。
        AppSurface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(11.dp),
            color = accentColor.copy(alpha = 0.14f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = accentColor,
                )
            }
        }
    } else {
        RpIconBubble(
            icon = icon,
            size = 34.dp,
            iconSize = 18.dp,
            shape = RoundedCornerShape(11.dp),
            containerColor = accentColor.copy(alpha = 0.14f),
            contentColor = accentColor,
        )
    }
}

/** 状态横幅底部的自适应折行操作按钮组。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBannerActions(
    actions: List<SearchBannerAction>,
    accentColor: Color,
) {
    // 多个恢复入口采用流式排布，适配 320dp 窄屏和大字号模式。
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.forEach { action ->
            PageOutlinedButton(
                onClick = action.onClick,
                modifier = Modifier.heightIn(min = 34.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                border = BorderStroke(
                    width = 0.75.dp,
                    color = accentColor.copy(alpha = 0.35f),
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (action.icon != null) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 居中引导与空态卡片，承载空闲引导、无匹配结果及慢查询初始等待状态。
 *
 * @param title 主标题文案。
 * @param subtitle 可选辅助说明文案。
 * @param icon 状态图标（加载态时不使用）。
 * @param iconTint 图标强调色。
 * @param isLoading 是否渲染加载指示器。
 * @param action 可选操作按钮（如对象内无结果时一键返回全部内容）。
 */
@Composable
internal fun SearchEmptyStateCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    isLoading: Boolean = false,
    action: SearchBannerAction? = null,
) {
    // 居中留白卡片为首次进入、零命中与长查询首屏提供稳定视觉重心。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchEmptyStateIndicator(isLoading = isLoading, icon = icon, iconTint = iconTint)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(modifier = Modifier.height(2.dp))
                PageOutlinedButton(
                    onClick = action.onClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** 图标与进度指示器共用空态卡片中的同一占位。 */
@Composable
private fun SearchEmptyStateIndicator(isLoading: Boolean, icon: ImageVector?, iconTint: Color) {
    // 顶部展示主图标气泡或加载指示器。
    if (isLoading) {
        AppSurface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else if (icon != null) {
        RpIconBubble(
            icon = icon,
            size = 52.dp,
            iconSize = 26.dp,
            shape = RoundedCornerShape(16.dp),
            containerColor = iconTint.copy(alpha = 0.14f),
            contentColor = iconTint,
        )
    }
}

/**
 * 底部翻页占位条；加载更多与加载中保持一致高度，防止翻页时底部视图跳动。
 *
 * @param isLoading 是否正在加载下一页。
 * @param enabled 当前游标是否仍可用于继续分页。
 * @param onLoadMore 点击加载更多回调。
 */
@Composable
internal fun SearchPaginationFooter(
    isLoading: Boolean,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    // 复用同一圆角容器与最小高度，在触发分页和拉取过程中保持底部布局稳定。
    AppSurface(
        onClick = onLoadMore.takeIf { enabled && !isLoading },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.content_search_more),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 按内容类型返回对应图标，与首页最近内容列表保持一致。 */
internal fun kindIcon(kind: Int): ImageVector = when (kind) {
    1 -> Icons.Rounded.ChatBubble
    2 -> Icons.Rounded.Groups
    3 -> Icons.Rounded.AutoStories
    else -> Icons.Rounded.ChatBubble
}

/** 按内容类型编码返回对应的本地化标签资源。 */
internal fun kindLabel(kind: Int): Int = when (kind) {
    1 -> R.string.home_session_filter_single
    2 -> R.string.home_session_filter_group
    3 -> R.string.home_content_filter_story
    else -> R.string.home_session_filter_all
}

/** 按内容检索范围返回对应的本地化标签资源。 */
internal fun scopeLabel(scope: SearchScope): Int = when (scope) {
    SearchScope.All -> R.string.content_search_all
    SearchScope.Metadata -> R.string.content_search_metadata
    SearchScope.Body -> R.string.content_search_body
}

/** 与首页卡片保持相同的强调色种子计算规则，使同一会话或故事在两页呈现相同色标。 */
private fun hitAccentKey(hit: SearchHit): String {
    if (hit.kind == 1) {
        val firstCharacter = hit.names.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        if (!firstCharacter.isNullOrEmpty()) return firstCharacter
    }
    return hit.title.ifBlank { hit.key }
}

/** 仅格式化真实有效时间戳，过滤测试夹具或初始化占位的小数值时间戳。 */
private fun formatHitTimestamp(latestTime: Long): String? =
    if (latestTime >= MIN_VALID_TIMESTAMP_MS) {
        latestTime.formatTimestamp("MM-dd HH:mm")
    } else {
        null
    }

/** 构建带关键词高亮样式的摘录富文本，不改动原始字符序列以保证匹配定位一致。 */
private fun buildHighlightedSnippet(
    snippet: String,
    matchStart: Int,
    matchEnd: Int,
    background: Color,
    foreground: Color,
): AnnotatedString = buildAnnotatedString {
    append(snippet)
    // 索引或原文变更可能使范围失效，此时仍展示未高亮的完整摘录。
    if (matchStart >= 0 && matchEnd in (matchStart + 1)..length) {
        addStyle(
            style = SpanStyle(
                background = background,
                color = foreground,
                fontWeight = FontWeight.Bold,
            ),
            start = matchStart,
            end = matchEnd,
        )
    }
}

private const val MIN_VALID_TIMESTAMP_MS = 946_684_800_000L
