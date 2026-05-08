package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "character"
)
data class Character(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 角色名称
    val name: String,
    // 角色头像
    val avatar: String,
    // 性格标签
    val characterTags: String,
    // 核心设定
    val personality: String,
    // 场景设定
    val scenario: String,
    // 开场白
    val firstMessages: String,
    // 对话示例
    val examplesOfDialogue: String,
    // 后置提示词
    val postHistoryInstructions: String
)
