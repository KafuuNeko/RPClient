package me.kafuuneko.rpclient.feature.charactercontent.presentation

import me.kafuuneko.rpclient.feature.charactercontent.model.CharacterContentItem
import me.kafuuneko.rpclient.libs.character.CharacterContentKind

/** 角色页在首次读取、正常浏览和退出过渡之间切换。 */
sealed interface CharacterContentUiState {
    data object Loading : CharacterContentUiState
    data object LoadFailed : CharacterContentUiState
    data class Normal(val characterName: String, val list: CharacterContentListState) :
        CharacterContentUiState

    data class Finished(val previous: CharacterContentUiState) : CharacterContentUiState
}

/** 筛选只改变当前角色的可见条目，不需要再次读取数据库。 */
data class CharacterContentListState(
    val items: List<CharacterContentItem>,
    val filter: CharacterContentKind? = null,
) {
    val visibleItems = if (filter == null) items else items.filter { it.type == filter }
}
