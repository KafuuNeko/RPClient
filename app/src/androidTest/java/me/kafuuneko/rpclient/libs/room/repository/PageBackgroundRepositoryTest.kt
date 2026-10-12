package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
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
        backgrounds.save(PageBackground(BackgroundPage.Chat, blur = 100, focalX = 0.2f), next)
        assertNull(files.getFileEntity(first.file.uuid))
        val settings = backgrounds.observe(BackgroundPage.Chat).first()
        assertEquals(100, settings.blur)
        assertEquals(0.2f, settings.focalX)
        files.withFileLease(next.file.uuid) { assertEquals("new", it.readText()) }
    }
}
