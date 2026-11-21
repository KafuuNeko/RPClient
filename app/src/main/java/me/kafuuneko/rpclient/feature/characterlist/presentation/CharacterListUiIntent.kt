package me.kafuuneko.rpclient.feature.characterlist.presentation

import android.net.Uri
import me.kafuuneko.rpclient.libs.character.CharacterCardExportFormat

/** 角色列表页可接收的用户意图与文件选择结果。 */
sealed class CharacterListUiIntent {
    data object Init : CharacterListUiIntent()

    data object Resume : CharacterListUiIntent()

    data object Back : CharacterListUiIntent()

    data class ChangeSearchText(val value: String) : CharacterListUiIntent()

    data class OpenCharacter(val characterId: Long) : CharacterListUiIntent()

    data class BeginCharacterSelection(val characterId: Long) : CharacterListUiIntent()

    data class ToggleCharacterSelection(val characterId: Long) : CharacterListUiIntent()

    data object ToggleVisibleCharacterSelection : CharacterListUiIntent()

    data object ClearCharacterSelection : CharacterListUiIntent()

    data object DeleteSelectedCharactersClick : CharacterListUiIntent()

    data object ConfirmDeleteSelectedCharacters : CharacterListUiIntent()

    data object ConfirmDeleteSelectedCharactersWithChats : CharacterListUiIntent()

    data object ExportSelectedCharactersClick : CharacterListUiIntent()

    data class ExportSelectedCharacters(
        val characterIds: List<Long>,
        val format: CharacterCardExportFormat,
        val uri: Uri
    ) : CharacterListUiIntent()

    data class VisibleCharactersChanged(
        val characterIds: Set<Long>,
        val targetSizePx: Int
    ) : CharacterListUiIntent()

    data object CreateCharacter : CharacterListUiIntent()

    data object ImportCharacterClick : CharacterListUiIntent()

    data class ImportCharacterCards(val uris: List<Uri>) : CharacterListUiIntent()

    data object ImportCharacterWithGlobalLorebookBudget : CharacterListUiIntent()

    data object ImportCharacterWithOriginalLorebookBudget : CharacterListUiIntent()

    data object DismissDialog : CharacterListUiIntent()

    data class ExportCharacterClick(val characterId: Long) : CharacterListUiIntent()

    data class ChooseExportFormat(val format: CharacterCardExportFormat) : CharacterListUiIntent()

    data class ExportCharacter(
        val characterId: Long,
        val format: CharacterCardExportFormat,
        val uri: Uri
    ) : CharacterListUiIntent()
}
