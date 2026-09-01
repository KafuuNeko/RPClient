package me.kafuuneko.rpclient.feature.about.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

/** 关于页 Compose 入口，负责整体脚手架与页面外部事件分发。 */
@Composable
fun AboutLayout(
    uiState: AboutUiState,
    onBack: () -> Unit,
    onCopyDeveloperEmail: () -> Unit,
    onOpenRepository: () -> Unit,
    onRateApp: () -> Unit,
    onOpenFeedback: () -> Unit
) {
    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.about),
                onBack = onBack
            )
        },
    ) { paddingValues ->
        AboutViewContent(
            uiState = uiState,
            onCopyDeveloperEmail = onCopyDeveloperEmail,
            onOpenRepository = onOpenRepository,
            onRateApp = onRateApp,
            onOpenFeedback = onOpenFeedback,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/** 关于页正文内容，组织品牌头部、特色评分卡片、社区互动组、支持信息组与致谢页脚。 */
@Composable
private fun AboutViewContent(
    uiState: AboutUiState,
    onCopyDeveloperEmail: () -> Unit,
    onOpenRepository: () -> Unit,
    onRateApp: () -> Unit,
    onOpenFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .draggableScrollIndicator(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // - 品牌核心展示头部（包含双层圆角容器图标、应用名、版本胶囊与标语）
        AboutHeroHeader(appVersionName = uiState.appVersionName)

        // - 精选特色好评卡片（突出星级评分与 Google Play 引导）
        AboutRatingCard(onRateApp = onRateApp)

        // - 社区与反馈分组（涵盖 Issue 追踪与 GitHub 仓库）
        AboutCommunitySection(
            repoName = uiState.githubRepoName,
            onOpenFeedback = onOpenFeedback,
            onOpenRepository = onOpenRepository
        )

        // - 关于与支持分组（涵盖开发者联系与开源许可证声明）
        AboutSupportSection(
            developerEmail = uiState.developerEmail,
            onCopyDeveloperEmail = onCopyDeveloperEmail,
            onOpenRepository = onOpenRepository
        )

        // - 底部致谢与版权声明
        AboutArtisanFooter()
    }
}

/** 品牌核心展示头部：双层柔光图标、产品标题、带状态指示圆点的版本胶囊与愿景副标题。 */
@Composable
private fun AboutHeroHeader(
    appVersionName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // - 双层质感圆角容器，包裹应用 Logo 形成微浮雕视觉深度
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        }

        // - 应用主标题
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // - 状态点与版本号胶囊
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "v$appVersionName",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // - 产品核心标语（不强调技术限定词，传达创作沉浸感）
        Text(
            text = stringResource(R.string.about_app_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/** 现代特色好评卡片：金色星级徽标、清晰说明与一键跳转按钮。 */
@Composable
private fun AboutRatingCard(
    onRateApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onRateApp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // - 星级评价行与 Google Play 徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.rate_on_google_play),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // - 标题与引导文案
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.rate_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.rate_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }

            // - 行动按钮
            Button(
                onClick = onRateApp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rate_card_action),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 社区与反馈分组卡片：聚合 GitHub Issues 反馈与代码开源主页。 */
@Composable
private fun AboutCommunitySection(
    repoName: String,
    onOpenFeedback: () -> Unit,
    onOpenRepository: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.section_community),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        RpSettingsGroup {
            RpSettingsTile(
                icon = Icons.Rounded.BugReport,
                iconColor = MaterialTheme.colorScheme.secondary,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                title = stringResource(R.string.feedback_label),
                subtitle = stringResource(R.string.feedback_desc),
                onClick = onOpenFeedback,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_github_issues),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
            RpSettingsDivider()
            RpSettingsTile(
                icon = Icons.Rounded.Code,
                iconColor = MaterialTheme.colorScheme.tertiary,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                title = stringResource(R.string.github_repo_label),
                subtitle = repoName,
                onClick = onOpenRepository,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.visit_repo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

/** 关于与支持分组卡片：聚合开发者交流邮箱与开源 MIT 许可证说明。 */
@Composable
private fun AboutSupportSection(
    developerEmail: String,
    onCopyDeveloperEmail: () -> Unit,
    onOpenRepository: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.section_about),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        RpSettingsGroup {
            RpSettingsTile(
                icon = Icons.Rounded.Email,
                iconColor = MaterialTheme.colorScheme.primary,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                title = stringResource(R.string.developer_contact),
                subtitle = stringResource(R.string.copy_email_hint),
                onClick = onCopyDeveloperEmail,
                trailing = {
                    Text(
                        text = developerEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            RpSettingsDivider()
            RpSettingsTile(
                icon = Icons.Rounded.Info,
                iconColor = MaterialTheme.colorScheme.secondary,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                title = stringResource(R.string.open_source_license),
                subtitle = stringResource(R.string.open_source_license_desc),
                onClick = onOpenRepository,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "MIT",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

/** 底部匠心制作与开源版权声明组件。 */
@Composable
private fun AboutArtisanFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.about_crafted_with_love),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AboutLayoutPreview() {
    AppTheme(dynamicColor = false) {
        AboutLayout(
            uiState = AboutUiState(
                appVersionName = "2026.2.2",
                githubRepoUrl = "https://github.com/KafuuNeko/RPClient",
                githubRepoName = "KafuuNeko/RPClient",
                developerEmail = "developer@example.com",
                githubIssuesUrl = "https://github.com/KafuuNeko/RPClient/issues"
            ),
            onBack = {},
            onCopyDeveloperEmail = {},
            onOpenRepository = {},
            onRateApp = {},
            onOpenFeedback = {}
        )
    }
}
