package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 群聊会话及其生成策略配置。
 *
 * 成员、消息和摘要分别存放在关系表中；本实体只保存会话级 Prompt 覆盖、
 * 世界书状态以及自动发言行为。
 */
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
    /** 本轮发言者选择策略。 */
    enum class ActivationStrategy {
        Manual,
        Natural,
        List,
        Pooled
    }

    /** 多角色卡进入 Prompt 时采用替换当前角色还是联合注入。 */
    enum class CharacterCardMode {
        Swap,
        Join
    }
}
