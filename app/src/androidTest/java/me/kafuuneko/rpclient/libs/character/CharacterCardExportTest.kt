package me.kafuuneko.rpclient.libs.character

import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 用真实文件、位图与数据库验证导出格式、混合归档回退及头像恢复。 */
@RunWith(AndroidJUnit4::class)
class CharacterCardExportTest {
    private lateinit var mDirectory: File
    private lateinit var mContext: Context
    private lateinit var mDatabase: AppDatabase
    private lateinit var mFiles: FileRepository
    private lateinit var mCharacters: CharacterRepository
    private lateinit var mCards: CharacterCardRepository
    private val mGson = Gson()

    /** 使用独立内存数据库及文件目录，不修改应用已有角色或头像。 */
    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        mDirectory =
            File(base.cacheDir, "character-export-test-${System.nanoTime()}").apply { mkdirs() }
        mContext = object : ContextWrapper(base) {
            override fun getDir(name: String, mode: Int): File =
                File(mDirectory, name).apply { mkdirs() }

            override fun getCacheDir(): File = File(mDirectory, "cache").apply { mkdirs() }
        }
        // 仓库使用真实依赖，覆盖 URI、文件索引与角色元数据之间的集成行为。
        mDatabase = Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java).build()
        val codec = RegexScriptCodec(mGson)
        mFiles = FileRepository(mContext, mDatabase)
        mCharacters = CharacterRepository(
            mAppDatabase = mDatabase,
            mMemory = MemoryRepository(mDatabase),
            mGson = mGson,
            mRegexCodec = codec,
            mImages = MessageImageRepository(mDatabase, mFiles)
        )
        mCards = CharacterCardRepository(
            mContext = mContext,
            mGson = mGson,
            mCharacterRepository = mCharacters,
            mLorebookRepository = LorebookRepository(mDatabase, mGson, mContext),
            mFileRepository = mFiles,
            mRegexCodec = codec,
            mRegexRepository = RegexScriptRepository(mContext, mGson, mDatabase, codec)
        )
    }

    /** 成功或失败都移除测试文件，保持后续测试隔离。 */
    @After
    fun tearDown() {
        mDatabase.close()
        mDirectory.deleteRecursively()
    }

    /** JPEG 原头像转换为 PNG 后仍保持像素尺寸、内容与最新角色卡元数据。 */
    @Test
    fun singlePngConvertsAvatarAndEmbedsCurrentCharacterData() = runBlocking {
        val jpeg = avatar(Bitmap.CompressFormat.JPEG)
        val id = createCharacter("猫🙂", mFiles.saveFile(jpeg, "image/jpeg"))
        val target = File(mDirectory, "card.png")
        mCards.exportToUri(id, CharacterCardExportFormat.Png, Uri.fromFile(target))

        // 使用 Android 解码器读取最终文件，验证输出确实是可显示的 PNG 图片。
        val png = target.readBytes()
        assertTrue(CharacterCardPngCodec.isPng(png))
        val expected = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val actual = BitmapFactory.decodeByteArray(png, 0, png.size)
        try {
            assertEquals(expected.width, actual.width)
            assertEquals(expected.height, actual.height)
            assertEquals(expected.getPixel(1, 1), actual.getPixel(1, 1))
        } finally {
            expected.recycle()
            actual.recycle()
        }
        val document =
            JsonParser.parseString(CharacterCardPngCodec.readCharacterJson(png)).asJsonObject
        val original = JsonParser.parseString(mCards.exportJson(id)).asJsonObject
        // PNG 优先读取 V3 元数据，除原角色数据外还须包含新版要求的群聊开场白数组。
        val expectedData = original.getAsJsonObject("data")
            .apply { add("group_only_greetings", JsonArray()) }
        assertEquals(expectedData, document.get("data"))
    }

    /** 混合 PNG／JSON ZIP 可再次导入，重名角色与重复选择均按原有规则处理。 */
    @Test
    fun pngArchiveFallsBackToJsonAndRestoresAvatars() = runBlocking {
        val avatar = mFiles.saveFile(avatar(Bitmap.CompressFormat.PNG), "image/png")
        val first = createCharacter("Same", avatar)
        val second = createCharacter("Same", avatar)
        val withoutAvatar = createCharacter("Same")
        val target = File(mDirectory, "cards.zip")
        mCards.exportArchiveToUri(
            characterIds = listOf(first, second, withoutAvatar, first),
            format = CharacterCardExportFormat.Png,
            uri = Uri.fromFile(target)
        )
        // 归档中每张卡独立可用；未设置头像的条目必须拥有真实 JSON 扩展名及正文。
        ZipFile(target).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals(
                setOf("Same.png", "Same-$second-2.png", "Same.json"),
                entries.map { it.name }.toSet()
            )
            val json =
                zip.getInputStream(zip.getEntry("Same.json")).bufferedReader().use { it.readText() }
            assertEquals(
                "chara_card_v2",
                JsonParser.parseString(json).asJsonObject.get("spec").asString
            )
        }
        val imported = mCards.readImportItemsFromUri(Uri.fromFile(target))
        assertEquals(0, imported.failureCount)
        assertEquals(3, imported.drafts.size)
        assertEquals(2, imported.drafts.count { it.stagedAvatar != null })
        // 草稿保存后头像仍有有效文件索引，不能依赖已经删除的 ZIP 临时文件。
        for (draft in imported.drafts) {
            val expectedAvatar = draft.stagedAvatar?.let { prepared ->
                mFiles.withPreparedFile(prepared) { it.readBytes() }
            }
            val restoredId = mCards.saveImport(draft)
            val restored = requireNotNull(mCharacters.getCharacterById(restoredId))
            if (expectedAvatar == null) {
                assertTrue(restored.avatar.isEmpty())
            } else {
                assertArrayEquals(expectedAvatar, mFiles.getFile(restored.avatar)?.readBytes())
            }
        }
    }

    /** 写入角色失败时，头像索引和内嵌世界书随事务一并回滚。 */
    @Test
    fun failedCharacterInsertRollsBackStagedAvatarAndEmbeddedLorebook() = runBlocking {
        val avatar = avatar(Bitmap.CompressFormat.PNG).inputStream().use { input ->
            mFiles.prepareStream(ownerId = "import", input = input, mimeType = "image/png")
        }
        val parsed = CharacterCardMapper(mGson).parseCharacter("""{"name":"Failed import"}""")
        val draft = CharacterCardImportDraft(
            card = parsed.copy(
                embeddedLorebook = CharacterBookImport(
                    lorebook = Lorebook(name = "Embedded book"),
                    entries = emptyList(),
                ),
            ),
            avatarSourceUri = null,
            avatarMimeType = "image/png",
            stagedAvatar = avatar,
        )
        // 在真实数据库的最后一步拒绝写入，使此前发布的文件和世界书经过回滚路径。
        mDatabase.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_character_import BEFORE INSERT ON character
            BEGIN SELECT RAISE(ABORT, 'Import test failure'); END
            """.trimIndent(),
        )
        val result = runCatching { mCards.saveImport(draft) }
        assertTrue(result.exceptionOrNull() is SQLiteException)
        assertTrue(mDatabase.getLorebookDao().getAllLorebooks().isEmpty())
        assertEquals(0, mDatabase.getFileDao().countByHash(avatar.file.hash))
        assertFalse(
            File(
                mContext.getDir("repository", Context.MODE_PRIVATE),
                avatar.file.hash
            ).exists()
        )
    }

    /** 多个归档的待确认头像总量超过普通堆上限时，草稿仍只持有磁盘凭据并可整批释放。 */
    @Test
    fun pendingArchiveAvatarsDoNotAccumulateInHeap() = runBlocking {
        val png = CharacterCardPngCodec.writeCharacterJson(
            noisyAvatar(),
            """{"spec":"chara_card_v2","data":{"name":"Memory fixture","description":"Test card"}}""",
        )
        assertTrue(png.size < CharacterCardArchiveLimits().entryBytes)
        val archive = File(mDirectory, "large-avatars.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            repeat(4) { index ->
                zip.putNextEntry(ZipEntry("card-$index.png"))
                zip.write(png)
                zip.closeEntry()
            }
        }
        val drafts = mutableListOf<CharacterCardImportDraft>()
        try {
            // 复用同一归档夹具模拟连续读取，整批确认前保留所有返回草稿。
            repeat(9) {
                val result = mCards.readImportItemsFromUri(Uri.fromFile(archive))
                assertEquals(0, result.failureCount)
                assertEquals(4, result.drafts.size)
                drafts += result.drafts
            }
            val staged = drafts.map { requireNotNull(it.stagedAvatar) }
            assertTrue(staged.sumOf { it.byteCount } > 192L * 1024 * 1024)
            assertEquals(staged.size, staged.map { it.handle }.distinct().size)
            staged.forEach { prepared ->
                mFiles.withPreparedFile(prepared) { assertEquals(png.size.toLong(), it.length()) }
            }
        } finally {
            mCards.releaseImports(drafts)
        }
        // 释放暂存不依赖角色落库，也不需要重新把原图读回内存。
        drafts.mapNotNull { it.stagedAvatar }.forEach { prepared ->
            assertNull(mFiles.restorePrepared(prepared.ownerId, prepared.handle))
        }
    }

    /** 生成接近真实照片大小且难以压缩的 PNG，避免小色块掩盖原图累积的问题。 */
    private fun noisyAvatar(): ByteArray {
        val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        try {
            val random = Random(7)
            val pixels = IntArray(bitmap.width * bitmap.height) {
                0xff000000.toInt() or random.nextInt(0x1000000)
            }
            bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 显式选择 JSON 时，即使角色有头像，单卡与 ZIP 也只写入 JSON。 */
    @Test
    fun jsonSelectionNeverEmbedsAvatar() = runBlocking {
        val id = createCharacter(
            "With avatar",
            mFiles.saveFile(avatar(Bitmap.CompressFormat.PNG), "image/png")
        )
        val single = File(mDirectory, "card.json")
        mCards.exportToUri(id, CharacterCardExportFormat.Json, Uri.fromFile(single))
        val archive = File(mDirectory, "cards.zip")
        mCards.exportArchiveToUri(listOf(id), CharacterCardExportFormat.Json, Uri.fromFile(archive))

        // 对比两种导出容器中的正文，确认显式 JSON 选择不受头像存在与否影响。
        ZipFile(archive).use { zip ->
            val entry = zip.entries().nextElement()
            assertTrue(entry.name.endsWith(".json"))
            assertArrayEquals(single.readBytes(), zip.getInputStream(entry).use { it.readBytes() })
        }
        assertFalse(CharacterCardPngCodec.isPng(single.readBytes()))
    }

    /** 已设置但不可用的头像不能伪装成成功 PNG，失败前不得覆盖已有目标内容。 */
    @Test
    fun invalidAvatarDoesNotProducePlaceholderOrOverwriteTarget() = runBlocking {
        val invalid = mFiles.saveFile("invalid image".toByteArray(), "image/jpeg")
        val ids =
            listOf(createCharacter("Missing", "missing-file"), createCharacter("Broken", invalid))
        val target = File(mDirectory, "card.png").apply { writeText("original") }
        // 分别覆盖文件缺失和解码失败，两种错误都不应创建一像素占位角色卡。
        for (id in ids) {
            assertTrue(runCatching {
                mCards.exportToUri(id, CharacterCardExportFormat.Png, Uri.fromFile(target))
            }.isFailure)
            assertEquals("original", target.readText())
        }
    }

    /** 通过角色映射器提供有效的卡片字段，使测试聚焦导出行为。 */
    private suspend fun createCharacter(name: String, avatar: String = ""): Long {
        val character =
            CharacterCardMapper(mGson).parseCharacter("""{"name":"$name","description":"Fixture"}""").character
        return mCharacters.saveCharacter(character.copy(avatar = avatar))
    }

    /** 构造可验证像素的真实图片，PNG 与 JPEG 共用相同头像内容。 */
    private fun avatar(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.CYAN)
            return ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(format, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
