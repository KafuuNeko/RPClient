package me.kafuuneko.rpclient.feature.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun MainLayout(
    uiState: MainUiState,
    emit: MainUiIntent.() -> Unit
) {
    BackHandler { MainUiIntent.Back.emit() }
    when (uiState) {
        MainUiState.None, MainUiState.Finished -> Unit
        is MainUiState.Normal -> MainNormal(uiState, emit)
    }
}

@Composable
private fun MainNormal(
    uiState: MainUiState.Normal,
    emit: MainUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = { MainBottomBar(uiState.selectedPage, emit) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialThemeLike.background())
        ) {
            when (uiState.selectedPage) {
                MainPage.Home -> HomePage(uiState.homeState, emit)
                MainPage.Settings -> SettingsPage(uiState.settingsState, emit)
            }
        }
    }
}

@Composable
private fun MainBottomBar(
    selectedPage: MainPage,
    emit: MainUiIntent.() -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        NavigationBarItem(
            selected = selectedPage == MainPage.Home,
            onClick = { MainUiIntent.SelectPage(MainPage.Home).emit() },
            icon = { Icon(Icons.Rounded.Home, contentDescription = "首页") },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = selectedPage == MainPage.Settings,
            onClick = { MainUiIntent.SelectPage(MainPage.Settings).emit() },
            icon = { Icon(Icons.Rounded.Settings, contentDescription = "设置") },
            label = { Text("设置") }
        )
    }
}

@Composable
private fun HomePage(
    state: MainHomeState,
    emit: MainUiIntent.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HomeHero(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeEntryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Person,
                    title = "角色",
                    subtitle = "${state.totalCharacters} 张角色卡",
                    onClick = { MainUiIntent.OpenCharacterManager.emit() }
                )
                HomeEntryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Book,
                    title = "世界书",
                    subtitle = "${state.totalWorldBooks} 个 lorebook",
                    onClick = { MainUiIntent.OpenWorldBookManager.emit() }
                )
            }
        }
        item { RpSectionHeader(title = "最近会话", action = "管理角色") { MainUiIntent.OpenCharacterManager.emit() } }
        items(state.recentSessions) { session ->
            SessionCard(
                session = session,
                onClick = { MainUiIntent.OpenChat(session.id).emit() }
            )
        }
    }
}

@Composable
private fun HomeHero(state: MainHomeState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "RPClient",
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = state.greeting,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpAvatar(
                    text = state.activeCharacter.avatarText,
                    color = Color(state.activeCharacter.accentColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = state.activeCharacter.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = state.activeCharacter.subtitle,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeEntryCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    RpInfoCard(
        modifier = modifier.clickable { onClick() },
        icon = icon,
        title = title,
        subtitle = subtitle
    )
}

@Composable
private fun SessionCard(
    session: ChatSessionUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.ChatBubble)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = session.preview,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RpMetaRow(
                items = listOf(
                    session.characterName,
                    "${session.messageCount} 条消息",
                    "${session.branchCount} 分支",
                    session.updatedAt
                )
            )
        }
    }
}

@Composable
private fun SettingsPage(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { RpPageTitle(title = "设置", subtitle = "Provider、生成参数、本地优先与备份") }
        item { RpSectionHeader(title = "模型 Provider", action = "管理") { MainUiIntent.OpenProviderManager.emit() } }
        if (state.providers.isEmpty()) {
            item { EmptyProviderCard { MainUiIntent.OpenProviderManager.emit() } }
        } else {
            items(state.providers) { provider ->
                ProviderCard(
                    provider = provider,
                    selected = provider.id.toString() == state.selectedProviderId,
                    onClick = { MainUiIntent.SelectProvider(provider.id.toString()).emit() }
                )
            }
            item { ParameterPanel(state) }
        }
        item { PrivacyPanel(state) }
    }
}

@Composable
private fun EmptyProviderCard(
    onClick: () -> Unit
) {
    RpInfoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        icon = Icons.Rounded.Storage,
        title = "没有已启用模型",
        subtitle = "进入模型管理启用或新建一个 Provider"
    )
}

@Composable
private fun ProviderCard(
    provider: LLMProvider,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(2.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(if (provider.isEnabled) Icons.Rounded.Key else Icons.Rounded.Storage)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(provider.model, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Text(
                    provider.baseUrl,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RpTagRow(listOf(provider.statusText()), maxCount = 1)
        }
    }
}

private fun LLMProvider.statusText(): String {
    return when {
        !isEnabled -> "未启用"
        apiKey.isBlank() -> "待配置"
        else -> "可用"
    }
}

@Composable
private fun ParameterPanel(state: MainSettingsState) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = "生成参数", action = "预设")
            ParameterRow("Temperature", state.temperature.toString())
            ParameterRow("Max Tokens", state.maxTokens.toString())
            ParameterRow("Context", "${state.contextTokens} tokens")
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        FilterChip(selected = true, onClick = {}, label = { Text(value) })
    }
}

@Composable
private fun PrivacyPanel(state: MainSettingsState) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = "隐私与备份", action = "导出")
            SettingSwitchRow(Icons.Rounded.Shield, "本地优先", "聊天、角色与世界书默认保存在设备内", state.localFirstEnabled)
            SettingSwitchRow(Icons.Rounded.Refresh, "流式回复", "实时显示模型输出，支持停止生成", state.streamEnabled)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
        }
        Switch(checked = checked, onCheckedChange = {})
    }
}

private object MaterialThemeLike {
    @Composable
    fun background() = androidx.compose.material3.MaterialTheme.colorScheme.background
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainLayoutPreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(
            uiState = MainUiState.Normal(
                homeState = MainHomeState(
                    greeting = "继续一段有角色记忆、有世界书约束的剧情。",
                    activeCharacter = RpCharacterUiModel("lyra", "Lyra", "雾港档案管理员", "", "L", emptyList(), 12, "刚刚", 0xFF315EFD),
                    recentSessions = emptyList(),
                    totalCharacters = 24,
                    totalWorldBooks = 7
                ),
                settingsState = MainSettingsState("", emptyList(), 0.8f, 1200, 8192, true, true)
            ),
            emit = {}
        )
    }
}
