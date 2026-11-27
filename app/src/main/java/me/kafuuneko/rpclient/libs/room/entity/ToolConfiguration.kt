package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** HTTP 连接只保存非敏感配置与凭据引用，密钥不进入 Room。 */
@Entity(tableName = "tool_connections")
data class ToolConnection(
    @PrimaryKey val id: String,
    val name: String,
    val configJson: String,
    val enabled: Boolean = true,
)

/** 用户 HTTP 工具的稳定身份与配置；删除连接后保留工具并提示重新绑定。 */
@Entity(
    tableName = "tool_definitions",
    foreignKeys = [ForeignKey(
        entity = ToolConnection::class,
        parentColumns = ["id"],
        childColumns = ["connectionId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("connectionId"), Index(value = ["name"], unique = true)],
)
data class ToolDefinition(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val parametersJson: String,
    val mappingJson: String = "{}",
    val connectionId: String? = null,
    val kind: String = "Http",
    val enabled: Boolean = true,
)

/** 内置契约只允许稀疏覆写说明和可选默认参数，处理器与类型不可修改。 */
@Entity(tableName = "builtin_tool_overrides")
data class BuiltinToolOverride(
    @PrimaryKey val toolId: String,
    val configJson: String,
)
