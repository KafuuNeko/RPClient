package me.kafuuneko.rpclient.libs.agent.tool.base

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult

/** 仅映射已知本地领域失败，取消、撤权和未知系统错误继续上抛。 */
object ToolErrorMapper {
    /** @return 已知领域失败的稳定结果；未知异常保持原有失败语义。 */
    suspend fun read(block: suspend () -> ToolResult): ToolResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ToolAuthorizationRevokedException) {
        throw error
    } catch (error: ToolPreparationException) {
        ToolResult.RecoverableError(error.code)
    } catch (_: IllegalArgumentException) {
        ToolResult.RecoverableError(ToolErrorCode.INVALID_LOCAL_TOOL_REQUEST)
    } catch (_: IllegalStateException) {
        ToolResult.RecoverableError(ToolErrorCode.LOCAL_DATA_UNAVAILABLE_OR_CHANGED)
    }
}
