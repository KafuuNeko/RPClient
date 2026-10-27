package me.kafuuneko.rpclient.feature.backuprestore.ui

import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupDialogState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupOperation
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiIntent
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageButton
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedButton
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel

/**
 * 备份与恢复页面布局入口。
 *
 * 核心架构与职责：
 * - 遵循 MVI 状态渲染与意图分发规范，不持有 ViewModel 与数据层引用。
 * - 响应应用全局通用背景与壁纸透明度/模糊配置，按钮与卡片统一支持表面半透明及模糊渲染。
 * - 显式提示试验性功能风险与未加密数据安全约束，保障用户知情权。
 *
 * @param state 页面状态，包含当前运行操作、进度状态与弹窗。
 * @param emit 意图发射回调函数。
 */
@Composable
fun BackupRestoreLayout(
    state: BackupRestoreUiState,
    emit: BackupRestoreUiIntent.() -> Unit,
) {
    BackHandler(onBack = { BackupRestoreUiIntent.Back.emit() })
    when (state) {
        is BackupRestoreUiState.Normal -> {
            BackupContent(state, emit)
            DialogSwitch(state, emit)
        }

        is BackupRestoreUiState.Finished -> {
            BackupContent(state = state.previous, emit = { })
        }
    }
}

/**
 * 备份页主体滚动区域，包含顶栏、标题、试验性风险警示、安全说明、操作面板与任务状态。
 *
 * 布局规则与时序：
 * - 采用 AppPageScaffold 统一管理页面画布与透明度槽位。
 * - 使用 RpLazyColumn 滚动列表，适配小屏与系统导航栏安全边距。
 * - 各功能区块通过卡片封装，避免视觉杂乱。
 *
 * @param state 正常状态数据。
 * @param emit 意图发射回调。
 */
@Composable
private fun BackupContent(
    state: BackupRestoreUiState.Normal,
    emit: BackupRestoreUiIntent.() -> Unit,
) {
    // 页面骨架负责分配顶部栏与画布背景色，支持主题透明壁纸穿透
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.backup_restore_title),
                onBack = { BackupRestoreUiIntent.Back.emit() },
            )
        },
        content = { paddingValues ->
            // 惰性列表承载页面内容，自动挂载统一侧边滚动指示器并适配边距
            RpLazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 14.dp,
                    end = 18.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 顶部页面标题与功能概要说明
                item {
                    RpPageTitle(
                        title = stringResource(R.string.backup_restore_title),
                        subtitle = stringResource(R.string.backup_restore_description),
                    )
                }
                // 试验性功能风险警示卡片，提醒用户尚未验证可靠性与数据覆盖风险
                item {
                    NoticeCard(
                        icon = Icons.Rounded.Science,
                        iconColor = AccentAmberColor,
                        iconContainerColor = AccentAmberColor.copy(alpha = 0.15f),
                        containerColor = MaterialTheme.colorScheme.surface,
                        borderColor = AccentAmberColor.copy(alpha = 0.35f),
                        title = stringResource(R.string.backup_experimental_title),
                        message = stringResource(R.string.backup_experimental_desc),
                    )
                }
                // 安全与隐私警示卡片，提示导出归档未加密特性与 API Key 泄露风险
                item {
                    NoticeCard(
                        icon = Icons.Rounded.Security,
                        iconColor = MaterialTheme.colorScheme.error,
                        iconContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        containerColor = MaterialTheme.colorScheme.surface,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        title = stringResource(R.string.backup_security_title),
                        message = stringResource(R.string.backup_restore_security_warning),
                    )
                }
                // 任务执行进度指示与操作状态提示卡片
                if (state.busy || state.status != null) {
                    item {
                        StatusCard(state)
                    }
                }
                // 完整数据导出操作卡片
                item {
                    ExportSection(state.busy, emit)
                }
                // 完整数据恢复操作卡片
                item {
                    StatusCard(state, emit)
                }
            }
        },
    )
}

/**
 * 警示提示信息卡片，图标置顶对齐，适用于长文案风险与安全警示。
 *
 * @param icon 提示图标。
 * @param iconColor 图标前景色。
 * @param iconContainerColor 图标背景浅底色。
 * @param containerColor 卡片容器背景色。
 * @param borderColor 卡片外边框色。
 * @param title 提示标题。
 * @param message 提示正文详细说明。
 * @param modifier 外部修饰符。
 */
@Composable
private fun NoticeCard(
    icon: ImageVector,
    iconColor: Color,
    iconContainerColor: Color,
    containerColor: Color,
    borderColor: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    // 外层卡片继承应用透明度与模糊修饰
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(width = 0.5.dp, color = borderColor),
    ) {
        // 横向排列图标与多行文字，保持图标顶部对齐
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 图标微光气泡底板
            RpIconBubble(
                icon = icon,
                contentColor = iconColor,
                containerColor = iconContainerColor,
            )
            // 标题与长文案垂直排列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/**
 * 数据导出面板，组织导出说明与触发按钮。
 *
 * @param busy 当前后台是否有任务正在执行。
 * @param emit 意图发射回调。
 */
@Composable
private fun ExportSection(
    busy: Boolean,
    emit: BackupRestoreUiIntent.() -> Unit,
) {
    // 使用统一面板包裹导出内容与操作控件
    RpPanel {
        // 头部信息行展示模块图标、标题与简要说明
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.FileUpload,
                contentColor = AccentSkyColor,
                containerColor = AccentSkyColor.copy(alpha = 0.14f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_export_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.backup_export_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
        // 遵循项目规范的 PageButton，严格适配透明度与模糊效果
        PageButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            onClick = { BackupRestoreUiIntent.ExportClick.emit() },
        ) {
            Icon(
                imageVector = Icons.Rounded.FileUpload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.backup_export_action))
        }
    }
}

/**
 * 数据恢复面板，组织恢复说明与触发按钮。
 *
 * @param busy 当前后台是否有任务正在执行。
 * @param emit 意图发射回调。
 */
@Composable
private fun RestoreSection(
    busy: Boolean,
    emit: BackupRestoreUiIntent.() -> Unit,
) {
    // 使用统一面板包裹恢复内容与操作控件
    RpPanel {
        // 头部信息行展示警告色图标与恢复说明
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.FileDownload,
                contentColor = MaterialTheme.colorScheme.error,
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.backup_restore_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
        // 遵循项目规范的 PageOutlinedButton，采用危险色调并适配透明度与模糊效果
        PageOutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = { BackupRestoreUiIntent.RestoreClick.emit() },
        ) {
            Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.backup_restore_action))
        }
    }
}

/**
 * 实时状态指示面板，展示任务进度与操作结果。
 *
 * @param state 当前页面状态。
 * @param emit 意图发射回调，用于恢复完成后的手动重启。
 */
@Composable
private fun StatusCard(
    state: BackupRestoreUiState.Normal,
    emit: BackupRestoreUiIntent.() -> Unit
) {
    // 外层卡片提供柔和背景与边框
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 状态卡片小标题
            Text(
                text = stringResource(R.string.backup_status_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 忙碌状态展示进度指示条
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            // 状态文字与动态适配的语义图标展示
            state.status?.let { statusRes ->
                val isFailure = statusRes == R.string.backup_failure_detail ||
                    statusRes == R.string.backup_operation_failed
                val isSuccess = statusRes == R.string.backup_export_success ||
                    statusRes == R.string.backup_restore_success ||
                    statusRes == R.string.backup_restore_done
                val statusIcon = when {
                    isFailure -> Icons.Rounded.ErrorOutline
                    isSuccess -> Icons.Rounded.Check
                    else -> Icons.Rounded.Info
                }
                val iconColor = when {
                    isFailure -> MaterialTheme.colorScheme.error
                    isSuccess -> AccentEmeraldColor
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(statusRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // 阶段日志、警告与完整异常链：默认折叠，可展开阅读和复制
            state.detail?.takeIf { it.isNotBlank() }?.let { DetailsPanel(it) }
            // 恢复结束后由用户确认重启，避免警告随进程消失
            if (state.operation == BackupOperation.Restarting) {
                PageButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = { BackupRestoreUiIntent.RestartClick.emit() }
                ) {
                    Text(stringResource(R.string.backup_restart_now))
                }
            }
        }
    }
}

/**
 * 可展开、可选择、可一键复制的技术详情区块。
 *
 * @param detail 阶段日志、警告和完整异常链文本。
 */
@Composable
private fun DetailsPanel(detail: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.backup_details_copied)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.backup_details_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            // 内部区块限制高度并独立滚动，长异常链不会撑开整页
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            PageOutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("backup-report", detail))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_details_copy))
            }
        }
    }
}

/**
 * 业务弹窗分发入口，覆盖恢复复用全局危险操作弹窗。
 *
 * @param state 当前页面状态。
 * @param emit 意图发射回调。
 */
@Composable
private fun DialogSwitch(
    state: BackupRestoreUiState.Normal,
    emit: BackupRestoreUiIntent.() -> Unit,
) {
    // 弹窗状态分发，取消时重置临时 URI
    when (state.dialog) {
        BackupDialogState.None -> Unit
        BackupDialogState.ConfirmRestore -> AppDangerDialog(
            onDismissRequest = { BackupRestoreUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.backup_restore_confirm_title),
            message = stringResource(R.string.backup_restore_confirm_message),
            confirmText = stringResource(R.string.backup_restore_action),
            confirmEnabled = !state.busy,
            onConfirm = { BackupRestoreUiIntent.ConfirmRestore.emit() },
        )
    }
}

/**
 * 备份与恢复页面预览环境，强制关闭动态颜色以验证默认主题视觉。
 */
@Preview
@Composable
private fun BackupRestorePreview() {
    AppTheme(dynamicColor = false) {
        BackupRestoreLayout(state = BackupRestoreUiState.Normal(), emit = { })
    }
}
