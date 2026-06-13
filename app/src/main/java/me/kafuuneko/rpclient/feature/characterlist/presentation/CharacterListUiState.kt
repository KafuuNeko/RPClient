package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.libs.room.entity.Character

/** 角色列表页状态；Normal 同时保存筛选条件、选择项和头像路径缓存。 */
sealed class CharacterListUiState {
    data object None : CharacterListUiState()

    data class Normal(
        val loadState: CharacterListLoadState = CharacterListLoadState.None,
        val searchText: String = "",
        val selectedCharacterId: Long? = null,
        val characters: List<Character> = emptyList(),
        val avatarFilePaths: Map<String, String> = emptyMap()
    ) : CharacterListUiState()

    data object Finished : CharacterListUiState()
}

/** 角色列表读取或导入期间的阻塞状态。 */
sealed class CharacterListLoadState {
    data object None : CharacterListLoadState()
    data object Loading : CharacterListLoadState()
}
