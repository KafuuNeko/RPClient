package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 在隔离文件目录和真实 Room 中验证背景引用的提交、去重与回滚。 */
@RunWith(AndroidJUnit4::class)
class PageBackgroundRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var directory: File
    private lateinit var files: FileRepository
    private lateinit var backgrounds: PageBackgroundRepository

    /** 测试仅使用临时目录，不接触应用的已保存背景。 */
    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "background-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int): File = File(directory, name).apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        files = FileRepository(context, database)
        backgrounds = PageBackgroundRepository(database, files)
    }

    /** 关闭独立数据库并移除本测试文件。 */
    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    /** 两页使用同图时共享物理字节，但删除一页不能使另一页失效。 */
    @Test
    fun independentOwnersShareHashWithoutSharingDeletion() = runBlocking {
        val first = files.prepareStream("chat", ByteArrayInputStream("image".toByteArray()), "image/png")
        val second = files.prepareStream("group", ByteArrayInputStream("image".toByteArray()), "image/png")
        assertEquals(first.file.hash, second.file.hash)
        assertNotEquals(first.file.uuid, second.file.uuid)
        backgrounds.save(PageBackground(BackgroundPage.Chat), first)
        backgrounds.save(PageBackground(BackgroundPage.Group), second)
        // 恢复其中一页默认，只释放该页索引，共享物理文件仍能读取。
        backgrounds.save(PageBackground(BackgroundPage.Chat,
            topBarOpacity = 0.1f, bottomBarOpacity = 0.4f, bubbleOpacity = 0.7f), null)
        val withoutImage = backgrounds.observe(BackgroundPage.Chat).first()
        assertNull(withoutImage.fileUuid)
        assertEquals(0.1f, withoutImage.topBarOpacity)
        assertEquals(0.4f, withoutImage.bottomBarOpacity)
        assertEquals(0.7f, withoutImage.bubbleOpacity)
        assertNull(files.getFileEntity(first.file.uuid))
        assertEquals(second.file.uuid, backgrounds.observe(BackgroundPage.Group).first().fileUuid)
        files.withFileLease(second.file.uuid) { assertEquals("image", it.readText()) }
        backgrounds.save(PageBackground(BackgroundPage.Group), null)
        assertTrue(File(directory, "repository").listFiles().orEmpty().none { it.name == first.file.hash })
    }

    /** 未持有当前页索引的旧草稿不能覆盖配置，也不能删除其他页资源。 */
    @Test
    fun rejectedReferenceLeavesPreviousBackgroundIntact() = runBlocking {
        val prepared = files.prepareStream("story", ByteArrayInputStream("original".toByteArray()), "image/png")
        backgrounds.save(PageBackground(BackgroundPage.Story, blur = 73), prepared)
        val failure = runCatching {
            backgrounds.save(PageBackground(BackgroundPage.Story, fileUuid = "stale", blur = 0), null)
        }.exceptionOrNull()
        assertNotNull(failure)
        val restored = backgrounds.observe(BackgroundPage.Story).first()
        assertEquals(73, restored.blur)
        assertEquals(prepared.file.uuid, restored.fileUuid)
        files.withFileLease(prepared.file.uuid) { assertEquals("original", it.readText()) }
    }

    /** 替换背景成功后旧文件被回收，新图和全部参数一起生效。 */
    @Test
    fun replacementCommitsParametersAndReleasesOldIndex() = runBlocking {
        val first = files.prepareStream("draft", ByteArrayInputStream("old".toByteArray()), "image/png")
        val next = files.prepareStream("draft", ByteArrayInputStream("new".toByteArray()), "image/png")
        backgrounds.save(PageBackground(BackgroundPage.Chat), first)
        backgrounds.save(PageBackground(BackgroundPage.Chat, blur = 100, focalX = 0.2f,
            topBarBlur = 20, bottomBarBlur = 40, bubbleBlur = 60, contentBlur = 80), next)
        assertNull(files.getFileEntity(first.file.uuid))
        val settings = backgrounds.observe(BackgroundPage.Chat).first()
        assertEquals(100, settings.blur)
        assertEquals(0.2f, settings.focalX)
        assertEquals(20, settings.topBarBlur)
        assertEquals(40, settings.bottomBarBlur)
        assertEquals(60, settings.bubbleBlur)
        assertEquals(80, settings.contentBlur)
        files.withFileLease(next.file.uuid) { assertEquals("new", it.readText()) }
    }

    /** 栏位模糊在不透明时不读取源图，启用后只模糊指定栏位并保持全局底图清晰。 */
    @Test
    fun surfaceBlurSkipsOpaqueLayersAndKeepsBaseImageUnchanged() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = File(directory, "cache").apply { mkdirs() }
        }
        val runtime = BackgroundImageRuntime(context, files)
        val settings = PageBackground(BackgroundPage.Chat, fileUuid = "not-read-while-opaque",
            blur = 0, topBarBlur = 100, bottomBarBlur = 100, bubbleBlur = 100)
        // 无效引用也不会触发读取，证明不透明栏位在像素处理之前已被跳过。
        assertTrue(runtime.renderSurfaces(settings).isEmpty())
        val pixels = IntArray(256 * 256) { index ->
            if ((index % 256 / 4 + index / 256 / 4) % 2 == 0) Color.BLACK else Color.WHITE
        }
        val source = Bitmap.createBitmap(pixels, 256, 256, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use {
            source.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        source.recycle()
        val prepared = files.prepareStream("blur-test", ByteArrayInputStream(bytes), "image/png")
        try {
            val active = settings.copy(fileUuid = prepared.file.uuid, bubbleOpacity = 0.5f)
            val clear = requireNotNull(runtime.render(active, prepared, preview = true))
            val surfaces = runtime.renderSurfaces(active, prepared, preview = true)
            assertEquals(setOf(PageSurfaceKind.Bubble), surfaces.keys)
            val blurred = requireNotNull(surfaces[PageSurfaceKind.Bubble])
            val value = Color.red(blurred.getPixel(128, 128))
            assertTrue(value in 30..225)
            assertEquals(Color.BLACK, clear.getPixel(128, 128))
            assertTrue(runtime.renderSurfaces(active.copy(bubbleBlur = 0), prepared, preview = true).isEmpty())
        } finally { files.releasePrepared(prepared) }
    }

    /** 继承不复制文件索引，独立背景保留裁切参数，区域自定义和恢复默认可切换跟随。 */
    @Test
    fun generalInheritancePreservesIndependentImagesAndRawSettings() = runBlocking {
        val commonImage = files.prepareStream("general", ByteArrayInputStream("common".toByteArray()), "image/png")
        val ownImage = files.prepareStream("own", ByteArrayInputStream("own".toByteArray()), "image/png")
        val common = PageBackground(BackgroundPage.General, opacity = 0.4f, topBarOpacity = 0.2f,
            bottomBarOpacity = 0.6f, contentBlur = 60, topBarBlur = 20, bottomBarBlur = 40)
        backgrounds.save(common, commonImage)
        // 缺省页读取同一套区域值，但持久化原始配置仍为空，不能生成跨页文件引用。
        for (page in listOf(BackgroundPage.Chat, BackgroundPage.Group, BackgroundPage.Story)) {
            val inherited = backgrounds.observeEffective(page).first()
            assertEquals(commonImage.file.uuid, inherited.fileUuid)
            assertEquals(0.4f, inherited.bubbleOpacity)
            assertEquals(0.4f, inherited.opacity)
            assertEquals(60, inherited.bubbleBlur)
            assertEquals(60, inherited.contentBlur)
            assertNull(backgrounds.observe(page).first().fileUuid)
        }
        backgrounds.save(PageBackground(BackgroundPage.Chat, blur = 17, scrim = 0.1f, focalX = 0.2f), ownImage)
        val inheritedWithOwnImage = backgrounds.observeEffective(BackgroundPage.Chat).first()
        assertEquals(ownImage.file.uuid, inheritedWithOwnImage.fileUuid)
        assertEquals(17, inheritedWithOwnImage.blur)
        assertEquals(0.1f, inheritedWithOwnImage.scrim)
        assertEquals(0.2f, inheritedWithOwnImage.focalX)
        assertEquals(20, inheritedWithOwnImage.topBarBlur)
        // 单个非零区域设置也会保留整套独立配置；归零后重新读取当前通用值。
        val raw = backgrounds.observe(BackgroundPage.Chat).first()
        backgrounds.save(raw.copy(topBarBlur = 1), null)
        assertEquals(1f, backgrounds.observeEffective(BackgroundPage.Chat).first().bubbleOpacity)
        backgrounds.save(raw, null)
        val commonRaw = backgrounds.observe(BackgroundPage.General).first()
        backgrounds.save(commonRaw.copy(opacity = 0.7f), null)
        assertEquals(0.7f, backgrounds.observeEffective(BackgroundPage.Chat).first().bubbleOpacity)
        assertEquals(1f, backgrounds.observe(BackgroundPage.Chat).first().bubbleOpacity)
        // 删除通用图片不会影响独立图片的生命周期，也不会留下继承者的悬挂索引。
        backgrounds.save(commonRaw.copy(fileUuid = null), null)
        assertNull(backgrounds.observeEffective(BackgroundPage.Group).first().fileUuid)
        assertNull(files.getFileEntity(commonImage.file.uuid))
        files.withFileLease(ownImage.file.uuid) { assertEquals("own", it.readText()) }
    }

}
