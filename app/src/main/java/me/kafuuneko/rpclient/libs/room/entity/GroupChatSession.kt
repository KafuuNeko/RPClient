package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_chat_sessions")
data class GroupChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createTime: Long,
    val latestTime: Long,
    val userName: String,
    val userDescription: String,
    val scenario: String = "",
    val userNote: String = "",
    val lorebookEntrySet: String = "[]",
    val worldInfoStateJson: String = "{}",
    val systemPromptOverride: String = "",
    val groupNudgePromptOverride: String = "",
    val newGroupChatPromptOverride: String = "",
    val activationStrategy: ActivationStrategy = ActivationStrategy.Natural,
    val allowSelfResponses: Boolean = false,
    val characterCardMode: CharacterCardMode = CharacterCardMode.Swap,
    val includeMutedCards: Boolean = false,
    val autoModeEnabled: Boolean = false,
    val trimOtherSpeakers: Boolean = true
) {
    enum class ActivationStrategy {
        Manual,
        Natural,
        List,
        Pooled
    }

    enum class CharacterCardMode {
        Swap,
        Join
    }
}
