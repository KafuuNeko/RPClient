package me.kafuuneko.rpclient.libs.character

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 用真实 ZIP 验证角色批量导入的限额、容错与临时文件清理。 */
class CharacterCardArchiveReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val mapper = CharacterCardMapper(Gson())

    /** 损坏 JSON 不阻止后续卡片；非 JSON 和不安全路径不会成为角色。 */
    @Test
    fun malformedAndUnsafeEntriesDoNotBlockValidCards() = runBlocking {
        val archive = zip(
            "../unsafe.json" to card("Unsafe"),
            "image.png" to "ignored",
            "broken.json" to "not json",
            "nested/valid.JSON" to card("Valid")
        )
        val result = read(archive)

        assertEquals(listOf("Valid"), result.drafts.map { it.card.character.name })
        assertEquals(1, result.failureCount)
        assertEquals(2, result.totalCount)
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
            zip(
                "characters/Alice.json" to card("Alice"),
                "manifest.json" to """{"format":"rpclient-portable-data","version":3}"""
            )
        )
        assertTrue(result.drafts.isEmpty())
        assertEquals(1, result.failureCount)
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
            CharacterCardArchiveLimits(entryBytes = size, totalJsonBytes = size.toLong())
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
            read(archive, CharacterCardArchiveLimits(totalJsonBytes = limit))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("total JSON size limit"))
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
            input.use { CharacterCardArchiveReader(mapper).read(it, temporary.root) }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertCacheEmpty()
    }

    /** 经生产读取器执行 ZIP 解析，保持测试夹具与 Android URI 读取无关。 */
    private suspend fun read(
        archive: ByteArray,
        limits: CharacterCardArchiveLimits = CharacterCardArchiveLimits()
    ): CharacterCardArchiveReadResult = archive.inputStream().use {
        CharacterCardArchiveReader(mapper, limits).read(it, temporary.root)
    }

    /** 构造包含实际压缩数据与中央目录的 ZIP。 */
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** 构造不含 Android 依赖的有效 V2 角色卡。 */
    private fun card(name: String, description: String = ""): String {
        return """{"spec":"chara_card_v2","data":{"name":"$name","description":"$description"}}"""
    }

    /** 成功、失败和取消都不能留下导入缓存。 */
    private fun assertCacheEmpty() {
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }
}
