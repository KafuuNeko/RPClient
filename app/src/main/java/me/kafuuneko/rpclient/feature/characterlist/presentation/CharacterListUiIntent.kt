package me.kafuuneko.rpclient.feature.characterlist.presentation

sealed class CharacterListUiIntent {
    data object Init : CharacterListUiIntent()

    data object Resume : CharacterListUiIntent()

    data object Back : CharacterListUiIntent()

    data class ChangeSearchText(val value: String) : CharacterListUiIntent()

    data class SelectCharacter(val characterId: Long) : CharacterListUiIntent()

    data object CreateCharacter : CharacterListUiIntent()
}
