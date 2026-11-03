package me.kafuuneko.rpclient.feature.charactercontent.presentation

import me.kafuuneko.rpclient.libs.character.CharacterContentKind

/** 角色页的生命周期、筛选和导航操作。 */
sealed interface CharacterContentUiIntent {
    data class Resume(val characterId: Long) : CharacterContentUiIntent
    data object Retry : CharacterContentUiIntent
    data class SelectFilter(val filter: CharacterContentKind?) : CharacterContentUiIntent
    data class OpenContent(val type: CharacterContentKind, val contentId: Long) :
        CharacterContentUiIntent

    data object CreateChat : CharacterContentUiIntent
    data object Back : CharacterContentUiIntent
}
