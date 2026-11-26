package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话工具策略与运行提交时钟。
 * - 恰好一个所有者由数据库触发器保障，删除会话级联清理运行及剧情状态。
 * - Agent 开关与设置版本以会话表为准，避免冗余权威值。
 */
@Entity(
    tableName = "conversation_tool_policies",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupChatSession::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index(value = ["chatId"], unique = true), Index(value = ["groupId"], unique = true)],
)
data class ConversationToolPolicy(
    @PrimaryKey val conversationKey: String,
    val chatId: Long? = null,
    val groupId: Long? = null,
    val enabledToolIdsJson: String = "[\"search_lore\",\"read_lore_entry\",\"search_chat\",\"read_messages\",\"search_memory\",\"read_memory\",\"upsert_memory\",\"correct_memory\",\"invalidate_memory\",\"promote_memory_scope\",\"roll_dice\",\"calculate\",\"get_scene_state\",\"update_scene_state\",\"ask_user\"]",
    val memoryReadOnly: Boolean = false,
    val autoPromoteMemory: Boolean = false,
    val maxToolRounds: Int = 5,
    val maxToolCalls: Int = 20,
    val maxModelRequests: Int = 8,
    val maxTotalTokens: Long = 64_000,
    val toolTimeoutMillis: Long = 30_000,
    val maxActiveMillis: Long = 180_000,
    val compactionWatermarkPercent: Int = 80,
    val maxGroupModelRequests: Int = 32,
    val maxGroupTotalTokens: Long = 256_000,
    val maxAutoBatches: Int = 3,
    val maxGroupActiveMillis: Long = 900_000,
    val historyVersion: Long = 0,
    val executionEpoch: Long = 0,
)
