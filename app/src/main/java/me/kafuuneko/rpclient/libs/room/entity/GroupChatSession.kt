package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
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
    // 群聊会话 ID。
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 会话标题。
    val title: String,
    // 会话创建时间。
    val createTime: Long,
    // 会话最近活动时间，用于列表排序。
    val latestTime: Long,
    // 当前群聊使用的用户名。
    val userName: String,
    // 当前群聊使用的用户描述。
    val userDescription: String,
    // 群聊级场景设定，会与成员角色卡共同进入 Prompt。
    val scenario: String = "",
    // 用户笔记。
    val userNote: String = "",
    // 当前群聊已启用的世界书条目 ID 集合，按 JSON 数组保存。
    val lorebookEntrySet: String = "[]",
    // 世界书 sticky/cooldown 等 timed effects 运行时状态，不是用户可编辑内容。
    val worldInfoStateJson: String = "{}",
    // 群聊系统提示词覆盖；为空时使用全局默认值。
    val systemPromptOverride: String = "",
    // 群聊成员发言引导提示词覆盖；为空时使用全局默认值。
    val groupNudgePromptOverride: String = "",
    // 新群聊开场提示词覆盖；为空时使用全局默认值。
    val newGroupChatPromptOverride: String = "",
    // 本轮发言者选择策略。
    val activationStrategy: ActivationStrategy = ActivationStrategy.Natural,
    // 是否允许刚发言的角色在下一轮再次被选中。
    val allowSelfResponses: Boolean = false,
    // 多成员角色卡进入 Prompt 时采用 Swap 或 Join 模式。
    val characterCardMode: CharacterCardMode = CharacterCardMode.Swap,
    // 是否仍将已静音成员的角色卡包含在 Prompt 中。
    val includeMutedCards: Boolean = false,
    // 是否启用自动选择成员并连续生成回复。
    val autoModeEnabled: Boolean = false,
    // 是否从单个角色的输出中裁剪其他成员的发言前缀。
    val trimOtherSpeakers: Boolean = true,
    // 是否仅暂停当前群聊的自动总结；手动总结不受影响。
    val autoSummaryPaused: Boolean = false,
    // 是否注入本轮参与 Prompt 的成员角色卡示例，成员范围仍遵循 Swap/Join 配置。
    @ColumnInfo(defaultValue = "1") val includeCharacterExamples: Boolean = true
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
