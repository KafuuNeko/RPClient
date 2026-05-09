package me.kafuuneko.rpclient.feature.characterlist

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListLoadState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiIntent
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiState
import me.kafuuneko.rpclient.feature.characteredit.CharacterEditActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterListViewModel : CoreViewModelWithEvent<CharacterListUiIntent, CharacterListUiState>(
    CharacterListUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()

    @UiIntentObserver(CharacterListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<CharacterListUiState.None>()) return
        CharacterListUiState.Normal(loadState = CharacterListLoadState.Loading).setup()
        refreshCharacters(selectedCharacterId = null)
    }

    @UiIntentObserver(CharacterListUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        refreshCharacters(selectedCharacterId = uiState.selectedCharacterId)
    }

    @UiIntentObserver(CharacterListUiIntent.Back::class)
    private fun onBack() {
        CharacterListUiState.Finished.setup()
    }

    @UiIntentObserver(CharacterListUiIntent.ChangeSearchText::class)
    private fun onChangeSearchText(intent: CharacterListUiIntent.ChangeSearchText) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        uiState.copy(searchText = intent.value).setup()
    }

    @UiIntentObserver(CharacterListUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: CharacterListUiIntent.SelectCharacter) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        if (uiState.characters.none { it.id == intent.characterId }) return
        uiState.copy(selectedCharacterId = intent.characterId).setup()
        AppViewEvent.StartActivity(
            activity = CharacterEditActivity::class.java,
            extras = Bundle().apply {
                putLong(CharacterEditActivity.EXTRA_CHARACTER_ID, intent.characterId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(CharacterListUiIntent.CreateCharacter::class)
    private fun onCreateCharacter() {
        AppViewEvent.StartActivity(CharacterEditActivity::class.java).tryEmit()
    }

    private suspend fun refreshCharacters(selectedCharacterId: Long?) {
        val uiState = getOrNull<CharacterListUiState.Normal>() ?: return
        val characters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }
        val avatarFilePaths = withContext(Dispatchers.IO) {
            characters.mapNotNull { character ->
                character.avatar
                    .takeIf { it.isNotBlank() }
                    ?.let { uuid -> mFileRepository.getFile(uuid)?.absolutePath?.let { uuid to it } }
            }.toMap()
        }
        uiState.copy(
            loadState = CharacterListLoadState.None,
            selectedCharacterId = characters.firstOrNull { it.id == selectedCharacterId }?.id,
            characters = characters,
            avatarFilePaths = avatarFilePaths
        ).setup()
    }
}
