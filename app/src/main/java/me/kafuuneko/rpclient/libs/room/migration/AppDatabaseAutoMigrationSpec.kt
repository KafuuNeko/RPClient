package me.kafuuneko.rpclient.libs.room.migration

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.llm.model.OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON

/**
 * 主业务数据库 v1→v2 自动迁移所需的删表消歧义声明。
 *
 * 历史请求日志可能包含升级前写入的原始载荷，因此允许 Room 删除旧日志表；
 * Room 根据已导出的 v1/v2 schema 生成迁移，其余业务表继续接受 schema 校验。v1 中本地
 * 新建世界书的默认预算 25 表示全局输入预算的 25%，v2 改用 0 表示跟随全局，因此迁移时
 * 同步转换旧默认值；其他显式预算值保持不变。
 */
@DeleteTable(tableName = "llm_request_logs")
class AppDatabaseAutoMigration1To2Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE lorebooks SET tokenBudget = 0 WHERE tokenBudget = 25")
    }
}

/**
 * 主业务数据库 v2→v3 完成 Regex、故事、角色模型关联表和高级请求 JSON 结构升级后的默认值迁移。
 *
 * 表和字段由 Room 按当前尚未发布的 v3 schema 自动创建；这里仅补写升级当下仍为空的
 * OpenRouter 模板。迁移结束后不再自动补写，因此用户可以从高级 JSON 删除 session_id
 * 并持续保持关闭状态。
 */
class AppDatabaseAutoMigration2To3Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE llm_providers
            SET requestBodyPatchJson = ?
            WHERE providerType = 'OpenRouter' AND requestBodyPatchJson = '{}'
            """.trimIndent(),
            arrayOf(OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON)
        )
    }
}

/**
 * 主业务数据库 v3→v4 为原生用量协议保留升级前的服务端统计行为。
 *
 * Gemini 与 Anthropic 不需要附加请求字段即可返回用量，因此可以安全保持启用；
 * OpenAI-compatible 端点能力差异较大，迁移后保持关闭，避免新增请求字段破坏兼容性。
 */
class AppDatabaseAutoMigration3To4Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE llm_providers
            SET useServerReportedUsage = 1
            WHERE protocol IN ('Gemini', 'AnthropicMessages')
            """.trimIndent()
        )
    }
}
