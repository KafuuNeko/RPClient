package me.kafuuneko.rpclient.libs.room.model

/** 角色目录轻量投影，数量包含全部仍保存的通用条目。 */
data class MemoryCharacterOverview(
    val id: Long,
    val name: String,
    val avatar: String,
    val memoryCount: Int
)
