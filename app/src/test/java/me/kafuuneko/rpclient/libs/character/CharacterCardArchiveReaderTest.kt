package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 用真实 ZIP 验证角色批量导入的限额、容错与临时文件清理。 */
class CharacterCardArchiveReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val mapper = CharacterCardMapper(Gson())
    private val mArchiveCache by lazy { temporary.newFolder("archives") }
    private val mAvatarStaging by lazy { temporary.newFolder("avatars") }

    /** 损坏卡片不阻止后续条目；普通附件和不安全路径不会成为角色。 */
    @Test
    fun malformedAndUnsafeEntriesDoNotBlockValidCards() = runBlocking {
        val archive = zip(
            "../unsafe.json" to card("Unsafe"),
            "image.png" to "not a PNG",
            "notes.txt" to "ignored",
            "broken.json" to "not json",
            "nested/valid.JSON" to card("Valid")
        )
        val result = read(archive)

        assertEquals(listOf("Valid"), result.drafts.map { it.card.character.name })
        assertEquals(2, result.failureCount)
        assertEquals(3, result.totalCount)
        assertCacheEmpty()
    }

    /** 混合归档同时保留 PNG 的头像与角色元数据，JSON 条目不生成头像。 */
    @Test
    fun mixedPngAndJsonArchiveStagesAvatarOnDisk() = runBlocking {
        // 使用真实元数据 PNG 组成混合归档，验证暂存文件删除后头像仍可保存。
        val png = CharacterCardPngCodec.writeCharacterJson(avatarPng(), card("猫🙂"))
        val result = read(
            zipBytes(
                "nested/avatar.PNG" to png,
                "without-avatar.json" to card("No avatar").toByteArray()
            )
        )

        assertEquals(listOf("猫🙂", "No avatar"), result.drafts.map { it.card.character.name })
        val staged = requireNotNull(result.drafts.first().stagedAvatar)
        assertArrayEquals(png, File(mAvatarStaging, staged.handle).readBytes())
        assertEquals("image/png", result.drafts.first().avatarMimeType)
        assertNull(result.drafts.last().stagedAvatar)
        assertEquals(0, result.failureCount)
        assertCacheEmpty()
    }

    /** PNG 图片字节与 JSON 一起计入预算，不能靠图片扩展名绕过解压大小限制。 */
    @Test
    fun pngBytesCountTowardEntryAndTotalLimits() = runBlocking {
        val png = CharacterCardPngCodec.writeCharacterJson(avatarPng(), card("Image"))
        val json = card("Text").toByteArray()
        val archive = zipBytes("image.png" to png, "text.json" to json)
        val exact = CharacterCardArchiveLimits(
            entryBytes = png.size,
            totalEntryBytes = png.size.toLong() + json.size
        )
        // 边界以内的混合包可恢复；单卡超限跳过，累计超限拒绝整包。
        assertEquals(2, read(archive, exact).drafts.size)
        val skipped = read(archive, exact.copy(entryBytes = png.size - 1))
        assertEquals(listOf("Text"), skipped.drafts.map { it.card.character.name })
        assertEquals(1, skipped.failureCount)
        assertTrue(runCatching {
            read(archive, exact.copy(totalEntryBytes = exact.totalEntryBytes - 1))
        }.isFailure)
        assertCacheEmpty()
    }

    /** 同包的清单、世界书和普通配置不会变成角色，旧版名称别名仍可导入。 */
    @Test
    fun metadataAndWorldbooksCannotBecomeCharacters() = runBlocking {
        val result = read(
            zip(
                "manifest.json" to """{"version":3}""",
                "book.json" to """{"name":"Book","description":"Lore","entries":{}}""",
                "package.json" to """{"name":"Bundle","version":"1","description":"Metadata"}""",
                "short.json" to """{"name":"Iris","description":"Character"}""",
                "settings.json" to """{"data":{"theme":"dark"}}""",
                "legacy.json" to """{"char_name":"Alice","char_greeting":"Hello"}""",
                "modern.json" to card("Bob")
            )
        )
        assertEquals(listOf("Iris", "Alice", "Bob"), result.drafts.map { it.card.character.name })
        assertEquals(4, result.failureCount)
        assertCacheEmpty()
    }

    /** 选错入口时，完整备份即使先遇到有效角色也不会返回可保存的草稿。 */
    @Test
    fun fullBackupIsRejectedRegardlessOfManifestOrder() = runBlocking {
        val result = read(
            zipBytes(
                "characters/Alice.png" to CharacterCardPngCodec.writeCharacterJson(
                    avatarPng(),
                    card("Alice")
                ),
                "manifest.json" to """{"format":"rpclient-portable-data","version":3}""".toByteArray()
            )
        )
        assertTrue(result.drafts.isEmpty())
        assertEquals(1, result.failureCount)
        assertTrue(mAvatarStaging.listFiles().orEmpty().isEmpty())
        assertCacheEmpty()
    }

    /** 整包在后续条目超限时，已经暂存的头像也必须回收。 */
    @Test
    fun archiveFailureReleasesPreviouslyStagedAvatars() = runBlocking {
        val png = CharacterCardPngCodec.writeCharacterJson(avatarPng(), card("Image"))
        val archive = zipBytes("image.png" to png, "text.json" to card("Text").toByteArray())
        val failure = runCatching {
            read(archive, CharacterCardArchiveLimits(totalEntryBytes = png.size.toLong()))
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("total entry size limit"))
        assertTrue(mAvatarStaging.listFiles().orEmpty().isEmpty())
        assertCacheEmpty()
    }

    /** 取消后续卡片解析时，前一张已完成的头像不能遗留。 */
    @Test
    fun cancellationReleasesPreviouslyStagedAvatars() = runBlocking {
        val png = CharacterCardPngCodec.writeCharacterJson(avatarPng(), card("Image"))
        var stageCount = 0
        val reader = reader(stageAvatar = { bytes ->
            if (++stageCount == 2) throw CancellationException("cancelled")
            stageAvatar(bytes)
        })
        val failure = runCatching {
            zipBytes("first.png" to png, "second.png" to png).inputStream().use {
                reader.read(it, mArchiveCache)
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(mAvatarStaging.listFiles().orEmpty().isEmpty())
        assertCacheEmpty()
    }

    /** 多字节正文按字节限制；伪造较小的 ZIP 声明大小也不能绕过实际读取上限。 */
    @Test
    fun oversizedEntryIsSkippedEvenWhenDeclaredSizeIsForged() = runBlocking {
        val archive = zip(
            "large.json" to card("Large", "猫".repeat(200)),
            "valid.json" to card("Valid")
        )
        val forged = archive.copyOf()
        // 只改中央目录首个条目的解压大小，保留真实压缩流以覆盖运行时计量。
        val centralHeader = forged.indices.first { index ->
            index + 4 <= forged.size && forged[index] == 0x50.toByte() &&
                forged[index + 1] == 0x4b.toByte() && forged[index + 2] == 0x01.toByte() &&
                forged[index + 3] == 0x02.toByte()
        }
        repeat(4) { forged[centralHeader + 24 + it] = 0 }
        forged[centralHeader + 24] = 1

        listOf(archive, forged).forEach { bytes ->
            val result = read(bytes, CharacterCardArchiveLimits(entryBytes = 256))
            assertEquals(listOf("Valid"), result.drafts.map { it.card.character.name })
            assertEquals(1, result.failureCount)
            assertEquals(2, result.totalCount)
            assertCacheEmpty()
        }
    }

    /** 单卡恰好达到上限仍能读取，累计上限也允许恰好等于实际字节数。 */
    @Test
    fun exactEntryAndTotalLimitsAreAccepted() = runBlocking {
        val json = card("Boundary", "你好🙂")
        val size = json.toByteArray(Charsets.UTF_8).size
        val result = read(
            zip("card.json" to json),
            CharacterCardArchiveLimits(entryBytes = size, totalEntryBytes = size.toLong())
        )

        assertEquals("Boundary", result.drafts.single().card.character.name)
        assertEquals(0, result.failureCount)
        assertCacheEmpty()
    }

    /** 解析失败的卡片也消耗累计预算，整包超限不能返回此前已解析的部分草稿。 */
    @Test
    fun totalLimitIncludesMalformedJsonAndRejectsWholeArchive() = runBlocking {
        val valid = card("Valid")
        val broken = "[".repeat(100)
        val archive = zip("first.json" to valid, "broken.json" to broken, "last.json" to valid)
        val limit = valid.toByteArray().size.toLong() * 2 + broken.length - 1

        val failure = runCatching {
            read(archive, CharacterCardArchiveLimits(totalEntryBytes = limit))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("total entry size limit"))
        assertCacheEmpty()
    }

    /** 缓存复制按压缩流实际大小受限，超限和条目数量超限都清理暂存文件。 */
    @Test
    fun compressedSizeAndEntryCountLimitsRejectArchive() = runBlocking {
        val archive = zip("card.json" to card("Valid"), "ignored.txt" to "ignored")
        val exact = CharacterCardArchiveLimits(archiveBytes = archive.size.toLong())
        assertEquals(1, read(archive, exact).drafts.size)
        assertTrue(runCatching {
            read(
                archive,
                exact.copy(archiveBytes = archive.size - 1L)
            )
        }.isFailure)
        assertTrue(runCatching { read(archive, exact.copy(entries = 1)) }.isFailure)
        assertCacheEmpty()
    }

    /** 复制阶段取消必须保留取消异常，不能吞掉异常或留下缓存文件。 */
    @Test
    fun cancellationRemainsCancellableAndRemovesTemporaryFile() = runBlocking {
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                throw CancellationException("cancelled")
            }
        }
        val failure = runCatching {
            input.use { reader().read(it, mArchiveCache) }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertCacheEmpty()
    }

    /** 经生产读取器执行 ZIP 解析，保持测试夹具与 Android URI 读取无关。 */
    private suspend fun read(
        archive: ByteArray,
        limits: CharacterCardArchiveLimits = CharacterCardArchiveLimits()
    ): CharacterCardArchiveReadResult = archive.inputStream().use {
        reader(limits).read(it, mArchiveCache)
    }

    /** 用真实磁盘暂存替代 Android 文件仓库，验证读取器的资源所有权。 */
    private fun reader(
        limits: CharacterCardArchiveLimits = CharacterCardArchiveLimits(),
        stageAvatar: suspend (ByteArray) -> PreparedFile = ::stageAvatar,
    ) = CharacterCardArchiveReader(
        mMapper = mapper,
        mStageAvatar = stageAvatar,
        mReleaseAvatar = { File(mAvatarStaging, it.handle).delete() },
        mLimits = limits,
    )

    /** 测试凭据只引用磁盘，避免测试本身重新持有全部 PNG 原图。 */
    private fun stageAvatar(bytes: ByteArray): PreparedFile {
        val file = File.createTempFile("avatar-", ".png", mAvatarStaging)
        file.writeBytes(bytes)
        return PreparedFile(
            ownerId = "test",
            handle = file.name,
            file = FileEntity(uuid = file.name, hash = file.name, mimeType = "image/png"),
            byteCount = file.length(),
        )
    }

    /** 构造包含实际压缩数据与中央目录的 ZIP。 */
    private fun zip(vararg entries: Pair<String, String>): ByteArray = zipBytes(
        *entries.map { (name, text) -> name to text.toByteArray(Charsets.UTF_8) }.toTypedArray()
    )

    /** 将二进制 PNG 与文本作为独立 ZIP 条目写入真实压缩流。 */
    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun avatarPng(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
    )

    /** 构造不含 Android 依赖的有效 V2 角色卡。 */
    private fun card(name: String, description: String = ""): String {
        return """{"spec":"chara_card_v2","data":{"name":"$name","description":"$description"}}"""
    }

    /** 成功、失败和取消都不能留下导入缓存。 */
    private fun assertCacheEmpty() {
        assertTrue(mArchiveCache.listFiles().orEmpty().isEmpty())
    }
}
