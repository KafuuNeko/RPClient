package me.kafuuneko.rpclient.feature.worldbooklist.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook

data class WorldBookListItem(
    val id: Long,
    val name: String,
    val entryCount: Int
) {
    companion object {
        fun from(lorebook: Lorebook, entryCount: Int): WorldBookListItem {
            return WorldBookListItem(
                id = lorebook.id,
                name = lorebook.name,
                entryCount = entryCount
            )
        }
    }
}

