package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import me.kafuuneko.rpclient.libs.room.entity.BuiltinToolOverride
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition

/** 工具配置访问，跨表校验与导入事务统一由配置仓库负责。 */
@Dao
interface ToolConfigDao {
    /** 读取连接快照。 */
    @Query("SELECT * FROM tool_connections ORDER BY name, id")
    suspend fun connections(): List<ToolConnection>

    /** 读取工具快照。 */
    @Query("SELECT * FROM tool_definitions ORDER BY name, id")
    suspend fun tools(): List<ToolDefinition>

    /** 按稳定身份读取连接。 */
    @Query("SELECT * FROM tool_connections WHERE id = :id")
    suspend fun connection(id: String): ToolConnection?

    /** 按稳定身份读取工具。 */
    @Query("SELECT * FROM tool_definitions WHERE id = :id")
    suspend fun tool(id: String): ToolDefinition?

    /** 保存连接，不使用 REPLACE 触发外键删除。 */
    @Upsert
    suspend fun saveConnection(value: ToolConnection)

    /** 保存已校验的工具契约。 */
    @Upsert
    suspend fun saveTool(value: ToolDefinition)

    /** 删除连接，绑定工具转为待配置。 */
    @Query("DELETE FROM tool_connections WHERE id = :id")
    suspend fun deleteConnection(id: String)

    /** 删除工具，不回收历史运行中的稳定身份。 */
    @Query("DELETE FROM tool_definitions WHERE id = :id")
    suspend fun deleteTool(id: String)

    /** 读取内置工具的稀疏覆盖。 */
    @Query("SELECT * FROM builtin_tool_overrides ORDER BY toolId")
    suspend fun overrides(): List<BuiltinToolOverride>

    /** 保存内置工具覆盖。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOverride(value: BuiltinToolOverride)

    /** 恢复内置契约默认说明。 */
    @Query("DELETE FROM builtin_tool_overrides WHERE toolId = :id")
    suspend fun deleteOverride(id: String)
}
