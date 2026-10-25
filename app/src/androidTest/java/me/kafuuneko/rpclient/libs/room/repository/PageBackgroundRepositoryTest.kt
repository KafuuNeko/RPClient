package me.kafuuneko.rpclient.libs.room.repository

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.character.CharacterCardMapper
import me.kafuuneko.rpclient.libs.media.ImageCropCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.model.ImageCropSelection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

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
            override fun getDir(name: String, mode: Int): File =
                File(directory, name).apply { mkdirs() }
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
        val first =
            files.prepareStream("chat", ByteArrayInputStream("image".toByteArray()), "image/png")
        val second =
            files.prepareStream("group", ByteArrayInputStream("image".toByteArray()), "image/png")
        assertEquals(first.file.hash, second.file.hash)
        assertNotEquals(first.file.uuid, second.file.uuid)
        backgrounds.save(PageBackground(BackgroundPage.Chat), first)
        backgrounds.save(PageBackground(BackgroundPage.Group), second)
        // 恢复其中一页默认，只释放该页索引，共享物理文件仍能读取。
        backgrounds.save(
            value = PageBackground(
                page = BackgroundPage.Chat,
                topBarOpacity = 0.1f,
                bottomBarOpacity = 0.4f,
                bubbleOpacity = 0.7f,
            ),
            prepared = null,
        )
        val withoutImage = backgrounds.observe(BackgroundPage.Chat).first()
        assertNull(withoutImage.fileUuid)
        assertEquals(0.1f, withoutImage.topBarOpacity)
        assertEquals(0.4f, withoutImage.bottomBarOpacity)
        assertEquals(0.7f, withoutImage.bubbleOpacity)
        assertNull(files.getFileEntity(first.file.uuid))
        assertEquals(second.file.uuid, backgrounds.observe(BackgroundPage.Group).first().fileUuid)
        files.withFileLease(second.file.uuid) { file -> assertEquals("image", file.readText()) }
        backgrounds.save(value = PageBackground(BackgroundPage.Group), prepared = null)
        assertTrue(
            File(directory, "repository").listFiles().orEmpty()
                .none { fileValue -> fileValue.name == first.file.hash })
    }

    /** 未持有当前页索引的旧草稿不能覆盖配置，也不能删除其他页资源。 */
    @Test
    fun rejectedReferenceLeavesPreviousBackgroundIntact() = runBlocking {
        val prepared = files.prepareStream(
            "story",
            ByteArrayInputStream("original".toByteArray()),
            "image/png"
        )
        backgrounds.save(PageBackground(page = BackgroundPage.Story, blur = 73), prepared)
        val failure = runCatching {
            backgrounds.save(
                value = PageBackground(page = BackgroundPage.Story, fileUuid = "stale", blur = 0),
                prepared = null,
            )
        }.exceptionOrNull()
        assertNotNull(failure)
        val restored = backgrounds.observe(BackgroundPage.Story).first()
        assertEquals(73, restored.blur)
        assertEquals(prepared.file.uuid, restored.fileUuid)
        files.withFileLease(prepared.file.uuid) { file ->
            assertEquals(
                "original",
                file.readText()
            )
        }
    }

    /** 替换背景成功后旧文件被回收，新图和全部参数一起生效。 */
    @Test
    fun replacementCommitsParametersAndReleasesOldIndex() = runBlocking {
        val first =
            files.prepareStream("draft", ByteArrayInputStream("old".toByteArray()), "image/png")
        val next =
            files.prepareStream("draft", ByteArrayInputStream("new".toByteArray()), "image/png")
        backgrounds.save(PageBackground(BackgroundPage.Chat), first)
        backgrounds.save(
            value = PageBackground(
                page = BackgroundPage.Chat,
                blur = 100,
                focalX = 0.2f,
                topBarBlur = 20,
                bottomBarBlur = 40,
                bubbleBlur = 60,
                contentBlur = 80,
            ),
            prepared = next,
        )
        assertNull(files.getFileEntity(first.file.uuid))
        val settings = backgrounds.observe(BackgroundPage.Chat).first()
        assertEquals(100, settings.blur)
        assertEquals(0.2f, settings.focalX)
        assertEquals(20, settings.topBarBlur)
        assertEquals(40, settings.bottomBarBlur)
        assertEquals(60, settings.bubbleBlur)
        assertEquals(80, settings.contentBlur)
        files.withFileLease(next.file.uuid) { file -> assertEquals("new", file.readText()) }
    }

    /** 栏位模糊在不透明时不读取源图，启用后只模糊指定栏位并保持全局底图清晰。 */
    @Test
    fun surfaceBlurSkipsOpaqueLayersAndKeepsBaseImageUnchanged() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = File(directory, "cache").apply { mkdirs() }
        }
        val runtime = BackgroundImageRuntime(context, files)
        val settings = PageBackground(
            page = BackgroundPage.Chat,
            fileUuid = "not-read-while-opaque",
            blur = 0,
            topBarBlur = 100,
            bottomBarBlur = 100,
            bubbleBlur = 100,
        )
        // 无效引用也不会触发读取，证明不透明栏位在像素处理之前已被跳过。
        assertTrue(runtime.renderSurfaces(settings).isEmpty())
        val pixels = IntArray(256 * 256) { index ->
            if ((index % 256 / 4 + index / 256 / 4) % 2 == 0) Color.BLACK else Color.WHITE
        }
        val source = Bitmap.createBitmap(pixels, 256, 256, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { byteArrayOutputStream ->
            source.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            byteArrayOutputStream.toByteArray()
        }
        source.recycle()
        val prepared = files.prepareStream("blur-test", ByteArrayInputStream(bytes), "image/png")
        try {
            val active = settings.copy(fileUuid = prepared.file.uuid, bubbleOpacity = 0.5f)
            val clear = requireNotNull(
                runtime.render(
                    settings = active,
                    prepared = prepared,
                    preview = true
                )
            )
            val surfaces =
                runtime.renderSurfaces(settings = active, prepared = prepared, preview = true)
            assertEquals(setOf(PageSurfaceKind.Bubble), surfaces.keys)
            val blurred = requireNotNull(surfaces[PageSurfaceKind.Bubble])
            val value = Color.red(blurred.getPixel(128, 128))
            assertTrue(value in 30..225)
            assertEquals(Color.BLACK, clear.getPixel(128, 128))
            assertTrue(
                runtime.renderSurfaces(
                    settings = active.copy(bubbleBlur = 0),
                    prepared = prepared,
                    preview = true,
                ).isEmpty(),
            )
        } finally {
            files.releasePrepared(prepared)
        }
    }

    /** 裁剪预览可读取选中的源图，最终背景按同一矩形选区旋转后暂存。 */
    @Test
    fun cropPreviewAndBackgroundImportUseTheSameOrientedSelection() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getCacheDir(): File = File(directory, "crop-cache").apply { mkdirs() }
        }
        val runtime = BackgroundImageRuntime(context, files)
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        val sourceFile = File(directory, "crop-source.png")
        // 左右两种颜色可同时校验旋转方向和矩形宽高，避免只验证文件是否存在。
        for (y in 0 until source.height) {
            for (x in 0 until source.width) source.setPixel(
                x,
                y,
                if (x < 40) Color.RED else Color.BLUE
            )
        }
        sourceFile.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        val uri = Uri.fromFile(sourceFile)
        val preview = requireNotNull(ImageCropCodec(context).loadBitmapForCrop(uri))
        try {
            assertEquals(80, preview.width)
            assertEquals(40, preview.height)
            assertEquals(Color.RED, preview.getPixel(10, 10))
        } finally {
            preview.recycle()
        }
        val prepared = runtime.prepare(
            owner = "crop-test",
            uri = uri,
            selection = ImageCropSelection(0.5f, 0.5f, 0.5f, 1f, rotationDegrees = 90),
        )
        try {
            val output = requireNotNull(
                runtime.render(
                    settings = PageBackground(
                        BackgroundPage.General,
                        fileUuid = prepared.file.uuid,
                        blur = 0
                    ),
                    prepared = prepared,
                )
            )
            assertEquals(20, output.width)
            assertEquals(80, output.height)
            assertEquals(Color.RED, output.getPixel(10, 10))
            assertEquals(Color.BLUE, output.getPixel(10, 60))
        } finally {
            files.releasePrepared(prepared)
        }
    }

    /** 图片继承不复制索引；显式开关控制全部参数，关闭时全零参数也不会重新跟随。 */
    @Test
    fun generalInheritancePreservesIndependentImagesAndRawSettings() = runBlocking {
        val commonImage = files.prepareStream(
            "general",
            ByteArrayInputStream("common".toByteArray()),
            "image/png"
        )
        val ownImage =
            files.prepareStream("own", ByteArrayInputStream("own".toByteArray()), "image/png")
        val common = PageBackground(
            page = BackgroundPage.General,
            opacity = 0.4f,
            topBarOpacity = 0.2f,
            bottomBarOpacity = 0.6f,
            contentBlur = 60,
            topBarBlur = 20,
            bottomBarBlur = 40,
        )
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
        backgrounds.save(
            PageBackground(
                page = BackgroundPage.Chat,
                blur = 17,
                scrim = 0.1f,
                focalX = 0.2f
            ), ownImage
        )
        val inheritedWithOwnImage = backgrounds.observeEffective(BackgroundPage.Chat).first()
        assertEquals(ownImage.file.uuid, inheritedWithOwnImage.fileUuid)
        assertEquals(common.blur, inheritedWithOwnImage.blur)
        assertEquals(common.scrim, inheritedWithOwnImage.scrim)
        assertEquals(common.focalX, inheritedWithOwnImage.focalX)
        assertEquals(20, inheritedWithOwnImage.topBarBlur)
        // 关闭跟随时保留独立参数，即使区域值全零也不隐式重新跟随。
        val raw = backgrounds.observe(BackgroundPage.Chat).first()
        backgrounds.save(value = raw.copy(followGeneral = false), prepared = null)
        val independent = backgrounds.observeEffective(BackgroundPage.Chat).first()
        assertEquals(1f, independent.bubbleOpacity)
        assertEquals(17, independent.blur)
        assertEquals(0.2f, independent.focalX)
        assertEquals(
            false,
            PageBackgroundRepository(database, files).observe(BackgroundPage.Chat)
                .first().followGeneral,
        )
        backgrounds.save(value = raw, prepared = null)
        val commonRaw = backgrounds.observe(BackgroundPage.General).first()
        backgrounds.save(value = commonRaw.copy(opacity = 0.7f), prepared = null)
        assertEquals(0.7f, backgrounds.observeEffective(BackgroundPage.Chat).first().bubbleOpacity)
        assertEquals(1f, backgrounds.observe(BackgroundPage.Chat).first().bubbleOpacity)
        // 删除通用图片不会影响独立图片的生命周期，也不会留下继承者的悬挂索引。
        backgrounds.save(value = commonRaw.copy(fileUuid = null), prepared = null)
        assertNull(backgrounds.observeEffective(BackgroundPage.Group).first().fileUuid)
        assertNull(files.getFileEntity(commonImage.file.uuid))
        files.withFileLease(ownImage.file.uuid) { file -> assertEquals("own", file.readText()) }
    }

    /** 角色图片独占索引，启用和禁用开关都按角色、对话、通用顺序回退。 */
    @Test
    fun characterBackgroundPriorityAndDeletionPreserveSharedFiles() = runBlocking {
        val gson = Gson()
        val characters = CharacterRepository(
            mAppDatabase = database,
            mGson = gson,
            mRegexCodec = RegexScriptCodec(gson),
            mImages = MessageImageRepository(
                mDatabase = database,
                mFiles = files,
            ),
        )
        val character = CharacterCardMapper(gson).parseCharacter("""{"name":"Lyra"}""").character
        val common = files.prepareStream(
            "general",
            ByteArrayInputStream("shared".toByteArray()),
            "image/png"
        )
        val portrait = files.prepareStream(
            "character",
            ByteArrayInputStream("shared".toByteArray()),
            "image/png"
        )
        val chat =
            files.prepareStream("chat", ByteArrayInputStream("chat".toByteArray()), "image/png")
        backgrounds.save(PageBackground(page = BackgroundPage.General, opacity = 0.3f), common)
        backgrounds.save(
            PageBackground(page = BackgroundPage.Chat, useCharacterBackground = true),
            chat
        )
        val id = characters.saveCharacterWithAppearance(
            character = character,
            llmProviderId = 0,
            backgroundUuid = null,
            prepared = portrait,
        )
        assertEquals(portrait.file.uuid, backgrounds.observeChat(id).first().fileUuid)
        assertEquals(0.3f, backgrounds.observeChat(id).first().bubbleOpacity)
        // 禁用角色背景后使用对话图片；再清除对话图片，独立参数仍可配合通用图片。
        val raw = backgrounds.observe(BackgroundPage.Chat).first()
        backgrounds.save(value = raw.copy(useCharacterBackground = false), prepared = null)
        assertEquals(chat.file.uuid, backgrounds.observeChat(id).first().fileUuid)
        backgrounds.save(
            value = raw.copy(
                fileUuid = null,
                useCharacterBackground = false,
                followGeneral = false
            ),
            prepared = null,
        )
        assertEquals(common.file.uuid, backgrounds.observeChat(id).first().fileUuid)
        assertEquals(1f, backgrounds.observeChat(id).first().bubbleOpacity)
        backgrounds.save(value = raw.copy(fileUuid = null), prepared = null)
        assertEquals(portrait.file.uuid, backgrounds.observeChat(id).first().fileUuid)
        characters.deleteCharacter(id)
        assertNull(files.getFileEntity(portrait.file.uuid))
        assertEquals(common.file.uuid, backgrounds.observeChat(id).first().fileUuid)
        files.withFileLease(common.file.uuid) { file -> assertEquals("shared", file.readText()) }
    }

    /** 无效模型关联导致角色与图片一起回滚；同一草稿可重试，替换和移除会回收旧索引。 */
    @Test
    fun characterBackgroundRollbackReplacementAndRemovalAreAtomic() = runBlocking {
        val gson = Gson()
        val characters = CharacterRepository(
            mAppDatabase = database,
            mGson = gson,
            mRegexCodec = RegexScriptCodec(gson),
            mImages = MessageImageRepository(
                mDatabase = database,
                mFiles = files,
            ),
        )
        val character = CharacterCardMapper(gson).parseCharacter("""{"name":"Lyra"}""").character
        val first = files.prepareStream(
            "character",
            ByteArrayInputStream("first".toByteArray()),
            "image/png"
        )
        val id = characters.saveCharacterWithAppearance(
            character = character,
            llmProviderId = 0,
            backgroundUuid = null,
            prepared = first,
        )
        val saved = requireNotNull(characters.getCharacterById(id))
        val next = files.prepareStream(
            "character",
            ByteArrayInputStream("next".toByteArray()),
            "image/png"
        )
        try {
            val failure = runCatching {
                characters.saveCharacterWithAppearance(
                    character = saved.copy(name = "Changed"),
                    llmProviderId = Long.MAX_VALUE,
                    backgroundUuid = null,
                    prepared = next,
                )
            }
            assertTrue(failure.isFailure)
            assertEquals(saved, characters.getCharacterById(id))
            assertNull(files.getFileEntity(next.file.uuid))
            files.withFileLease(first.file.uuid) { file -> assertEquals("first", file.readText()) }
            // 重试只发布新的独占索引，旧图在成功事务之后才被删除。
            characters.saveCharacterWithAppearance(
                character = saved,
                llmProviderId = 0,
                backgroundUuid = null,
                prepared = next,
            )
            assertNull(files.getFileEntity(first.file.uuid))
            assertEquals(next.file.uuid, characters.getCharacterById(id)?.backgroundFileUuid)
            characters.saveCharacterWithAppearance(
                character = requireNotNull(characters.getCharacterById(id)),
                llmProviderId = 0,
                backgroundUuid = null,
                prepared = null,
            )
            assertNull(characters.getCharacterById(id)?.backgroundFileUuid)
            assertNull(files.getFileEntity(next.file.uuid))
        } finally {
            files.releasePrepared(next)
        }
    }

}
