package me.kafuuneko.rpclient.libs.agent.model

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 整库恢复前验证 Agent 元数据及剧情快照。
 * - 不依赖未导出的协议载荷及凭据；恢复时保留摘要但撤销执行资格。
 * - 所有检查先于在线库替换，异常信息仅使用固定诊断文本。
 */
object AgentRestoreValidator {
    /** 校验跨表身份、状态边界和快照；不修改库，供迁移及集成测试复用。 */
    fun validate(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check")
            .use { require(!it.moveToFirst()) { "Invalid agent foreign keys" } }
        // Room 的外键不能表达二选一所有者及运行内的调用归属。
        rejectRows(
            db, """
            SELECT 1 FROM conversation_tool_policies WHERE
            NOT ((chatId IS NOT NULL AND groupId IS NULL AND conversationKey = 'single/' || chatId)
            OR (groupId IS NOT NULL AND chatId IS NULL AND conversationKey = 'group/' || groupId))
            OR historyVersion < 0 OR executionEpoch < 0 OR maxToolRounds <= 0 OR maxToolCalls <= 0
            OR maxModelRequests <= 0 OR maxTotalTokens <= 0 OR toolTimeoutMillis <= 0 OR maxActiveMillis <= 0
            OR compactionWatermarkPercent NOT BETWEEN 1 AND 100 OR maxGroupModelRequests <= 0
            OR maxGroupTotalTokens <= 0 OR maxAutoBatches <= 0 OR maxGroupActiveMillis <= 0
        """.trimIndent()
        )
        db.query("SELECT enabledToolIdsJson FROM conversation_tool_policies").use { rows ->
            while (rows.moveToNext()) AgentPolicyValidator.enabledTools(rows.getString(0))
        }
        validateRuns(db)
        validateScenes(db)
    }

    /** 校验成功后撤销不可恢复的运行引用，整库备份不会携带本机检查点。 */
    fun prepareForRestore(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            validate(db)
            // 不将未备份的协议历史当作可续接状态，防止恢复后重发副作用。
            db.execSQL("UPDATE agent_runs SET state = 'Interrupted', activeConversationKey = NULL WHERE activeConversationKey IS NOT NULL")
            db.execSQL("UPDATE agent_runs SET checkpointKey = NULL, checkpointDigest = NULL, expiresAt = NULL")
            db.execSQL("UPDATE tool_invocations SET state = 'UnknownOutcome' WHERE state = 'Dispatching'")
            db.execSQL("UPDATE tool_invocations SET state = 'Failed' WHERE state IN ('Prepared', 'AwaitingUser')")
            db.execSQL("UPDATE tool_invocations SET checkpointKey = NULL, resultDigest = NULL")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun validateRuns(db: SupportSQLiteDatabase) {
        // 消息关联没有跨表外键，恢复时必须验证任务卡仍属于相同会话和助手回复。
        rejectRows(
            db, """
            SELECT 1 FROM agent_runs r JOIN conversation_tool_policies p USING (conversationKey)
            WHERE r.messageId IS NOT NULL AND
            ((p.chatId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.id = r.messageId AND m.sessionId = p.chatId AND m.source = 'Char'))
            OR (p.groupId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM group_chat_messages m WHERE m.id = r.messageId AND m.sessionId = p.groupId AND m.source = 'Character')))
        """.trimIndent()
        )
        rejectRows(
            db, """
            SELECT 1 FROM agent_runs WHERE state NOT IN
            ('Preparing','RequestingModel','ExecutingTools','WaitingForUser','Committing','Cancelling','Completed','Failed','Cancelled','Interrupted','Expired')
            OR (state IN ('Completed','Failed','Cancelled','Interrupted','Expired') AND activeConversationKey IS NOT NULL)
            OR (state NOT IN ('Completed','Failed','Cancelled','Interrupted','Expired')
                AND (activeConversationKey IS NULL OR activeConversationKey != conversationKey))
            OR settingsVersion < 0 OR historyVersion < 0 OR executionEpoch < 0 OR activeMillis < 0
            OR toolRounds < 0 OR toolCalls < 0 OR modelRequests < 0 OR totalTokens < 0
        """.trimIndent()
        )
        rejectRows(
            db, """
            SELECT 1 FROM agent_steps WHERE ordinal < 0 OR kind NOT IN ('Model','Tools','Compaction')
            OR estimatedTokens < 0 OR actualTokens < 0
        """.trimIndent()
        )
        rejectRows(
            db, """
            SELECT 1 FROM tool_invocations i JOIN agent_steps s ON s.id = i.stepId
            WHERE i.runId != s.runId OR i.ordinal < 0 OR i.contractVersion < 1
            OR i.state NOT IN ('Prepared','Dispatching','AwaitingUser','Completed','Failed','UnknownOutcome')
            OR i.writeDisposition NOT IN ('Proposed','Committed')
        """.trimIndent()
        )
        // 调用主键必须能由客户端身份重建，远程 callId 不参与去重。
        db.query("SELECT id, runId, stepId, ordinal, definitionDigest, argumentsDigest FROM tool_invocations")
            .use { rows ->
                while (rows.moveToNext()) {
                    require(
                        rows.getString(0) == AgentJson.digest(
                            "${rows.getString(1)}/${rows.getLong(2)}/${
                                rows.getInt(
                                    3
                                )
                            }"
                        ) &&
                            rows.getString(4).matches(Regex("[0-9a-f]{64}")) && rows.getString(5)
                            .matches(Regex("[0-9a-f]{64}"))
                    ) { "Invalid tool invocation fingerprint" }
                }
            }
    }

    private fun validateScenes(db: SupportSQLiteDatabase) {
        rejectRows(
            db, """
            SELECT 1 FROM scene_state_revisions r JOIN conversation_tool_policies p USING (conversationKey)
            WHERE r.revision <= 0 OR r.effectiveAfterMessageId < 0 OR (r.effectiveAfterMessageId > 0 AND
            ((p.chatId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.id = r.effectiveAfterMessageId AND m.sessionId = p.chatId))
            OR (p.groupId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM group_chat_messages m WHERE m.id = r.effectiveAfterMessageId AND m.sessionId = p.groupId))))
        """.trimIndent()
        )
        rejectRows(
            db, """
            SELECT 1 FROM scene_states s WHERE s.revision < 0 OR (s.revision = 0 AND s.stateJson != '{}')
            OR (s.revision > 0 AND NOT EXISTS (SELECT 1 FROM scene_state_revisions r
                WHERE r.conversationKey = s.conversationKey AND r.revision = s.revision AND r.stateJson = s.stateJson))
        """.trimIndent()
        )
        rejectRows(
            db, """
            SELECT 1 FROM scene_state_revisions r JOIN scene_states s USING (conversationKey)
            WHERE r.revision > s.revision
        """.trimIndent()
        )
        listOf("scene_states", "scene_state_revisions").forEach { table ->
            db.query("SELECT stateJson FROM $table").use { rows ->
                while (rows.moveToNext()) SceneStateCodec.normalize(rows.getString(0))
            }
        }
    }

    private fun rejectRows(db: SupportSQLiteDatabase, sql: String) {
        db.query(sql).use { require(!it.moveToFirst()) { "Invalid agent backup metadata" } }
    }
}
