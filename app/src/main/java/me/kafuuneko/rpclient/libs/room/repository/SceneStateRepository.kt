package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.model.SceneStateCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.SceneState
import me.kafuuneko.rpclient.libs.room.entity.SceneStateRevision

/**
 * 会话剧情状态及消息边界上的修订管理。
 * - 增量修改和撤销采用 expectedRevision，避免工具间丢失更新。
 * - 分支复制有效快照，删除消息时同步回滚，最近保留 200 个完整修订。
 */
class SceneStateRepository(
    private val mDatabase: AppDatabase,
    private val mRuns: AgentRunRepository
) {
    private val mDao = mDatabase.getSceneStateDao()

    /** 读取当前快照，不会为普通会话初始化 Agent 策略。 */
    suspend fun get(conversation: ConversationRef): SceneState =
        mDao.get(conversation.key) ?: SceneState(conversation.key)

    /** 读取保留窗口内的历史快照，调用方可据相邻版本生成差异。 */
    suspend fun revisions(conversation: ConversationRef): List<SceneStateRevision> =
        mDao.revisions(conversation.key)

    /**
     * 应用有界增量；用户编辑撤销当前运行，Agent 编辑必须持有有效执行令牌。
     * @param effectiveAfterMessageId 已提交的生效消息；0 表示会话起点。
     */
    suspend fun update(
        conversation: ConversationRef,
        expectedRevision: Long,
        changes: List<SceneStateChange>,
        effectiveAfterMessageId: Long,
        token: AgentExecutionToken? = null,
    ): SceneState {
        val current = get(conversation)
        val json = SceneStateCodec.apply(current.stateJson, changes)
        var result: SceneState? = null
        val commit: suspend () -> Unit = {
            val latest = get(conversation)
            check(latest.revision == expectedRevision && latest == current) { "Scene revision conflict" }
            validateBoundary(conversation, effectiveAfterMessageId)
            val previousBoundary =
                mDao.revisions(conversation.key).lastOrNull()?.effectiveAfterMessageId ?: 0
            check(effectiveAfterMessageId >= previousBoundary) { "Scene boundary cannot move backwards" }
            mRuns.policy(conversation)
            result = append(latest, json, effectiveAfterMessageId)
        }
        // JSON 转换与校验先在事务外执行，事务只复核版本和持久化。
        if (token != null) {
            require(token.conversation == conversation) { "Invalid scene execution owner" }
            mRuns.commitAuthorized(token, commit)
        } else mDatabase.withTransaction { mRuns.revoke(conversation); commit() }
        return requireNotNull(result)
    }

    /** 仅供已获执行器或正文提交事务授权的内置工具调用，不另行撤权或嵌套校验旧历史时钟。 */
    internal suspend fun applyAgentProposal(
        conversation: ConversationRef,
        expectedRevision: Long,
        changes: List<SceneStateChange>,
        boundary: Long
    ) {
        val current = get(conversation)
        check(current.revision == expectedRevision) { "Scene revision conflict" }
        validateBoundary(conversation, boundary)
        check(
            boundary >= (mDao.revisions(conversation.key).lastOrNull()?.effectiveAfterMessageId
                ?: 0)
        ) { "Scene boundary cannot move backwards" }
        append(current, SceneStateCodec.apply(current.stateJson, changes), boundary)
    }

    /** 在分支事务中复制策略与截断点有效状态，重映射消息边界。 */
    suspend fun copyBranch(
        source: ConversationRef,
        target: ConversationRef,
        messageIds: Map<Long, Long>
    ) = mDatabase.withTransaction {
        val policy = mDatabase.getAgentRunDao().policy(source.key) ?: return@withTransaction
        require(source::class == target::class && source != target) { "Invalid scene branch owner" }
        mDatabase.getAgentRunDao().insertPolicy(
            policy.copy(
                conversationKey = target.key,
                chatId = (target as? ConversationRef.Single)?.id,
                groupId = (target as? ConversationRef.Group)?.id,
                historyVersion = 0,
                executionEpoch = 0
            )
        )
        // 分支只继承消息前缀内已经生效的状态，不复制运行或协议检查点。
        val revision = mDao.revisions(source.key)
            .lastOrNull { it.effectiveAfterMessageId == 0L || it.effectiveAfterMessageId in messageIds }
            ?: return@withTransaction
        append(
            SceneState(target.key),
            revision.stateJson,
            messageIds[revision.effectiveAfterMessageId] ?: 0
        )
    }

    /** 删除消息前在同一事务撤权并回滚到截断点之前最近有效快照。 */
    suspend fun rollbackFrom(conversation: ConversationRef, boundary: Long) =
        mDatabase.withTransaction {
            val current = mDao.get(conversation.key) ?: return@withTransaction
            require(boundary > 0) { "Invalid scene rollback boundary" }
            val retained = mDao.revisions(conversation.key)
                .lastOrNull { it.effectiveAfterMessageId < boundary }
            // 删除后续修订，避免未来分支重新读到已经被截断的状态。
            mRuns.revoke(conversation)
            mDao.deleteFrom(conversation.key, boundary)
            append(current, retained?.stateJson ?: "{}", retained?.effectiveAfterMessageId ?: 0)
        }

    private suspend fun append(current: SceneState, json: String, boundary: Long): SceneState {
        val next = current.copy(
            revision = current.revision + 1,
            stateJson = json,
            updatedAt = System.currentTimeMillis()
        )
        if (mDao.get(current.conversationKey) == null) mDao.insert(next) else mDao.update(next)
        mDao.insertRevision(
            SceneStateRevision(
                conversationKey = next.conversationKey, revision = next.revision,
                effectiveAfterMessageId = boundary, stateJson = json, createdAt = next.updatedAt
            )
        )
        mDao.prune(next.conversationKey)
        return next
    }

    private suspend fun validateBoundary(conversation: ConversationRef, boundary: Long) {
        require(boundary >= 0) { "Invalid scene boundary" }
        if (boundary == 0L) return
        val valid = when (conversation) {
            is ConversationRef.Single -> mDatabase.getChatMessageDao()
                .getMessageById(boundary)
                ?.let { it.sessionId == conversation.id && it.source != ChatMessage.Source.Summary } == true

            is ConversationRef.Group -> mDatabase.getGroupChatMessageDao()
                .getMessageById(boundary)?.sessionId == conversation.id
        }
        require(valid) { "Scene boundary belongs to another conversation" }
    }
}
