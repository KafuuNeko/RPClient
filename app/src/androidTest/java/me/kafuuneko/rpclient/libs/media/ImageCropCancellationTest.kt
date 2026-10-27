package me.kafuuneko.rpclient.libs.media

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 使用真实描述符和阻塞管道覆盖裁剪加载的资源打开与各读取阶段取消。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ImageCropCancellationTest {
    /** 提供者尚未返回描述符时也必须收到取消信号，不能留下等待云端打开的任务。 */
    @Test
    fun cancellationStopsOpeningSource() = runBlocking {
        val opened = CompletableDeferred<Unit>()
        val cancelled = CountDownLatch(1)
        val provider = CropSourceProvider { signal ->
            signal?.setOnCancelListener { cancelled.countDown() }
            opened.complete(Unit)
            check(cancelled.await(5, TimeUnit.SECONDS)) { "Source open was not cancelled" }
            signal?.throwIfCanceled()
            null
        }
        val codec = ImageCropCodec(providerContext(provider))
        val task = async { codec.loadBitmapForCrop(mSourceUri) }
        try {
            // 先等提供者进入打开过程，再取消，避免只测试尚未启动的协程。
            withTimeout(5_000) { opened.await() }
            task.cancel()
            withTimeout(2_000) { task.join() }
            assertEquals(0L, cancelled.count)
            assertTrue(task.isCancelled)
        } finally {
            cancelled.countDown()
            task.cancel()
            withTimeout(5_000) { task.join() }
        }
    }

    /** 边界、EXIF 和像素读取都要主动关闭流，取消 EXIF 后不得继续打开像素流。 */
    @Test
    fun cancellationClosesEveryBlockedReadStage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("crop-cancel-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        try {
            // 前面的阶段读取正常文件，仅目标阶段使用始终不提供字节的管道。
            for (blockedStage in 1..3) {
                val pipe = ParcelFileDescriptor.createPipe()
                val opened = CompletableDeferred<Unit>()
                val openCount = AtomicInteger()
                val provider = CropSourceProvider {
                    val descriptor = if (openCount.incrementAndGet() == blockedStage) {
                        opened.complete(Unit)
                        pipe[0]
                    } else {
                        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    AssetFileDescriptor(descriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                }
                val codec = ImageCropCodec(providerContext(provider))
                val task = async { codec.loadBitmapForCrop(mSourceUri) }
                try {
                    withTimeout(5_000) { opened.await() }
                    assertNull(withTimeoutOrNull(100) { task.await() })
                    task.cancel()
                    withTimeout(2_000) { task.join() }
                    assertTrue(task.isCancelled)
                    assertEquals(blockedStage, openCount.get())
                    assertFalse("Read descriptor was not closed", pipe[0].fileDescriptor.valid())
                } finally {
                    // 失败时由测试解除阻塞，避免回归测试自身泄漏读取线程。
                    pipe[1].close()
                    task.cancel()
                    withTimeout(5_000) { task.join() }
                    runCatching { pipe[0].close() }
                }
            }
        } finally {
            source.delete()
        }
    }

    /**
     * 使用真实 ContentResolver 分发调用，保持生产路径的描述符包装与关闭行为。
     * @param provider 控制本次测试资源打开行为的提供者。
     * @return 仅替换内容解析器的应用上下文。
     */
    private fun providerContext(provider: ContentProvider): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = ContentResolver.wrap(provider)
        return object : ContextWrapper(base) {
            override fun getContentResolver(): ContentResolver = resolver
        }
    }

    /** 只提供图片读取入口，其余 ContentProvider 操作在裁剪流程中不会使用。 */
    private class CropSourceProvider(
        private val mOpen: (CancellationSignal?) -> AssetFileDescriptor?,
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String = "image/png"

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = mOpen(null)

        override fun openAssetFile(
            uri: Uri,
            mode: String,
            signal: CancellationSignal?,
        ): AssetFileDescriptor? = mOpen(signal)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        val mSourceUri: Uri = Uri.parse("content://crop-test/source")
    }
}
