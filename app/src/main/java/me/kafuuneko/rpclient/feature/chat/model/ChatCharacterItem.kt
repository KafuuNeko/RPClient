package me.kafuuneko.rpclient.feature.chat.model

import androidx.compose.ui.graphics.Color

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

