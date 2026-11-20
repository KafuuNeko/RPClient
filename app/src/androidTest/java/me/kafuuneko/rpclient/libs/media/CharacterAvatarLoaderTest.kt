package me.kafuuneko.rpclient.libs.media

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** 验证真实解码、缓存命中及视口变化后的头像引用生命周期。 */
class CharacterAvatarLoaderTest {
    /** 滚动和关闭会清理离屏引用，重新进入视口可以复用原位图。 */
    @Test
    fun viewportUpdatesReleaseImagesAndReuseCachedBitmaps() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "avatar-loader-test-${System.nanoTime()}")
        val context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int): File =
                File(directory, name).apply { mkdirs() }
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // 文件和索引都使用独立存储，测试清理时不会影响应用已有的头像。
            val files = FileRepository(context, database)
            val uuids = mapOf(
                1L to files.saveFile(avatarPng(Color.RED), mimeType = "image/png"),
                2L to files.saveFile(avatarPng(Color.GREEN), mimeType = "image/png"),
                3L to files.saveFile(avatarPng(Color.BLUE), mimeType = "image/png"),
            )
            verifyAvatarViewport(files = files, uuids = uuids)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    /** 使用与 ViewModel 一致的主线程作用域验证头像回填。 */
    private suspend fun verifyAvatarViewport(files: FileRepository, uuids: Map<Long, String>) =
        withContext(Dispatchers.Main) {
            val avatars = MutableStateFlow<Map<Long, ImageBitmap>>(emptyMap())
            val loader = CharacterAvatarLoader(
                mFileRepository = files,
                mScope = this,
                mOnAvatarsUpdated = { avatars.value = it },
            )
            try {
                loader.registerCharacters(uuids)
                loader.updatePinnedCharacters(characterIds = setOf(1L), targetSizePx = 64)
                loader.updateVisibleCharacters(characterIds = setOf(2L), targetSizePx = 64)
                val first = withTimeout(5_000) { avatars.first { it.keys == setOf(1L, 2L) } }
                // 滚出视口必须立即回收发布快照里的引用，且常驻头像继续显示。
                loader.updateVisibleCharacters(characterIds = setOf(3L), targetSizePx = 64)
                assertEquals(setOf(1L), avatars.value.keys)
                withTimeout(5_000) { avatars.first { it.keys == setOf(1L, 3L) } }
                loader.clearVisibleCharacters()
                assertEquals(setOf(1L), avatars.value.keys)
                // 再次进入视口应同步命中缓存，复用底层 Android 位图。
                loader.updateVisibleCharacters(characterIds = setOf(2L), targetSizePx = 64)
                assertEquals(setOf(1L, 2L), avatars.value.keys)
                assertSame(
                    first.getValue(2L).asAndroidBitmap(),
                    avatars.value.getValue(2L).asAndroidBitmap(),
                )
            } finally {
                loader.clear()
            }
        }

    /** 生成用于文件仓库与下采样解码的最小 PNG 素材。 */
    private fun avatarPng(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                stream.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
