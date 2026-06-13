package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 需要 Activity 调用系统文件选择器完成的一次性事件。 */
sealed class CharacterListViewEvent : IViewEvent {
    data object OpenCharacterCardImporter : CharacterListViewEvent()
    data class OpenCharacterCardJsonExporter(
        val characterId: Long,
        val fileName: String
    ) : CharacterListViewEvent()
}
