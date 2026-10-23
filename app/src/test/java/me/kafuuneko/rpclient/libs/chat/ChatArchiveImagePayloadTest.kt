package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Random
import java.util.zip.GZIPOutputStream

/** 保护压缩封装、原始摘要和 JSONL 向前引用协议，避免去重改变附件语义。 */
class ChatArchiveImagePayloadTest {
    private val codec = ChatArchiveCodec(Gson())

    /** 可压缩与不可压缩输入采用不同封装，但 hash 和恢复字节始终针对原文件。 */
    @Test
    fun compressionIsAdaptiveAndHashAlwaysDescribesOriginalBytes() {
        val compressible = ByteArray(size = 8192) { 42 }
        val random = ByteArray(8192).also { Random(7).nextBytes(it) }
        for ((bytes, expectedCompression) in listOf(compressible to "gzip", random to "none")) {
            val encoded = ChatArchiveImagePayload.encode(image(bytes))
            assertEquals(expectedCompression, encoded.compression)
            assertEquals(hash(bytes), encoded.hash)
            // 验证完整解码链路，不能只检查压缩字段或比较载荷长度。
            val restored = ByteArrayOutputStream()
            ChatArchiveImagePayload.open(encoded).use { input ->
                ChatArchiveImagePayload.copyOriginal(input, restored, encoded.hash)
            }
            assertArrayEquals(bytes, restored.toByteArray())
            if (expectedCompression == "gzip") {
                assertNotEquals(encoded.hash, hash(Base64.getDecoder().decode(encoded.data)))
            }
        }
    }

    /** 同消息内和后续消息共用一个顺序表，重复位置保留但载荷只写一次。 */
    @Test
    fun repeatedImagesUseOnlyHashesWithinAndAcrossMessagesWithoutVersionBump() {
        val first = image(ByteArray(size = 8192) { 1 })
        val second = image(byteArrayOf(3, 2, 1))
        val archive = codec.decode("{\"chat_metadata\":{}}", "Images").copy(
            messages = listOf(
                ChatArchiveMessage(
                    createTime = 1,
                    role = ChatArchiveMessageRole.User,
                    content = "",
                    images = listOf(
                        first,
                        second,
                        first,
                        first,
                    ),
                ),
                ChatArchiveMessage(
                    createTime = 2,
                    role = ChatArchiveMessageRole.Character,
                    content = "later",
                    images = listOf(
                        second,
                        first,
                    ),
                ),
            ),
        )
        val encoded = codec.encode(archive)
        val rows = encoded
            .lineSequence()
            .filter { it.isNotBlank() }
            .drop(1)
            .map { text ->
                JsonParser.parseString(text)
                    .asJsonObject
                    .getAsJsonObject("extra")
                    .getAsJsonObject("rpclient")
            }
            .toList()
        assertTrue(rows.all { it["schema_version"].asInt == 1 })
        val images =
            rows.flatMap { it.getAsJsonArray("images").map { value -> value.asJsonObject } }
        assertEquals(2, images.count { it.has("data") })
        assertTrue(images.drop(2).all { it.keySet() == setOf("hash") })
        // 导入后重复位置仍存在，并继承之前出现的完整资源描述。
        val restored = codec.decode(encoded, "Images").messages.flatMap { it.images }
        assertEquals(
            listOf(
                images[0]["hash"].asString,
                images[1]["hash"].asString,
                images[0]["hash"].asString,
                images[0]["hash"].asString,
                images[1]["hash"].asString,
                images[0]["hash"].asString,
            ),
            restored.map { it.hash },
        )
        assertEquals(restored[0], restored[2])
        assertEquals(encoded, codec.encode(codec.decode(encoded, "Images")))
    }

    /** 引用只能向前解析；不得用后续定义回填，也不得跨导入共享状态。 */
    @Test
    fun unresolvedReferencesStayMissingAndResolverStateIsPerArchive() {
        val hash = hash(byteArrayOf(1, 2, 3))
        val reference = """{"hash":"$hash"}"""
        val json = """{"chat_metadata":{}}
            {"mes":"","extra":{"rpclient":{"schema_version":1,"images":[$reference,{"hash":"$hash","mime_type":"image/png","compression":"none","data":"AQID"},$reference]}}}
            {"mes":"later","extra":{"rpclient":{"schema_version":1,"images":[$reference]}}}
        """.trimIndent()
        var staged = 0
        val archive = codec.decode(
            reader = json.reader(),
            fallbackTitle = "Images",
            transformImage = { chatArchiveImage ->
                staged++
                chatArchiveImage.copy(data = null, resourceKey = "resource-$staged")
            },
        )
        // 第一项缺失应保留给仓库计数；之后三个位置共享同一个暂存文件。
        val images = archive.messages.flatMap { it.images }
        assertEquals(1, staged)
        assertNull(images.first().resourceKey)
        assertTrue(images.drop(1).all { it.resourceKey == "resource-1" })
        val isolated =
            codec.decode("{\"chat_metadata\":{}}\n${json.lineSequence().last()}", "Other")
        assertNull(isolated.messages.single().images.single().data)
        assertNull(isolated.messages.single().images.single().resourceKey)
    }

    /** 摘要错配、未知压缩方式和截断 GZIP 必须报错，不允许进入文件引用表。 */
    @Test
    fun malformedPayloadsFailBeforeTheyCanBeUsedAsReferences() {
        // 分别覆盖内容摘要、传输算法和 GZIP 完整性三个失败边界。
        val encoded = ChatArchiveImagePayload.encode(image(ByteArray(8192)))
        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImagePayload.open(encoded).use { input ->
                ChatArchiveImagePayload.copyOriginal(input, discard(), "0".repeat(64))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImagePayload.open(encoded.copy(compression = "unknown"))
        }
        val gzip = Base64.getDecoder().decode(encoded.data)
        val truncated =
            encoded.copy(data = Base64.getEncoder().encodeToString(gzip.copyOf(gzip.size - 4)))
        assertThrows(IOException::class.java) {
            ChatArchiveImagePayload.open(truncated).use { input ->
                ChatArchiveImagePayload.copyOriginal(input, discard(), encoded.hash)
            }
        }
    }

    /** 很小的压缩载荷仍可能膨胀；上限必须按解压后的实际输出字节执行。 */
    @Test
    fun decompressedImageLimitIsEnforcedBeforeAcceptingHash() {
        // 构造超过单图上限但传输体积很小的载荷，避免测试只触发输入文件大小限制。
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { output ->
            val block = ByteArray(8192)
            repeat((MessageImagePolicy.MAX_ORIGINAL_BYTES / block.size).toInt() + 1) {
                output.write(
                    block
                )
            }
        }
        assertTrue(compressed.size() < 100_000)
        val image = ChatArchiveImage(
            mimeType = "image/png",
            data = Base64.getEncoder().encodeToString(compressed.toByteArray()),
            compression = "gzip",
        )
        assertThrows(IllegalArgumentException::class.java) {
            ChatArchiveImagePayload.open(image)
                .use { input -> ChatArchiveImagePayload.copyOriginal(input, discard()) }
        }
    }

    private fun image(bytes: ByteArray) =
        ChatArchiveImage("image/png", Base64.getEncoder().encodeToString(bytes))

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun discard() = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }
}
