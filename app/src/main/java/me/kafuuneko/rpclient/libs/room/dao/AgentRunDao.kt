package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.room.entity.AgentRun
import me.kafuuneko.rpclient.libs.room.entity.AgentStep
import me.kafuuneko.rpclient.libs.room.entity.ConversationToolPolicy
import me.kafuuneko.rpclient.libs.room.entity.ToolInvocation

/** Agent 元数据访问；授权、时钟推进及跨表事务由 Repository 统一处理。 */
@Dao
interface AgentRunDao {
    /** 读取工具策略及提交时钟。 */
    @Query("SELECT * FROM conversation_tool_policies WHERE conversationKey = :key")
    suspend fun policy(key: String): ConversationToolPolicy?

    /** 创建策略，冲突不得 REPLACE 以免级联删除运行记录。 */
    @Insert
    suspend fun insertPolicy(value: ConversationToolPolicy)

    /** 更新已经过仓库校验的策略。 */
    @Update
    suspend fun updatePolicy(value: ConversationToolPolicy)

    /** 读取单个运行。 */
    @Query("SELECT * FROM agent_runs WHERE id = :id")
    suspend fun run(id: String): AgentRun?

    /** 读取会话当前占有者，包括等待用户的运行。 */
    @Query("SELECT * FROM agent_runs WHERE activeConversationKey = :key")
    suspend fun activeRun(key: String): AgentRun?

    /** 观察低频运行摘要，原始协议载荷不经过此流。 */
    @Query("SELECT * FROM agent_runs WHERE conversationKey = :key ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun observeRuns(key: String): Flow<List<AgentRun>>

    /** 查询待恢复或待收尾的运行。 */
    @Query("SELECT * FROM agent_runs WHERE activeConversationKey IS NOT NULL")
    suspend fun activeRuns(): List<AgentRun>

    /** 本机检查点仅为一天内等待和七天内中断运行保留。 */
    @Query("SELECT * FROM agent_runs WHERE (state = 'WaitingForUser' AND expiresAt > :now) OR (state = 'Interrupted' AND updatedAt > :since)")
    suspend fun recoverableRuns(now: Long, since: Long): List<AgentRun>

    /** 读取同批次累计成本，不把后继群成员当作全新根限额。 */
    @Query("SELECT * FROM agent_runs WHERE batchId = :batchId AND conversationKey = :key")
    suspend fun batchRuns(batchId: String, key: String): List<AgentRun>

    /** 插入独占运行，数据库唯一索引阻止同会话并发准入。 */
    @Insert
    suspend fun insertRun(value: AgentRun)

    /** 更新事务内已复核资格的运行摘要。 */
    @Update
    suspend fun updateRun(value: AgentRun)

    /** 读取步骤并核对调用归属。 */
    @Query("SELECT * FROM agent_steps WHERE id = :id")
    suspend fun step(id: Long): AgentStep?

    /** 读取同一运行的步骤摘要。 */
    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY ordinal")
    suspend fun steps(runId: String): List<AgentStep>

    /** 分配运行内单调步骤序号。 */
    @Query("SELECT COALESCE(MAX(ordinal), -1) + 1 FROM agent_steps WHERE runId = :runId")
    suspend fun nextStepOrdinal(runId: String): Int

    /** 插入步骤，序号与 requestId 冲突时拒绝重复记账。 */
    @Insert
    suspend fun insertStep(value: AgentStep): Long

    /** 完成步骤账本，运行仓库按 requestId 只核销一次。 */
    @Update
    suspend fun updateStep(value: AgentStep)

    /** 读取运行的全部有序调用，用于恢复和提案投影。 */
    @Query("SELECT * FROM tool_invocations WHERE runId = :runId ORDER BY stepId, ordinal")
    suspend fun invocations(runId: String): List<ToolInvocation>

    /** 读取幂等调用元数据。 */
    @Query("SELECT * FROM tool_invocations WHERE id = :id")
    suspend fun invocation(id: String): ToolInvocation?

    /** 查询运行内尚未定案的调用。 */
    @Query("SELECT * FROM tool_invocations WHERE runId = :runId AND state = 'Dispatching'")
    suspend fun dispatchingInvocations(runId: String): List<ToolInvocation>

    /** 插入派发标记，必须在产生随机结果或副作用前提交。 */
    @Insert
    suspend fun insertInvocation(value: ToolInvocation)

    /** 更新调用结果引用，不保存参数与结果正文。 */
    @Update
    suspend fun updateInvocation(value: ToolInvocation)
}
