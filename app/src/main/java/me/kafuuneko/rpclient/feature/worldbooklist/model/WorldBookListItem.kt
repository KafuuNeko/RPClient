package me.kafuuneko.rpclient.feature.worldbooklist.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook

/** 世界书列表展示模型，附带需要额外查询得到的条目数量。 */
data class WorldBookListItem(
    val id: Long,
    val name: String,
    val entryCount: Int
) {
    companion object {
        /** 将持久化实体和聚合条目数映射为列表项。 */
        fun from(lorebook: Lorebook, entryCount: Int): WorldBookListItem {
            return WorldBookListItem(
                id = lorebook.id,
                name = lorebook.name,
                entryCount = entryCount
            )
        }
    }
}
