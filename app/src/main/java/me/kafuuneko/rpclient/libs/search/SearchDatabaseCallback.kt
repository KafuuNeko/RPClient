package me.kafuuneko.rpclient.libs.search

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.search.storage.SearchSourceContract

/**
 * 搜索维护入口与主库打开契约。
 * - 未确认开启时移除业务触发器；中断的构建转为待清理，不静默重建。
 * - 生命周期与触发器安装在同一事务提交，定义变化可更新已安装的同名触发器。
 * - 只隔离派生任务；主库损坏、空间耗尽或启用期间触发器写入失败仍可能影响业务事务。
 */
object SearchDatabaseCallback : RoomDatabase.Callback() {
    /** 打开时仅处理轻量状态与 DDL，大量派生数据交给管理器分批清理。 */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.beginTransaction()
        try {
            val phase = phase(db)
            val compatible = db.query("SELECT cursor FROM search_progress WHERE kind = -1").use {
                it.moveToFirst() && it.getLong(0) == SearchTextCodec.FORMAT_VERSION
            }
            // 已完成索引才恢复维护，丢失的检查点不能被误认为全量完成。
            val complete = db.query("SELECT count(*) FROM search_progress WHERE kind BETWEEN 1 AND 3 AND complete = 1").use {
                it.moveToFirst() && it.getInt(0) == 3
            }
            if (phase == SearchIndexPhase.Enabled && compatible && complete) {
                install(db)
            } else {
                uninstall(db)
                val clean = phase == null || phase == SearchIndexPhase.Disabled
                val remains = db.query("SELECT EXISTS(SELECT 1 FROM search_progress WHERE kind != -2) OR EXISTS(SELECT 1 FROM search_sources) OR EXISTS(SELECT 1 FROM search_chunks)").use {
                    it.moveToFirst() && it.getInt(0) != 0
                }
                setPhase(db, if (clean && !remains) SearchIndexPhase.Disabled else SearchIndexPhase.Cleaning)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 读取唯一权威开关；缺失表示旧自动索引实现，不能视为用户同意开启。 */
    internal fun phase(db: SupportSQLiteDatabase): SearchIndexPhase? =
        db.query("SELECT cursor FROM search_progress WHERE kind = -2").use {
            if (it.moveToFirst()) {
                val code = it.getLong(0)
                SearchIndexPhase.entries.firstOrNull { phase -> phase.code == code }
            } else null
        }

    /** 调用方必须与维护入口变更放在同一事务内。 */
    internal fun setPhase(db: SupportSQLiteDatabase, phase: SearchIndexPhase) {
        db.execSQL("INSERT OR REPLACE INTO search_progress(kind, cursor, complete) VALUES(-2, ?, 1)", arrayOf(phase.code))
    }

    /** 显式开启时初始化全部检查点，不能继承上次关闭前的修订号。 */
    internal fun beginBuild(db: SupportSQLiteDatabase) {
        for (kind in 0..3) {
            db.execSQL("INSERT INTO search_progress(kind, cursor, complete) VALUES(?, 0, 0)", arrayOf(kind))
        }
        db.execSQL("INSERT INTO search_progress(kind, cursor, complete) VALUES(-1, ?, 1)", arrayOf(SearchTextCodec.FORMAT_VERSION))
        setPhase(db, SearchIndexPhase.Building)
        install(db)
    }

    /** 校验实际定义再替换，重复打开无 DDL；安装失败由调用方事务完整回滚。 */
    private fun install(db: SupportSQLiteDatabase) {
        val installed = installedTriggers(db)
        val expected = definitions()
        // 清理被后续规则删除的旧名字，同时保护 Room 的 FTS 同步触发器。
        for (name in installed.keys - expected.keys) db.execSQL("DROP TRIGGER `$name`")
        for ((name, sql) in expected) {
            if (installed[name] == sql) continue
            db.execSQL("DROP TRIGGER IF EXISTS `$name`")
            db.execSQL(sql)
        }
    }

    /** 先停用业务维护再删派生数据，普通保存不会重新生成待办。 */
    internal fun uninstall(db: SupportSQLiteDatabase) {
        installedTriggers(db).keys.forEach { db.execSQL("DROP TRIGGER `$it`") }
    }

    /** 仅读取本模块拥有的名字，旧定义也可在关闭路径移除。 */
    private fun installedTriggers(db: SupportSQLiteDatabase): Map<String, String> =
        db.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND (name GLOB 'search_source_*' OR name GLOB 'search_meta_*')").use {
            buildMap { while (it.moveToNext()) put(it.getString(0), it.getString(1)) }
        }

    /** 所有原文表与查询依赖集中声明；字段实际变化才触发，兼容 Room 全字段 UPDATE。 */
    private fun definitions(): Map<String, String> = buildMap {
        for (source in SearchSourceContract.sources) {
            for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                val row = if (operation == "DELETE") "OLD" else "NEW"
                val deleted = if (operation == "DELETE") "1" else source.hidden
                val name = "search_source_${source.table}_$operation"
                val event = event(operation, source.columns)
                val guard = guard(operation, source.columns)
                // 外层 INSERT OR REPLACE 会覆盖内层冲突策略，必须先判断存在，不能依赖 OR IGNORE。
                put(name, """
                    CREATE TRIGGER `$name` AFTER $event ON `${source.table}` $guard
                    BEGIN
                        INSERT INTO search_sources(kind, sourceId, parentId, revision, indexedRevision, deleted, dirty, changedTime)
                            SELECT ${source.kind}, $row.id, $row.${source.parent}, 0, 0, $deleted, 1, 0
                            WHERE NOT EXISTS(SELECT 1 FROM search_sources WHERE kind = ${source.kind} AND sourceId = $row.id);
                        UPDATE search_sources SET parentId = $row.${source.parent}, revision = revision + 1, deleted = $deleted, dirty = 1,
                            changedTime = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)
                            WHERE kind = ${source.kind} AND sourceId = $row.id;
                        UPDATE search_progress SET cursor = cursor + 1 WHERE kind = 0;
                    END
                """.trimIndent())
            }
        }
        // 这些列覆盖标题、角色关联、结果排序与发言者标注，其他更新无需作废分页。
        for ((table, columns) in SearchSourceContract.metadataColumns) {
            for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                val name = "search_meta_${table}_$operation"
                put(name, """
                    CREATE TRIGGER `$name` AFTER ${event(operation, columns)} ON `$table` ${guard(operation, columns)}
                    BEGIN UPDATE search_progress SET cursor = cursor + 1 WHERE kind = 0; END
                """.trimIndent())
            }
        }
    }

    private fun event(operation: String, columns: List<String>): String =
        if (operation == "UPDATE") "UPDATE OF ${columns.joinToString()}" else operation

    private fun guard(operation: String, columns: List<String>): String =
        if (operation == "UPDATE") "WHEN " + columns.joinToString(" OR ") { "OLD.$it IS NOT NEW.$it" } else ""
}
