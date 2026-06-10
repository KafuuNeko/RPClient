package me.kafuuneko.rpclient.feature.groupchatcreate

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import me.kafuuneko.rpclient.libs.utils.toDefaultChatTitle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GroupChatCreateViewModel :
    CoreViewModelWithEvent<GroupChatCreateUiIntent, GroupChatCreateUiState>(
        GroupChatCreateUiState.None
    ), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mContext by inject<Context>()

    @UiIntentObserver(GroupChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<GroupChatCreateUiState.None>()) return
        GroupChatCreateUiState.Normal(
            loadState = GroupChatCreateLoadState.Loading
        ).setup()
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters().map {
                GroupChatCreateCharacterItem(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    selected = false,
                    characterLorebookId = it.characterLorebookId
                )
            }
            val lorebookGroups = mLorebookRepository.getAllLorebooks().map { lorebook ->
                GroupChatLorebookGroupItem(
                    lorebookId = lorebook.id,
                    lorebookName = lorebook.name,
                    entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id)
                        .sortedBy { it.order }
                        .map { entry ->
                            GroupChatLorebookEntryItem(
                                id = entry.id,
                                lorebookId = lorebook.id,
                                lorebookName = lorebook.name,
                                name = entry.name,
                                content = entry.content,
                                keywords = entry.getKeywordList(),
                                secondaryKeywords = entry.getSecondaryKeywordList(),
                                constant = entry.constant,
                                order = entry.order,
                                depth = entry.depth,
                                enabled = false
                            )
                        }
                )
            }.filter { it.entries.isNotEmpty() }
            characters to lorebookGroups
        }
        GroupChatCreateUiState.Normal(
            characters = data.first,
            visibleCharacters = data.first,
            lorebookGroups = data.second
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.Back::class)
    private fun onBack() {
        GroupChatCreateUiState.Finished.setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: GroupChatCreateUiIntent.ChangeTitle) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(title = intent.value).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ChangeSearchQuery::class)
    private fun onChangeSearchQuery(intent: GroupChatCreateUiIntent.ChangeSearchQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val query = intent.value.trim()
        uiState.copy(
            searchQuery = intent.value,
            visibleCharacters = uiState.characters.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: GroupChatCreateUiIntent.ToggleCharacter) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val characters = uiState.characters.map {
            if (it.id == intent.characterId) it.copy(selected = !it.selected) else it
        }
        val selectedCharacter = characters.firstOrNull { it.id == intent.characterId }
        val defaultEntryIds = selectedCharacter
            ?.takeIf { it.selected }
            ?.characterLorebookId
            ?.takeIf { it > 0L }
            ?.let { lorebookId ->
                uiState.lorebookGroups
                    .firstOrNull { it.lorebookId == lorebookId }
                    ?.entries
                    ?.mapTo(mutableSetOf()) { it.id }
            }
            .orEmpty()
        uiState.copy(
            characters = characters,
            visibleCharacters = characters.visibleFor(uiState.searchQuery),
            selectedLorebookEntryIds =
                uiState.selectedLorebookEntryIds + defaultEntryIds
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.SelectStrategy::class)
    private fun onSelectStrategy(intent: GroupChatCreateUiIntent.SelectStrategy) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(activationStrategy = intent.strategy).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleAllowSelfResponses::class)
    private fun onToggleAllowSelfResponses(
        intent: GroupChatCreateUiIntent.ToggleAllowSelfResponses
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(allowSelfResponses = intent.enabled).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacterGreetings::class)
    private fun onToggleCharacterGreetings(
        intent: GroupChatCreateUiIntent.ToggleCharacterGreetings
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(useCharacterGreetings = intent.enabled).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebook::class)
    /** 切换创建表单中一本世界书的全部条目。 */
    private fun onToggleLorebook(intent: GroupChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val entryIds = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        uiState.copy(
            selectedLorebookEntryIds =
                uiState.selectedLorebookEntryIds.toggleAll(entryIds)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebookEntry::class)
    /** 切换创建表单中的单个世界书条目。 */
    private fun onToggleLorebookEntry(
        intent: GroupChatCreateUiIntent.ToggleLorebookEntry
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.lorebookGroups.none { group ->
                group.entries.any { it.id == intent.entryId }
            }
        ) return
        uiState.copy(
            selectedLorebookEntryIds =
                uiState.selectedLorebookEntryIds.toggle(intent.entryId)
        ).setup()
    }

    @UiIntentObserver(GroupChatCreateUiIntent.Create::class)
    private suspend fun onCreate() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != GroupChatCreateLoadState.None) return
        val characterIds = uiState.characters.filter { it.selected }.map { it.id }
        if (characterIds.size < 2) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_select_two_characters
            ).tryEmit()
            return
        }
        uiState.copy(loadState = GroupChatCreateLoadState.Creating).setup()
        val createTime = System.currentTimeMillis()
        val sessionId = withContext(Dispatchers.IO) {
            mGroupChatRepository.createSession(
                title = uiState.title.trim().ifBlank { createTime.toDefaultChatTitle() },
                userName = AppModel.userName.trim().ifBlank { "You" },
                userDescription = AppModel.userDescription.trim(),
                characterIds = characterIds,
                lorebookEntryIds = uiState.selectedLorebookEntryIds.sorted(),
                activationStrategy = uiState.activationStrategy,
                allowSelfResponses = uiState.allowSelfResponses,
                useCharacterGreetings = uiState.useCharacterGreetings,
                createTime = createTime
            )
        }
        AppViewEvent.StartActivity(
            activity = GroupChatActivity::class.java,
            extras = Bundle().apply {
                putString(GroupChatActivity.EXTRA_SESSION_ID, sessionId.toString())
            }
        ).emitAndAwait()
        GroupChatCreateUiState.Finished.setup()
    }

    private fun List<GroupChatCreateCharacterItem>.visibleFor(
        query: String
    ): List<GroupChatCreateCharacterItem> {
        val normalized = query.trim()
        return filter {
            normalized.isBlank() ||
                it.name.contains(normalized, ignoreCase = true) ||
                it.description.contains(normalized, ignoreCase = true)
        }
    }
}
