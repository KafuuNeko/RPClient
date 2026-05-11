package me.kafuuneko.rpclient.feature.chatcreate

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ChatCreateViewModel : CoreViewModelWithEvent<ChatCreateUiIntent, ChatCreateUiState>(
    ChatCreateUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()

    @UiIntentObserver(ChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<ChatCreateUiState.None>()) return
        ChatCreateUiState.Normal(loadState = ChatCreateLoadState.Loading).setup()
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val lorebooks = mLorebookRepository.getAllLorebooks()
            val entries = lorebooks.flatMap { lorebook ->
                mLorebookRepository.getEntriesByLorebookId(lorebook.id)
                    .map { entry -> ChatCreateLorebookEntryItem(entry, lorebook.name) }
            }
            characters to entries
        }
        ChatCreateUiState.Normal(
            loadState = ChatCreateLoadState.None,
            form = getOrNull<ChatCreateUiState.Normal>()?.form
                ?.copy(selectedCharacterId = data.first.firstOrNull()?.id)
                ?: ChatCreateForm(selectedCharacterId = data.first.firstOrNull()?.id),
            characters = data.first,
            selectedCharacterFirstMessages = data.first.firstOrNull()?.getFirstMessageList().orEmpty(),
            lorebookEntries = data.second
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
                selectedFirstMessageIndex = null
            ),
            selectedCharacterFirstMessages = character.getFirstMessageList()
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
        if (uiState.lorebookEntries.none { it.entry.id == intent.entryId }) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = if (intent.entryId in selectedIds) {
                    selectedIds - intent.entryId
                } else {
                    selectedIds + intent.entryId
                }
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
        val sessionId = withContext(Dispatchers.IO) {
            mChatRepository.createSession(
                characterId = character.id,
                title = uiState.form.normalizedTitle(character),
                userNote = uiState.form.userNote.trim(),
                lorebookEntryIds = uiState.form.selectedLorebookEntryIds.sorted()
            )
        }
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply {
                putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
                putString(ChatActivity.EXTRA_FIRST_MESSAGE, firstMessageSelection.value)
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

    private fun ChatCreateForm.normalizedTitle(
        character: Character
    ): String {
        return title.trim().ifBlank { "Chat with ${character.name}" }
    }

    private data class FirstMessageSelection(val value: String?)
}
