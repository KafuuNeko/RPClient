package me.kafuuneko.rpclient.feature.worldbookedit.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

data class WorldBookEditForm(
    val id: Long = 0L,
    val name: String = "",
    val entries: List<WorldBookEntryListItem> = emptyList()
) {
    val isNew: Boolean
        get() = id == 0L

    companion object {
        fun from(lorebook: Lorebook, entries: List<LorebookEntry>): WorldBookEditForm {
            return WorldBookEditForm(
                id = lorebook.id,
                name = lorebook.name,
                entries = entries.map { WorldBookEntryListItem.from(it) }
            )
        }
    }

    fun toLorebook(): Lorebook {
        return Lorebook(
            id = id,
            name = name.trim()
        )
    }
}

data class WorldBookEntryListItem(
    val id: Long,
    val name: String,
    val keywords: List<String>,
    val order: Int,
    val depth: Int
) {
    companion object {
        fun from(entry: LorebookEntry): WorldBookEntryListItem {
            return WorldBookEntryListItem(
                id = entry.id,
                name = entry.name,
                keywords = entry.getKeywordList(),
                order = entry.order,
                depth = entry.depth
            )
        }
    }
}

fun WorldBookEditForm.toComparableForm(): WorldBookEditForm {
    return copy(
        name = name.trim()
    )
}
