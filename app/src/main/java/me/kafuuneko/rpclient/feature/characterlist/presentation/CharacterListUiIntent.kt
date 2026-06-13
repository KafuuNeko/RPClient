package me.kafuuneko.rpclient.feature.characterlist.presentation

import android.net.Uri

/** 角色列表页可接收的用户意图与文件选择结果。 */
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
