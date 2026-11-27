package me.kafuuneko.rpclient.libs.agent.runtime

import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository

/**
 * 数据替换与身份删除的运行生命周期桥接，延迟取得管理器以避免仓库循环依赖。
 * - 任何数据库关闭之前先撤销执行资格并停止搜索工作器。
 * - 本机恢复载荷不进入备份，替换完成后全部清理。
 */
class AgentDataLifecycle(
    private val mManager: () -> AgentRunManager,
    private val mRuns: AgentRunRepository,
    private val mSearch: ContentSearchRepository,
    private val mConfigurations: ToolConfigRepository,
) {
    /** 暂存备份验证成功后调用，不能在 Room 事务或文件变更锁内等待运行退出。 */
    suspend fun beforeRestore() {
        mManager().stopAll()
        mSearch.stop()
    }

    /** 数据库替换成功后清理旧库 ID 对应的本机检查点。 */
    suspend fun afterRestore() {
        mConfigurations.invalidate()
        mManager().clearCheckpoints()
    }

    /** 删除人设前中止所有使用该实际身份的运行，覆盖跟随默认的会话。 */
    suspend fun beforePersonaDelete(personaId: Long) {
        mRuns.activeForPersona(personaId).forEach { mManager().stop(it) }
    }
}
