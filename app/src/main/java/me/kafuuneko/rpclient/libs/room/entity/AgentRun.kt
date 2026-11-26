package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState

/** 运行摘要与提交资格；协议历史及原始工具结果只保存在不参与备份的检查点中。 */
@Entity(
    tableName = "agent_runs",
    foreignKeys = [
        ForeignKey(
            entity = ConversationToolPolicy::class,
            parentColumns = ["conversationKey"],
            childColumns = ["conversationKey"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UserPersona::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ],
    indices = [Index("conversationKey"), Index(
        value = ["activeConversationKey"],
        unique = true
    ), Index("characterId"), Index("personaId")],
)
data class AgentRun(
    @PrimaryKey val id: String,
    val conversationKey: String,
    val activeConversationKey: String?,
    val state: AgentRunState = AgentRunState.Preparing,
    val characterId: Long?,
    val personaId: Long?,
    val settingsVersion: Long,
    val historyVersion: Long,
    val executionEpoch: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val batchId: String? = null,
    val activeMillis: Long = 0,
    val toolRounds: Int = 0,
    val toolCalls: Int = 0,
    val modelRequests: Int = 0,
    val totalTokens: Long = 0,
    val checkpointKey: String? = null,
    val checkpointDigest: String? = null,
    val messageId: Long? = null,
    val outcomeCode: String? = null,
    val expiresAt: Long? = null,
)

/** 一个模型或工具步骤的低频摘要，不接收流式正文及未经脱敏的异常文本。 */
@Entity(
    tableName = "agent_steps",
    foreignKeys = [ForeignKey(
        entity = AgentRun::class,
        parentColumns = ["id"],
        childColumns = ["runId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["runId", "ordinal"], unique = true), Index(
        value = ["requestId"],
        unique = true
    )],
)
data class AgentStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val ordinal: Int,
    val kind: String,
    val createdAt: Long,
    val requestId: String? = null,
    val completedAt: Long? = null,
    val estimatedTokens: Long = 0,
    val actualTokens: Long? = null,
    val outcomeCode: String? = null,
)

/** 客户端调用身份与协议关联 ID 分离，参数及结果用指纹绑定检查点。 */
@Entity(
    tableName = "tool_invocations",
    foreignKeys = [
        ForeignKey(
            entity = AgentRun::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AgentStep::class,
            parentColumns = ["id"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("runId"), Index(value = ["stepId", "ordinal"], unique = true)],
)
data class ToolInvocation(
    @PrimaryKey val id: String,
    val runId: String,
    val stepId: Long,
    val ordinal: Int,
    val remoteCallId: String?,
    val toolId: String,
    val contractVersion: Int,
    val definitionDigest: String,
    val argumentsDigest: String,
    val state: ToolInvocationState = ToolInvocationState.Prepared,
    val confirmationGranted: Boolean = false,
    val awaitingKind: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
    val resultDigest: String? = null,
    val checkpointKey: String? = null,
    val writeDisposition: String? = null,
)
