package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.PrimaryKey

data class ChatSession(
    // 对话 ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 当前对话角色卡
    val characterId: Long,
    // 当前对话创建时间
    val createTime: Long,
    // 当前对话最后打开时间
    val latestTime: Long,
    // 当前对话已启用的世界书条目集
    val lorebookEntrySet: String,
    // 当前对话总结
    val summarize: String,
    // 用户笔记
    val userNote: String
)
