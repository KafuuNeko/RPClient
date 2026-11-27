package me.kafuuneko.rpclient.libs.agent.connection

import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailability
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailabilityState
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository

/** 配置就绪状态的单一解析入口，页面与执行工具共享连接及凭据判定。 */
class ToolConfigurationStatus(
    private val mConfigurations: ToolConfigRepository,
    private val mCredentials: ToolCredentialRepository,
) {
    /** @return 连接与凭据的非敏感就绪投影，每次解析只探测一次 Keystore。 */
    suspend fun connectionState(connection: ToolConnection?): ToolConnectionReadiness {
        val config =
            connection?.let { runCatching { HttpToolConfigCodec.connection(it.configJson) }.getOrNull() }
        val hasCredential = config?.credentialRef?.let { mCredentials.read(it) != null } == true
        val missing =
            config == null || config.url.isBlank() || (config.authMode != ToolAuthMode.None && !hasCredential)
        return ToolConnectionReadiness(config, hasCredential, missing)
    }

    /** @return 连接是否缺少有效目标或必需凭据。 */
    suspend fun connectionNeedsConfiguration(connection: ToolConnection?): Boolean =
        connectionState(connection).needsConfiguration

    /** @return 该工具是否尚未具备完整可用的连接；公网读取无需绑定。 */
    suspend fun needsConfiguration(tool: ToolDefinition, connection: ToolConnection?): Boolean =
        needsConfiguration(
            tool.kind,
            connection?.enabled == true,
            connectionNeedsConfiguration(connection)
        )

    /** @return 已解析连接投影对应的工具就绪状态，列表批量展示无需重复访问凭据。 */
    fun needsConfiguration(
        kind: String,
        connectionEnabled: Boolean,
        connectionMissing: Boolean
    ): Boolean =
        kind != HttpToolKind.ReadUrl && (!connectionEnabled || connectionMissing)

    /** @return 对冻结身份实时复核撤权及缺配置的组合策略。 */
    fun availability(tool: ToolDefinition, connection: ToolConnection?): ToolAvailability =
        ToolAvailability {
            when {
                !mConfigurations.permitted(tool, connection) -> ToolAvailabilityState.Revoked
                needsConfiguration(tool, connection) -> ToolAvailabilityState.NeedsConfiguration
                else -> ToolAvailabilityState.Ready
            }
        }
}

/** 配置就绪投影只保留凭据存在性，不持有凭据正文。 */
data class ToolConnectionReadiness(
    val configuration: HttpToolConnection?,
    val hasCredential: Boolean,
    val needsConfiguration: Boolean,
)
