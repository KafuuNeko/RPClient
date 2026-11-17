package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.libs.character.CharacterCardExportFormat
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 需要 Activity 调用系统文件选择器完成的一次性事件。 */
sealed class CharacterListViewEvent : IViewEvent {
    data object OpenCharacterCardImporter : CharacterListViewEvent()

    data class OpenCharacterCardExporter(
        val characterId: Long,
        val format: CharacterCardExportFormat,
        val fileName: String
    ) : CharacterListViewEvent()

    data class OpenCharacterArchiveExporter(
        val characterIds: List<Long>,
        val format: CharacterCardExportFormat,
        val fileName: String
    ) : CharacterListViewEvent()
}
