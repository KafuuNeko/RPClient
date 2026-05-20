package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class CharacterListViewEvent : IViewEvent {
    data object OpenCharacterCardImporter : CharacterListViewEvent()
    data class OpenCharacterCardJsonExporter(
        val characterId: Long,
        val fileName: String
    ) : CharacterListViewEvent()
}
