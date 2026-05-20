package me.kafuuneko.rpclient.feature.characterlist.presentation

import android.net.Uri

sealed class CharacterListUiIntent {
    data object Init : CharacterListUiIntent()

    data object Resume : CharacterListUiIntent()

    data object Back : CharacterListUiIntent()

    data class ChangeSearchText(val value: String) : CharacterListUiIntent()

    data class SelectCharacter(val characterId: Long) : CharacterListUiIntent()

    data object CreateCharacter : CharacterListUiIntent()

    data object ImportCharacterClick : CharacterListUiIntent()

    data class ImportCharacterCard(val uri: Uri) : CharacterListUiIntent()

    data class ExportCharacterJsonClick(val characterId: Long) : CharacterListUiIntent()

    data class ExportCharacterJson(val characterId: Long, val uri: Uri) : CharacterListUiIntent()
}
