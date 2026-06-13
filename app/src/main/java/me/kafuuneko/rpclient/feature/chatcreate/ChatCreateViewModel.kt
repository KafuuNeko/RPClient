package me.kafuuneko.rpclient.feature.chatcreate

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import me.kafuuneko.rpclient.libs.utils.toDefaultChatTitle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 新建单角色会话页状态持有者，负责角色选择、世界书授权和初始会话落库。 */
class ChatCreateViewModel : CoreViewModelWithEvent<ChatCreateUiIntent, ChatCreateUiState>(
    ChatCreateUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mMacroResolver by inject<PromptMacroResolver>()

    @UiIntentObserver(ChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<ChatCreateUiState.None>()) return
        ChatCreateUiState.Normal(loadState = ChatCreateLoadState.Loading).setup()
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val lorebooks = mLorebookRepository.getAllLorebooks()
            val groups = lorebooks.map { lorebook ->
                val entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id)
                    .sortedBy { it.order }
                    .map { entry -> ChatCreateLorebookEntryItem(entry, lorebook.name) }
                ChatCreateLorebookGroupItem(
                    lorebookId = lorebook.id,
                    lorebookName = lorebook.name,
                    entryCount = entries.size,
                    entries = entries
                )
            }.filter { it.entries.isNotEmpty() }
            characters to groups
        }
        val selectedCharacter = data.first.firstOrNull()
        val selectedLorebookEntryIds = selectedCharacter
            ?.defaultLorebookEntryIds(data.second)
            .orEmpty()
        ChatCreateUiState.Normal(
            loadState = ChatCreateLoadState.None,
            form = getOrNull<ChatCreateUiState.Normal>()?.form
                ?.let { form ->
                    form.copy(
                        selectedCharacterId = selectedCharacter?.id,
                        selectedLorebookEntryIds = form.selectedLorebookEntryIds + selectedLorebookEntryIds
                    )
                }
                ?: ChatCreateForm(
                    selectedCharacterId = selectedCharacter?.id,
                    selectedLorebookEntryIds = selectedLorebookEntryIds
                ),
            characters = data.first,
            selectedCharacterFirstMessages = selectedCharacter?.getChatFirstMessageList().orEmpty(),
            lorebookGroups = data.second
        ).setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.Back::class)
    private fun onBack() {
        ChatCreateUiState.Finished.setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: ChatCreateUiIntent.SelectCharacter) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        uiState.copy(
            form = uiState.form.copy(
                selectedCharacterId = intent.characterId,
                selectedFirstMessageIndex = null,
                selectedLorebookEntryIds = uiState.form.selectedLorebookEntryIds +
                    character.defaultLorebookEntryIds(uiState.lorebookGroups)
            ),
            selectedCharacterFirstMessages = character.getChatFirstMessageList()
        ).setup()
    }

    @UiIntentObserver(ChatCreateUiIntent.SelectFirstMessage::class)
    private fun onSelectFirstMessage(intent: ChatCreateUiIntent.SelectFirstMessage) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (intent.index !in uiState.selectedCharacterFirstMessages.indices) return
        updateForm { copy(selectedFirstMessageIndex = intent.index) }
    }

    @UiIntentObserver(ChatCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: ChatCreateUiIntent.ChangeTitle) {
        updateForm { copy(title = intent.value) }
    }

    @UiIntentObserver(ChatCreateUiIntent.ChangeUserNote::class)
    private fun onChangeUserNote(intent: ChatCreateUiIntent.ChangeUserNote) {
        updateForm { copy(userNote = intent.value) }
    }

    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: ChatCreateUiIntent.ToggleLorebookEntry) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.lorebookGroups.none { group -> group.entries.any { it.entry.id == intent.entryId } }) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = selectedIds.toggle(intent.entryId)
            )
        }
    }

    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: ChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val group = uiState.lorebookGroups.firstOrNull { it.lorebookId == intent.lorebookId } ?: return
        val entryIds = group.entries.map { it.entry.id }.toSet()
        if (entryIds.isEmpty()) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = selectedIds.toggleAll(entryIds)
            )
        }
    }

    @UiIntentObserver(ChatCreateUiIntent.CreateChat::class)
    private suspend fun onCreateChat() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != ChatCreateLoadState.None) return
        val character = uiState.selectedCharacter() ?: run {
            AppViewEvent.PopupToastMessageByResId(R.string.no_character_selected).tryEmit()
            return
        }
        val firstMessageSelection = uiState.resolveFirstMessageSelection() ?: return
        uiState.copy(loadState = ChatCreateLoadState.Creating).setup()
        val userName = AppModel.userName.trim().ifBlank { "You" }
        val userDescription = AppModel.userDescription.trim()
        val createTime = System.currentTimeMillis()
        val sessionTitle = uiState.form.normalizedTitle(createTime)

        val firstMessageContent = firstMessageSelection.value?.let { rawFirstMessage ->
            val session = ChatSession(
                id = 0L,
                characterId = character.id,
                createTime = createTime,
                latestTime = createTime,
                lorebookEntrySet = "",
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userName = userName,
                userDescription = userDescription,
                creatorNotes = null
            )
            val context = PromptBuildContext(
                userName = userName,
                userDescription = userDescription,
                character = character,
                session = session,
                summary = "",
                messages = emptyList(),
                currentUserMessage = null,
                candidateLorebookEntries = emptyList(),
                provider = mLLMRepository.getSelectedProvider(),
                maxContextTokens = 0,
                maxResponseTokens = 0
            )
            mMacroResolver.resolve(rawFirstMessage, context)
        }

        val sessionId = withContext(Dispatchers.IO) {
            mChatRepository.createSessionWithFirstMessage(
                characterId = character.id,
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userName = userName,
                userDescription = userDescription,
                lorebookEntryIds = uiState.form.selectedLorebookEntryIds.sorted(),
                firstMessageContent = firstMessageContent,
                createTime = createTime
            )
        }
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply {
                putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
            }
        ).emitAndAwait()
        ChatCreateUiState.Finished.setup()
    }

    private fun updateForm(block: ChatCreateForm.() -> ChatCreateForm) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    private fun ChatCreateUiState.Normal.selectedCharacter(): Character? {
        val characterId = form.selectedCharacterId ?: return null
        return characters.firstOrNull { it.id == characterId }
    }

    private fun ChatCreateUiState.Normal.resolveFirstMessageSelection(): FirstMessageSelection? {
        if (selectedCharacterFirstMessages.isEmpty()) return FirstMessageSelection(null)
        val selectedIndex = form.selectedFirstMessageIndex
        if (selectedIndex == null || selectedIndex !in selectedCharacterFirstMessages.indices) {
            AppViewEvent.PopupToastMessageByResId(R.string.first_message_required).tryEmit()
            return null
        }
        return FirstMessageSelection(selectedCharacterFirstMessages[selectedIndex])
    }

    private fun ChatCreateForm.normalizedTitle(createTime: Long): String {
        return title.trim().ifBlank { createTime.toDefaultChatTitle() }
    }

    private fun Character.defaultLorebookEntryIds(
        lorebookGroups: List<ChatCreateLorebookGroupItem>
    ): Set<Long> {
        if (characterLorebookId == 0L) return emptySet()
        return lorebookGroups
            .firstOrNull { it.lorebookId == characterLorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.entry.id }
            .orEmpty()
    }

    private data class FirstMessageSelection(val value: String?)
}
