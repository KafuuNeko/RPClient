package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 隐私与本地数据说明弹窗，供首次启动轻提示及关于页随时回看复用。
 *
 * 界面结构：
 * - 顶部使用强调徽标说明本地优先定位；
 * - 正文以分组卡片展示本地存储、第三方模型直连与本地模型离线三项核心事实；
 * - 附带可点击的 Discord 社区与 GitHub 开源仓库入口，并在底部提供单按钮轻确认。
 *
 * @param onDismissRequest 关闭弹窗回调，同时绑定主确认按钮。
 * @param onOpenDiscord 点击 Discord 社区入口的回调。
 * @param onOpenRepository 点击 GitHub 仓库入口的回调。
 * @param modifier 弹窗内容容器修饰符。
 * @param isFirstLaunch 是否处于首次启动提示场景；为 true 时禁用外侧误触关闭并展示回看提示。
 * @param discordUrl 用于展示简洁链接文本的 Discord 邀请地址。
 * @param repositoryUrl 用于展示简洁链接文本的仓库地址。
 */
@Composable
fun PrivacyNoticeDialog(
    onDismissRequest: () -> Unit,
    onOpenDiscord: () -> Unit,
    onOpenRepository: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstLaunch: Boolean = true,
    discordUrl: String = AppModel.DISCORD_INVITE_URL,
    repositoryUrl: String = AppModel.GITHUB_REPO,
) {
    // 首次展示禁止点击外侧误关闭，保留返回键与主按钮轻确认闭环。
    val properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = !isFirstLaunch,
        usePlatformDefaultWidth = true,
    )
    val discordDisplay = formatDisplayUrl(discordUrl)
    val repositoryDisplay = formatDisplayUrl(repositoryUrl)
    // 复用公共弹窗脚手架，确保圆角、半透明外观适配与小屏可滚动区域一致。
    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.privacy_notice_title),
        modifier = modifier,
        badgeIcon = Icons.Rounded.VerifiedUser,
        badgeTone = DialogBadgeTone.Primary,
        subtitle = stringResource(R.string.privacy_notice_subtitle),
        confirmText = stringResource(R.string.privacy_notice_confirm),
        dismissText = null,
        onConfirm = onDismissRequest,
        onDismiss = null,
        properties = properties,
        scrollableContent = true,
    ) {
        // 纵向组织核心隐私要点、社区与开源链接及关于页回看提示。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrivacyHighlightsCard()
            PrivacyLinksCard(
                discordDisplay = discordDisplay,
                repositoryDisplay = repositoryDisplay,
                onOpenDiscord = onOpenDiscord,
                onOpenRepository = onOpenRepository,
            )
            if (isFirstLaunch) {
                Text(
                    text = stringResource(R.string.privacy_notice_revisit_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 汇总纯本地存储、第三方模型直连与本地模型离线三项核心说明。 */
@Composable
private fun PrivacyHighlightsCard() {
    // 使用柔和表面底色和极细描边承托要点，保持弹窗内视觉层次清晰。
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = dialogControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            // 无云端账号与本地数据删除说明。
            PrivacyPointRow(
                icon = Icons.Rounded.PhonelinkLock,
                accent = AccentEmeraldColor,
                title = stringResource(R.string.privacy_notice_local_title),
                description = stringResource(R.string.privacy_notice_local_desc),
            )
            PrivacyCardDivider()
            // 第三方模型直连与对应隐私政策说明。
            PrivacyPointRow(
                icon = Icons.Rounded.Hub,
                accent = AccentSkyColor,
                title = stringResource(R.string.privacy_notice_provider_title),
                description = stringResource(R.string.privacy_notice_provider_desc),
            )
            PrivacyCardDivider()
            // 本地或局域网后端零数据出网说明。
            PrivacyPointRow(
                icon = Icons.Rounded.Memory,
                accent = AccentVioletColor,
                title = stringResource(R.string.privacy_notice_local_model_title),
                description = stringResource(R.string.privacy_notice_local_model_desc),
            )
        }
    }
}

/** 单条隐私说明项，图标置顶对齐以适配多语言长文案与大字体缩放。 */
@Composable
private fun PrivacyPointRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
) {
    // 图标与正文顶部对齐，避免多行译文撑高行高后图标视觉悬空。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PrivacyIconBubble(
            icon = icon,
            accent = accent,
        )
        // 标题与正文纵向紧凑排列，保持高可读行距。
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

/** 社区交流与开源仓库外链卡片，统一提供外跳视觉反馈。 */
@Composable
private fun PrivacyLinksCard(
    discordDisplay: String,
    repositoryDisplay: String,
    onOpenDiscord: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    // 用主色微描边与渐变底板区分可交互外链区域与静态说明卡片。
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = dialogControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            // Discord 社区入口行。
            PrivacyLinkRow(
                icon = Icons.Rounded.Forum,
                accent = AccentIndigoColor,
                title = stringResource(R.string.about_discord_community),
                subtitle = discordDisplay,
                onClick = onOpenDiscord,
            )
            PrivacyCardDivider()
            // GitHub 开源仓库入口行。
            PrivacyLinkRow(
                icon = Icons.Rounded.Code,
                accent = AccentVioletColor,
                title = stringResource(R.string.github_repo_label),
                subtitle = repositoryDisplay,
                onClick = onOpenRepository,
            )
        }
    }
}

/** 整行可点击的外链条目，避免小屏下按钮与长地址互相挤压。 */
@Composable
private fun PrivacyLinkRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    // 整行作为唯一点击与无障碍交互区域，扩大触控命中范围。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrivacyIconBubble(
            icon = icon,
            accent = accent,
        )
        // 主标题与精简地址均限制单行截断，防止窄屏换行破坏卡片节奏。
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 尾部外跳图标提示将离开当前应用打开外部地址。
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 卡片内部统一分割线，与条目水平内边距对齐。 */
@Composable
private fun PrivacyCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

/** 柔和彩色图标底座，保持图标实色对比度并适配半透明弹窗外观。 */
@Composable
private fun PrivacyIconBubble(
    icon: ImageVector,
    accent: Color,
) {
    // 仅底色响应半透明控件缩放，图标保持实色以确保可读性。
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = dialogControlColor(accent.copy(alpha = 0.13f)),
        border = BorderStroke(
            width = 0.5.dp,
            color = accent.copy(alpha = 0.26f),
        ),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 移除链接协议前缀与尾部斜杠，便于在窄屏卡片中完整辨识域名和路径。 */
private fun formatDisplayUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").trimEnd('/')

@Preview(name = "PrivacyNoticeDialog - Light", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PrivacyNoticeDialogLightPreview() {
    AppTheme(dynamicColor = false) {
        PrivacyNoticeDialog(
            onDismissRequest = {},
            onOpenDiscord = {},
            onOpenRepository = {},
        )
    }
}

@Preview(name = "PrivacyNoticeDialog - Dark", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun PrivacyNoticeDialogDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        PrivacyNoticeDialog(
            onDismissRequest = {},
            onOpenDiscord = {},
            onOpenRepository = {},
        )
    }
}

@Preview(
    name = "PrivacyNoticeDialog - Small / Large Text",
    widthDp = 320,
    heightDp = 640,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun PrivacyNoticeDialogSmallPreview() {
    AppTheme(dynamicColor = false) {
        PrivacyNoticeDialog(
            onDismissRequest = {},
            onOpenDiscord = {},
            onOpenRepository = {},
        )
    }
}
