package me.kafuuneko.rpclient.libs.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BlockingIoCancellationTest {
    @Test
    fun cancellationClosesResourceBeforeBlockedReadReturns() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val closed = CountDownLatch(1)
        val exited = AtomicBoolean(false)
        val worker = launch(Dispatchers.IO) {
            withBlockingIoCancellation({ closed.countDown() }) {
                entered.complete(Unit)
                // 模拟只响应 close、不主动检查协程状态的云端输入流。
                check(closed.await(5, TimeUnit.SECONDS))
                exited.set(true)
                throw IOException("Stream closed")
            }
        }
        try {
            withTimeout(timeMillis = 2_000) {
                entered.await()
                worker.cancelAndJoin()
            }
            assertTrue(exited.get())
        } finally {
            closed.countDown()
            worker.cancelAndJoin()
        }
    }

    @Test
    fun successfulReadDoesNotRunCancellationCleanup() = runBlocking {
        val closed = AtomicBoolean(false)
        withTimeout(timeMillis = 2_000) {
            withBlockingIoCancellation({ closed.set(true) }) { Unit }
        }
        assertFalse(closed.get())
    }
}
