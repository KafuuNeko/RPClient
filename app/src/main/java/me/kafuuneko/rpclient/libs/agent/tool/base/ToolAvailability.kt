package me.kafuuneko.rpclient.libs.agent.tool.base

/** 缺配置可以修复，撤权必须终止运行；二者不混用错误级别。 */
enum class ToolAvailabilityState { Ready, NeedsConfiguration, Revoked }

/** 无调用状态的实时可用性策略，不替代执行器的会话授权。 */
fun interface ToolAvailability {
    /** @return 当前配置及全局权限状态。 */
    suspend fun state(): ToolAvailabilityState

    companion object {
        val Always = ToolAvailability { ToolAvailabilityState.Ready }
    }
}

/** 准备过程中发生撤权时，仍由执行器统一返回致命授权错误。 */
class ToolAuthorizationRevokedException : IllegalStateException("Tool authorization revoked")
