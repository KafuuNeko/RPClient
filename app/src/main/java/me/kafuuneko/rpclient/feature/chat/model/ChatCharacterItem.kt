package me.kafuuneko.rpclient.feature.chat.model

import androidx.compose.ui.graphics.Color

/** 单聊页面使用的角色卡展示快照，包含 Prompt 相关字段和头像展示信息。 */
data class ChatCharacterItem(
    val id: Long,
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val examplesOfDialogue: String,
    val postHistoryInstructions: String,
    val creatorNotes: String,
    val avatarText: String,
    val accentColor: Color,
    val avatarFilePath: String? = null
)
