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

/** 用真实 ZIP 验证世界书批量导入的限额、容错与临时文件清理。 */
class LorebookArchiveReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val codec = LorebookCodec(Gson())

    /** 损坏 JSON 不阻止后续世界书；非 JSON 和不安全路径不会成为世界书。 */
    @Test
    fun malformedAndUnsafeEntriesDoNotBlockValidBooks() = runBlocking {
        val archive = zip(
            "../unsafe.json" to book("Unsafe"),
            "image.png" to "ignored",
            "broken.json" to "not json",
            "nested/valid.JSON" to book("Valid")
        )
        val result = read(archive)

        assertEquals(listOf("Valid"), result.drafts.map { it.lorebook.name })
        assertEquals(1, result.failureCount)
        assertEquals(2, result.totalCount)
        assertCacheEmpty()
    }

    /** 角色卡、配置和错误格式不能被静默导入为空世界书。 */
    @Test
    fun unrelatedJsonCannotBecomeWorldBooks() = runBlocking {
        val result = read(
            zip(
                "manifest.json" to """{"version":3}""",
                "character.json" to """{"name":"Iris","description":"Character"}""",
                "array.json" to """{"name":"Wrong","entries":[]}""",
                "valid.json" to book("World"),
            )
        )
        assertEquals(listOf("World"), result.drafts.map { it.lorebook.name })
        assertEquals(3, result.failureCount)
        assertCacheEmpty()
    }

    /** 独立 JSON 与 ZIP 共用校验；重导入保留第三方字段和高级条目属性。 */
    @Test
    fun standaloneAndArchiveRoundTripPreserveEntryExtensions() = runBlocking {
        val source =
            """{"name":"世界","tokenBudget":50,"entries":{"8":{"comment":"规则","key":["猫"],"content":"正文","disable":true,"sticky":3,"custom":{"value":42}}}}"""
        val parsed = read(source.toByteArray()).drafts.single()
        val exported = codec.toLorebookJson(parsed.lorebook, parsed.entries)
        val restored = read(zip("世界.json" to exported)).drafts.single()
        assertEquals(50, restored.lorebook.tokenBudget)
        assertEquals("正文", restored.entries.single().content)
        assertEquals(3, restored.entries.single().sticky)
        assertTrue(restored.entries.single().disabled)
        assertTrue(restored.entries.single().rawJson.contains("custom"))
        assertTrue(runCatching { read("{}".toByteArray()) }.isFailure)
        assertCacheEmpty()
    }

    /** 空归档和仅包含附件的归档必须报告失败，不能误报成功。 */
    @Test
    fun emptyAndUnsupportedArchivesReportFailure() = runBlocking {
        listOf(zip(), zip("image.png" to "ignored")).forEach { bytes ->
            val result = read(bytes)
            assertTrue(result.drafts.isEmpty())
            assertEquals(1, result.failureCount)
        }
        assertCacheEmpty()
    }

    /** 选错入口时，完整备份即使先遇到有效世界书也不会返回可保存的草稿。 */
    @Test
    fun fullBackupIsRejectedRegardlessOfManifestOrder() = runBlocking {
        val result = read(
            zip(
                "worldbooks/Alice.json" to book("Alice"),
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
            "large.json" to book("Large", "猫".repeat(200)),
            "valid.json" to book("Valid")
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
            val result = read(bytes, LorebookArchiveLimits(entryBytes = 256))
            assertEquals(listOf("Valid"), result.drafts.map { it.lorebook.name })
            assertEquals(1, result.failureCount)
            assertEquals(2, result.totalCount)
            assertCacheEmpty()
        }
    }

    /** 单本恰好达到上限仍能读取，累计上限也允许恰好等于实际字节数。 */
    @Test
    fun exactEntryAndTotalLimitsAreAccepted() = runBlocking {
        val json = book("Boundary", "你好🙂")
        val size = json.toByteArray(Charsets.UTF_8).size
        val result = read(
            zip("book.json" to json),
            LorebookArchiveLimits(entryBytes = size, totalJsonBytes = size.toLong())
        )

        assertEquals("Boundary", result.drafts.single().lorebook.name)
        assertEquals(0, result.failureCount)
        assertCacheEmpty()
    }

    /** 解析失败的世界书也消耗累计预算，整包超限不能返回此前已解析的部分草稿。 */
    @Test
    fun totalLimitIncludesMalformedJsonAndRejectsWholeArchive() = runBlocking {
        val valid = book("Valid")
        val broken = "[".repeat(100)
        val archive = zip("first.json" to valid, "broken.json" to broken, "last.json" to valid)
        val limit = valid.toByteArray().size.toLong() * 2 + broken.length - 1

        val failure = runCatching {
            read(archive, LorebookArchiveLimits(totalJsonBytes = limit))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("total JSON size limit"))
        assertCacheEmpty()
    }

    /** 缓存复制按压缩流实际大小受限，超限和条目数量超限都清理暂存文件。 */
    @Test
    fun compressedSizeAndEntryCountLimitsRejectArchive() = runBlocking {
        val archive = zip("book.json" to book("Valid"), "ignored.txt" to "ignored")
        val exact = LorebookArchiveLimits(archiveBytes = archive.size.toLong())
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
        val input = object : ByteArrayInputStream(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) {
            private var reads = 0

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (reads++ > 0) throw CancellationException("cancelled")
                return super.read(bytes, offset, minOf(length, 4))
            }
        }
        val failure = runCatching {
            input.use { LorebookArchiveReader(codec).read(it, temporary.root) }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertCacheEmpty()
    }

    /** 经生产读取器执行 ZIP 解析，保持测试夹具与 Android URI 读取无关。 */
    private suspend fun read(
        archive: ByteArray,
        limits: LorebookArchiveLimits = LorebookArchiveLimits()
    ): LorebookImportBatch = archive.inputStream().use {
        LorebookArchiveReader(codec, limits).read(it, temporary.root)
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

    /** 构造不含 Android 依赖的有效独立世界书卡。 */
    private fun book(name: String, description: String = ""): String {
        return """{"name":"$name","description":"$description","entries":{}}"""
    }

    /** 成功、失败和取消都不能留下导入缓存。 */
    private fun assertCacheEmpty() {
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }
}
