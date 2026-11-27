package me.kafuuneko.rpclient.libs.agent.connection

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonObject
import java.util.UUID
import me.kafuuneko.rpclient.libs.agent.tool.catalog.BuiltinToolRegistrations

/**
 * 整库替换前的 HTTP 配置校验边界。
 * - 与编辑器共享连接、工具、绑定关系和内置覆盖的完整契约校验。
 * - 只检查暂存数据库，不读取或恢复本机凭据正文。
 */
object ToolConfigRestoreValidator {
    /**
     * 与编辑器共享完整契约校验，非法 URL、明文认证、不兼容绑定或未知参数覆写均直接阻断恢复。
     *
     * @param db 替换在线库之前的暂存数据库。
     */
    fun validate(db: SupportSQLiteDatabase) {
        // 先校验并索引连接方法，供后续工具绑定兼容性校验复用。
        val connectionMethods = mutableMapOf<String, String>()
        db.query("SELECT id, name, enabled, configJson FROM tool_connections").use { rows ->
            while (rows.moveToNext()) {
                val id = rows.getString(0)
                require(
                    UUID.fromString(id).toString() == id && rows.getInt(2) in 0..1,
                ) { "Invalid HTTP connection metadata" }
                val value = JsonObject().apply {
                    addProperty("name", rows.getString(1))
                    add("config", ToolConfigCodec.parse(rows.getString(3)))
                }
                val connection = ToolConfigCodec.readConnection(value, id)
                connectionMethods[id] = HttpToolConfigCodec.connection(connection.configJson).method
            }
        }
        // 外键由 Room 和 foreign_key_check 校验；这里验证可执行契约、内置名称冲突与参数绑定兼容性。
        db.query("SELECT id, name, description, parametersJson, mappingJson, connectionId, kind, enabled FROM tool_definitions")
            .use { rows ->
                while (rows.moveToNext()) {
                    val id = rows.getString(0)
                    require(
                        UUID.fromString(id).toString() == id && rows.getInt(7) in 0..1,
                    ) { "Invalid HTTP tool metadata" }
                    val value = JsonObject().apply {
                        addProperty("name", rows.getString(1))
                        addProperty("description", rows.getString(2))
                        add("parameters", ToolConfigCodec.parse(rows.getString(3)))
                        add("mapping", ToolConfigCodec.parse(rows.getString(4)))
                        if (!rows.isNull(5)) addProperty("connectionId", rows.getString(5))
                        addProperty("kind", rows.getString(6))
                    }
                    val tool = ToolConfigCodec.readTool(value, id)
                    ToolConfigCodec.validateBindings(
                        tool,
                        tool.connectionId?.let(connectionMethods::get)
                    )
                }
            }
        // 按内置工具真实参数 Schema 完整校验稀疏覆盖，防止恢复后运行期加载崩溃。
        val catalog = BuiltinToolRegistrations.catalog
        db.query("SELECT toolId, configJson FROM builtin_tool_overrides").use { rows ->
            while (rows.moveToNext()) {
                val toolId = rows.getString(0)
                val specification =
                    requireNotNull(catalog.find(toolId)) { "Unknown built-in tool override" }.specification
                BuiltinToolOverrideCodec.validateForSchema(
                    id = toolId,
                    schema = specification.prompt.parameters,
                    editableDefaults = specification.editableDefaults,
                    value = ToolConfigCodec.parse(rows.getString(1)),
                )
            }
        }
    }
}
