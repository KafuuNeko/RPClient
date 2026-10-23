package me.kafuuneko.rpclient.libs.utils

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 为阻塞 IO 注册独立的取消收尾，不能等待读取返回后才关闭资源。
 * - cleanup 在 IO 线程执行，允许关闭输入流或取消系统资源打开请求。
 * - 正常完成不执行 cleanup，资源仍由调用方的 use 关闭。
 */
internal suspend fun <T> withBlockingIoCancellation(
    cleanup: () -> Unit,
    block: suspend () -> T,
): T = coroutineScope {
    val owner = currentCoroutineContext()
    val cancellation = launch(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!owner.isActive) runCatching(cleanup)
        }
    }
    // 监视协程不依赖阻塞线程恢复；正常返回时也必须结束监视，避免作用域悬挂。
    try {
        block()
    } catch (error: Exception) {
        // 主动关闭可能表现为 IOException 或系统取消异常，取消语义优先于资源错误。
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        cancellation.cancel()
    }
}
