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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.ui.theme.AppTheme
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
            icon = { Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.home)) },
            label = { Text(stringResource(R.string.home)) }
        )
        NavigationBarItem(
            selected = selectedPage == MainPage.Settings,
            onClick = { MainUiIntent.SelectPage(MainPage.Settings).emit() },
            icon = {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            },
            label = { Text(stringResource(R.string.settings)) }
        )
    }
}

@Composable
private fun HomePage(
    state: MainHomeState,
    emit: MainUiIntent.() -> Unit
) {
    val collapsedCharacterIds = remember { mutableStateListOf<String>() }
    val sessionGroups = state.recentSessions.groupBy { it.characterId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeEntryCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AddComment,
                title = stringResource(R.string.new_session),
                subtitle = stringResource(R.string.new_session_desc),
                onClick = { MainUiIntent.OpenCreateChat.emit() }
            )
        }
        item {
            HomeEntryCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.character),
                subtitle = stringResource(R.string.character_cards_count, state.totalCharacters),
                onClick = { MainUiIntent.OpenCharacterManager.emit() }
            )
        }
        item {
            HomeEntryCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Book,
                title = stringResource(R.string.world_book),
                subtitle = stringResource(R.string.lorebook_count, state.totalWorldBooks),
                onClick = { MainUiIntent.OpenWorldBookManager.emit() }
            )
        }
        item {
            RpSectionHeader(
                title = stringResource(R.string.recent_chats),
                action = stringResource(R.string.new_session)
            ) { MainUiIntent.OpenCreateChat.emit() }
        }
        if (state.recentSessions.isEmpty()) {
            item {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.ChatBubble,
                    title = stringResource(R.string.no_recent_chats),
                    subtitle = stringResource(R.string.no_recent_chats_desc)
                )
            }
        }
        sessionGroups.forEach { (characterId, sessions) ->
            val characterName = sessions.firstOrNull()?.characterName.orEmpty()
            val expanded = characterId !in collapsedCharacterIds
            item(key = "character-$characterId") {
                SessionCharacterHeader(
                    characterName = characterName,
                    sessionCount = sessions.size,
                    expanded = expanded,
                    onClick = {
                        if (expanded) {
                            collapsedCharacterIds.add(characterId)
                        } else {
                            collapsedCharacterIds.remove(characterId)
                        }
                    }
                )
            }
            if (expanded) {
                items(
                    items = sessions,
                    key = { session -> "session-${session.id}" }
                ) { session ->
                    SessionCard(
                        session = session,
                        onClick = { MainUiIntent.OpenChat(session.id).emit() }
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
private fun SessionCharacterHeader(
    characterName: String,
    sessionCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = characterName,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.session_group_count, sessionCount),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(
                if (expanded) R.string.collapse_session_group else R.string.expand_session_group
            ),
            modifier = Modifier.size(24.dp),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun SessionCard(
    session: MainChatSessionItem,
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
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.62f
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RpMetaRow(
                items = listOf(
                    session.characterName,
                    stringResource(R.string.message_count, session.messageCount),
                    stringResource(R.string.branch_count, session.branchCount),
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
        item {
            RpPageTitle(
                title = stringResource(R.string.setting_page_title),
                subtitle = stringResource(R.string.setting_subtitle)
            )
        }
        item { UserIdentityPanel(state, emit) }
        item {
            RpSectionHeader(
                title = stringResource(R.string.model_provider),
                action = stringResource(R.string.manage)
            ) { MainUiIntent.OpenProviderManager.emit() }
        }
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
        item { PromptBehaviorPanel(state, emit) }
        item { PromptPresetEntryCard { MainUiIntent.OpenPromptPreset.emit() } }
        item { SummaryPanel(state, emit) }
        item { PrivacyPanel(state, emit) }
        item { DebugPanel(state, emit) }
    }
}

@Composable
private fun UserIdentityPanel(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.user_identity))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.Person)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.userName,
                        onValueChange = { MainUiIntent.ChangeUserName(it).emit() },
                        label = { Text(stringResource(R.string.user_display_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.userDescription,
                        onValueChange = { MainUiIntent.ChangeUserDescription(it).emit() },
                        label = { Text(stringResource(R.string.user_persona_description)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptBehaviorPanel(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    // 请求行为是全局 prompt 管线设置，放在首页设置页而不是提示词模板编辑页。
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.prompt_behavior_section))
            Text(
                text = stringResource(R.string.prompt_post_processing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
            PromptPostProcessingMode.entries.forEach { mode ->
                PromptPostProcessingModeRow(
                    mode = mode,
                    selected = mode == state.promptPostProcessingMode,
                    onClick = { MainUiIntent.SelectPostProcessingMode(mode).emit() }
                )
            }
            SettingSwitchRow(
                icon = Icons.Rounded.Compress,
                title = stringResource(R.string.prompt_include_think_context_title),
                subtitle = stringResource(R.string.prompt_include_think_context_desc),
                checked = state.includeThinkInContext,
                onCheckedChange = { MainUiIntent.ToggleIncludeThinkInContext(it).emit() }
            )
        }
    }
}

@Composable
private fun PromptPostProcessingModeRow(
    mode: PromptPostProcessingMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 使用整行可点击卡片，方便在手机上切换五个互斥模式。
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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
        title = stringResource(R.string.no_enabled_model),
        subtitle = stringResource(R.string.go_to_model_manager)
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
        border = if (selected) BorderStroke(
            2.dp,
            androidx.compose.material3.MaterialTheme.colorScheme.primary
        ) else null,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(if (provider.isEnabled) Icons.Rounded.Key else Icons.Rounded.Storage)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    provider.name,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                )
                Text(
                    provider.model,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Text(
                    provider.baseUrl,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.48f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RpTagRow(
                tags = listOf(
                    when {
                        !provider.isEnabled -> stringResource(R.string.not_enabled)
                        provider.apiKey.isBlank() -> stringResource(R.string.pending_config)
                        else -> stringResource(R.string.available)
                    }
                ), maxCount = 1
            )
        }
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
            RpSectionHeader(
                title = stringResource(R.string.generation_parameters),
                action = stringResource(R.string.preset)
            )
            ParameterRow(stringResource(R.string.temperature), state.temperature.toString())
            ParameterRow(stringResource(R.string.top_p), state.topP.toString())
            ParameterRow(stringResource(R.string.max_tokens), state.maxTokens.toString())
            ParameterRow(
                stringResource(R.string.context),
                "${state.contextTokens} ${stringResource(R.string.tokens)}"
            )
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        FilterChip(selected = true, onClick = {}, label = { Text(value) })
    }
}

@Composable
private fun PromptPresetEntryCard(onClick: () -> Unit) {
    RpInfoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.prompt_preset_title),
        subtitle = stringResource(R.string.prompt_preset_entry_subtitle)
    )
}

@Composable
private fun SummaryPanel(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(
                title = stringResource(R.string.summary_memory),
                action = if (state.autoSummaryEnabled) stringResource(R.string.auto) else stringResource(R.string.manual)
            )
            SettingSwitchRow(
                Icons.Rounded.AutoAwesome,
                stringResource(R.string.auto_summarize),
                stringResource(R.string.auto_summarize_desc),
                state.autoSummaryEnabled,
                onCheckedChange = { MainUiIntent.ToggleAutoSummaryEnabled(it).emit() }
            )
            NumberSettingRow(
                title = stringResource(R.string.summary_update_every_messages),
                value = state.summaryTriggerMessageCount.toString(),
                onValueChange = { MainUiIntent.ChangeSummaryTriggerMessageCount(it).emit() }
            )
            NumberSettingRow(
                title = stringResource(R.string.summary_target_words),
                value = state.summaryWordsLimit.toString(),
                onValueChange = { MainUiIntent.ChangeSummaryWordsLimit(it).emit() }
            )
            NumberSettingRow(
                title = stringResource(R.string.summary_max_messages_per_request),
                value = state.summaryMaxMessagesPerRequest.toString(),
                helper = stringResource(R.string.summary_max_messages_helper),
                onValueChange = { MainUiIntent.ChangeSummaryMaxMessagesPerRequest(it).emit() }
            )
            NumberSettingRow(
                title = stringResource(R.string.summary_response_tokens),
                value = state.summaryResponseTokens.toString(),
                onValueChange = { MainUiIntent.ChangeSummaryResponseTokens(it).emit() }
            )
        }
    }
}

@Composable
private fun NumberSettingRow(
    title: String,
    value: String,
    helper: String? = null,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (!helper.isNullOrBlank()) {
            Text(
                helper,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
        }
    }
}

@Composable
private fun PrivacyPanel(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(
                title = stringResource(R.string.privacy_and_backup),
                action = stringResource(R.string.export)
            )
            SettingSwitchRow(
                Icons.Rounded.Shield,
                stringResource(R.string.local_first),
                stringResource(R.string.local_first_desc),
                state.localFirstEnabled,
                onCheckedChange = {}
            )
            SettingSwitchRow(
                Icons.Rounded.Refresh,
                stringResource(R.string.streaming_response),
                stringResource(R.string.streaming_response_desc),
                state.streamEnabled,
                onCheckedChange = { MainUiIntent.ToggleStreamEnabled(it).emit() }
            )
        }
    }
}

@Composable
private fun DebugPanel(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.debug_mode))
            SettingSwitchRow(
                Icons.Rounded.BugReport,
                stringResource(R.string.debug_mode),
                stringResource(R.string.debug_mode_desc),
                state.debugModeEnabled,
                onCheckedChange = { MainUiIntent.ToggleDebugModeEnabled(it).emit() }
            )
            if (state.debugModeEnabled) {
                RpInfoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { MainUiIntent.OpenRequestLogs.emit() },
                    icon = Icons.Rounded.DataObject,
                    title = stringResource(R.string.request_logs),
                    subtitle = stringResource(R.string.request_logs_entry_subtitle)
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun PromptPostProcessingMode.titleRes(): Int {
    return when (this) {
        PromptPostProcessingMode.None -> R.string.prompt_post_processing_none
        PromptPostProcessingMode.Merge -> R.string.prompt_post_processing_merge
        PromptPostProcessingMode.SemiStrict -> R.string.prompt_post_processing_semi_strict
        PromptPostProcessingMode.Strict -> R.string.prompt_post_processing_strict
        PromptPostProcessingMode.SingleUserMessage -> R.string.prompt_post_processing_single_user
    }
}

private fun PromptPostProcessingMode.descriptionRes(): Int {
    return when (this) {
        PromptPostProcessingMode.None -> R.string.prompt_post_processing_none_desc
        PromptPostProcessingMode.Merge -> R.string.prompt_post_processing_merge_desc
        PromptPostProcessingMode.SemiStrict -> R.string.prompt_post_processing_semi_strict_desc
        PromptPostProcessingMode.Strict -> R.string.prompt_post_processing_strict_desc
        PromptPostProcessingMode.SingleUserMessage -> R.string.prompt_post_processing_single_user_desc
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
                    recentSessions = emptyList(),
                    totalCharacters = 24,
                    totalWorldBooks = 7
                ),
                settingsState = MainSettingsState(
                    userName = "You",
                    userDescription = "",
                    selectedProviderId = "",
                    providers = emptyList(),
                    temperature = 0.8f,
                    topP = 1.0f,
                    maxTokens = 1200,
                    contextTokens = 8192,
                    localFirstEnabled = true,
                    streamEnabled = true,
                    promptPostProcessingMode = PromptPostProcessingMode.None,
                    includeThinkInContext = false,
                    debugModeEnabled = false,
                    autoSummaryEnabled = false,
                    summaryTriggerMessageCount = 20,
                    summaryWordsLimit = 500,
                    summaryMaxMessagesPerRequest = 0,
                    summaryResponseTokens = 800
                )
            ),
            emit = {}
        )
    }
}
