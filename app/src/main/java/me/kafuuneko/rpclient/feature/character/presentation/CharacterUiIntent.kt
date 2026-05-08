package me.kafuuneko.rpclient.feature.character.presentation

sealed class CharacterUiIntent {
    data object Init : CharacterUiIntent()

    data object Resume : CharacterUiIntent()

    data object Back : CharacterUiIntent()

    data class SelectCharacter(val characterId: String) : CharacterUiIntent()

    data object ImportCharacter : CharacterUiIntent()

    data object CreateCharacter : CharacterUiIntent()
}

