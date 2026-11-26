package me.kafuuneko.rpclient.libs.room.migration

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 注解之外的 Agent 跨字段约束与历史变更时钟。
 * - 单聊和群聊所有者互斥，物理外键负责删除生命周期。
 * - 普通消息编辑同样推进历史版本，避免 Agent 使用过期上下文提交。
 */
object AgentDatabaseConstraints : RoomDatabase.Callback() {
    /** 新建数据库在任何 Agent 写入前安装约束。 */
    override fun onCreate(db: SupportSQLiteDatabase) = install(db)

    /** 恢复后的库同样必须具备约束。 */
    override fun onOpen(db: SupportSQLiteDatabase) = install(db)

    /** 安装固定表名的幂等触发器，不接收用户可控 SQL 标识符。 */
    fun install(db: SupportSQLiteDatabase) {
        // 所有者约束及运行归属校验同时保护 Repository 之外的导入和迁移写入。
        listOf("INSERT", "UPDATE").forEach { action ->
            trigger(
                db, "policy", action, "conversation_tool_policies", """
                NOT ((NEW.chatId IS NOT NULL AND NEW.groupId IS NULL AND NEW.conversationKey = 'single/' || NEW.chatId)
                OR (NEW.groupId IS NOT NULL AND NEW.chatId IS NULL AND NEW.conversationKey = 'group/' || NEW.groupId))
                OR NEW.historyVersion < 0 OR NEW.executionEpoch < 0
            """.trimIndent()
            )
            trigger(
                db, "run", action, "agent_runs", """
                NEW.settingsVersion < 0 OR NEW.historyVersion < 0 OR NEW.executionEpoch < 0
                OR NEW.state NOT IN ('Preparing','RequestingModel','ExecutingTools','WaitingForUser','Committing','Cancelling','Completed','Failed','Cancelled','Interrupted','Expired')
                OR (NEW.state IN ('Completed','Failed','Cancelled','Interrupted','Expired') AND NEW.activeConversationKey IS NOT NULL)
                OR (NEW.state NOT IN ('Completed','Failed','Cancelled','Interrupted','Expired')
                    AND (NEW.activeConversationKey IS NULL OR NEW.activeConversationKey != NEW.conversationKey))
            """.trimIndent()
            )
            trigger(
                db, "invocation", action, "tool_invocations", """
                NEW.runId != (SELECT runId FROM agent_steps WHERE id = NEW.stepId)
                OR NEW.ordinal < 0 OR NEW.contractVersion < 1
                OR NEW.state NOT IN ('Prepared','Dispatching','AwaitingUser','Completed','Failed','UnknownOutcome')
                OR NEW.writeDisposition NOT IN ('Proposed','Committed')
            """.trimIndent()
            )
        }
        installHistoryClock(db, "chat", "single/")
        installHistoryClock(db, "group_chat", "group/")
    }

    private fun trigger(
        db: SupportSQLiteDatabase,
        name: String,
        action: String,
        table: String,
        invalid: String
    ) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS agent_${name}_${action.lowercase()}
            BEFORE $action ON $table WHEN $invalid
            BEGIN SELECT RAISE(ABORT, 'Invalid agent identity'); END
        """.trimIndent()
        )
    }

    private fun installHistoryClock(db: SupportSQLiteDatabase, family: String, prefix: String) {
        // 物理删除正文时同步移除其任务卡与调用索引，不能把旧消息 ID 关联到后续导入的正文。
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS agent_${family}_message_delete
            AFTER DELETE ON ${family}_messages
            BEGIN DELETE FROM agent_runs WHERE messageId = OLD.id
                AND conversationKey = '$prefix' || OLD.sessionId; END
        """.trimIndent()
        )
        // 更新消息时同时失效旧归属和新归属；普通路径无需读取或初始化 Agent 策略。
        listOf("INSERT", "UPDATE", "DELETE").forEach { action ->
            val owners = when (action) {
                "INSERT" -> "'$prefix' || NEW.sessionId"
                "DELETE" -> "'$prefix' || OLD.sessionId"
                else -> "'$prefix' || NEW.sessionId, '$prefix' || OLD.sessionId"
            }
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS agent_${family}_history_${action.lowercase()}
                AFTER $action ON ${family}_messages
                BEGIN UPDATE conversation_tool_policies SET historyVersion = historyVersion + 1
                WHERE conversationKey IN ($owners); END
            """.trimIndent()
            )
        }
    }
}
