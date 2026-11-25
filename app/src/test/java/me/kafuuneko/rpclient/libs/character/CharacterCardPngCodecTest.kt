package me.kafuuneko.rpclient.libs.character

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.util.Base64
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 通过独立 chunk 解析验证酒馆 PNG 的双版本元数据、像素保留与校验和。 */
class CharacterCardPngCodecTest {
    /** 大文本块跨多个缓冲区复制，读取边界不会随原始块的大小增长。 */
    @Test
    fun streamsLargeUnrelatedChunkAndReplacesOldMetadata() {
        val comment = "a".repeat(128 * 1024)
        val original = withTextChunk(png(), "Comment", comment)
        val oldJson = """{"name":"Old"}"""
        val source = withTextChunk(original, "chara", encode(oldJson))
        val stream = object : FilterInputStream(source.inputStream()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertTrue(length <= DEFAULT_BUFFER_SIZE)
                return super.read(buffer, offset, length)
            }
        }
        val target = ByteArrayOutputStream()
        val json = """{"name":"Current"}"""
        // 独立解析输出，确保跨缓冲区复制后仍能逐块通过 CRC 校验。
        CharacterCardPngCodec.writeCharacterJson(stream, target, json)
        val metadata = textChunks(target.toByteArray())
        assertEquals(comment, metadata["Comment"])
        assertEquals(json, decode(metadata.getValue("chara")))
    }

    /** 中文、换行与扩展字段应完整写入两个版本，图片像素及其他文本不受影响。 */
    @Test
    fun writesCompatibleMetadataWithoutChangingImageChunks() {
        val original = withTextChunk(png(), "Software", "RPClient fixture")
        val json =
            """{"spec":"chara_card_v2","spec_version":"2.0","data":{"name":"猫🙂","description":"a\nb","extensions":{"custom":true}}}"""
        val encoded = CharacterCardPngCodec.writeCharacterJson(original, json)

        // 独立读取所有块并校验 CRC，避免生产读写器的同一错误互相抵消。
        val before = chunks(original)
        val after = chunks(encoded)
        before.forEachIndexed { index, (type, bytes) ->
            val actualIndex = if (type == "IEND") after.lastIndex else index
            assertEquals(type, after[actualIndex].first)
            assertArrayEquals(bytes, after[actualIndex].second)
        }
        val text = textChunks(encoded)
        assertEquals(json, decode(text.getValue("chara")))
        val v3 = JsonParser.parseString(decode(text.getValue("ccv3"))).asJsonObject
        assertEquals("chara_card_v3", v3.get("spec").asString)
        assertEquals("3.0", v3.get("spec_version").asString)
        val data = v3.getAsJsonObject("data")
        assertTrue(data.get("group_only_greetings").isJsonArray)
        assertEquals(0, data.getAsJsonArray("group_only_greetings").size())
        val expectedData = JsonParser.parseString(json).asJsonObject.getAsJsonObject("data")
            .apply { add("group_only_greetings", JsonArray()) }
        assertEquals(expectedData, data)
        assertEquals(v3.toString(), CharacterCardPngCodec.readCharacterJson(encoded))
    }

    /** 重新导出 V3 卡时保留已有群聊开场白，不能用兼容空数组覆盖它们。 */
    @Test
    fun preservesExistingGroupOnlyGreetings() {
        val json = """{"data":{"name":"Group","group_only_greetings":["Hello, everyone!"]}}"""
        val encoded = CharacterCardPngCodec.writeCharacterJson(png(), json)
        val v3 = JsonParser.parseString(decode(textChunks(encoded).getValue("ccv3"))).asJsonObject
        assertEquals(JsonParser.parseString(json).asJsonObject.get("data"), v3.get("data"))
    }

    /** 缺少有效 data 的旧卡仅保留 chara，避免畸形 ccv3 抢占导入优先级。 */
    @Test
    fun malformedDataDoesNotProduceV3Metadata() {
        val inputs = listOf("""{"name":"Legacy"}""", """{"data":null}""", """{"data":[]}""")
        for (json in inputs) {
            val encoded = CharacterCardPngCodec.writeCharacterJson(png(), json)
            assertEquals(setOf("chara"), textChunks(encoded).keys)
            assertEquals(json, CharacterCardPngCodec.readCharacterJson(encoded))
        }
    }

    /** 重复导出替换旧的两套元数据，不能残留同名块或旧角色设定。 */
    @Test
    fun replacesExistingMetadataInsteadOfAppendingDuplicates() {
        val old = CharacterCardPngCodec.writeCharacterJson(png(), """{"data":{"name":"Old"}}""")
        val json = """{"data":{"name":"New"}}"""
        // 已经带双版本元数据的头像再次导出时，每种关键字只能保留一个最新块。
        val updated = CharacterCardPngCodec.writeCharacterJson(old, json)
        val texts = chunks(updated).filter { it.first == "tEXt" }

        assertEquals(2, texts.size)
        assertEquals(json, decode(textChunks(updated).getValue("chara")))
        assertEquals(
            "New",
            JsonParser.parseString(CharacterCardPngCodec.readCharacterJson(updated))
                .asJsonObject.getAsJsonObject("data").get("name").asString
        )
    }

    /** 老卡只带 chara 仍能导入，新卡同时带两者时优先使用 ccv3。 */
    @Test
    fun readsLegacyCharaAndPrefersCcv3() {
        val legacy = """{"name":"Legacy"}"""
        val modern = """{"data":{"name":"Modern"}}"""
        val old = withTextChunk(png(), "chara", encode(legacy))
        assertEquals(legacy, CharacterCardPngCodec.readCharacterJson(old))
        val both = withTextChunk(old, "ccv3", encode(modern))
        assertEquals(modern, CharacterCardPngCodec.readCharacterJson(both))
    }

    /** 标准 PNG 固定夹具不依赖 Android 位图实现。 */
    private fun png(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
    )

    /** 从标准二进制布局读取块并独立验证 CRC。 */
    private fun chunks(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val chunks = mutableListOf<Pair<String, ByteArray>>()
        DataInputStream(bytes.inputStream()).use { input ->
            input.skipBytes(8)
            // PNG 的 CRC 覆盖类型与正文，不包含长度字段。
            while (input.available() > 0) {
                val size = input.readInt()
                val type = ByteArray(4).also { input.readFully(it) }
                val data = ByteArray(size).also { input.readFully(it) }
                val crc = CRC32().apply { update(type); update(data) }.value
                assertEquals(crc, input.readInt().toLong() and 0xffffffffL)
                chunks += type.toString(Charsets.ISO_8859_1) to data
            }
        }
        return chunks
    }

    private fun textChunks(bytes: ByteArray): Map<String, String> = chunks(bytes)
        .filter { it.first == "tEXt" }
        .associate { (_, data) ->
            val parts = data.toString(Charsets.ISO_8859_1).split('\u0000', limit = 2)
            parts[0] to parts[1]
        }

    /** 独立构造文本块以覆盖读取兼容性，不使用生产写入器生成预期值。 */
    private fun withTextChunk(png: ByteArray, keyword: String, text: String): ByteArray {
        val output = ByteArrayOutputStream()
        val type = "tEXt".toByteArray(Charsets.ISO_8859_1)
        val data = "$keyword\u0000$text".toByteArray(Charsets.ISO_8859_1)
        // 在固定长度的 IEND 块之前插入文本，不改变已有图片内容。
        DataOutputStream(output).use {
            it.write(png, 0, png.size - 12)
            it.writeInt(data.size)
            it.write(type)
            it.write(data)
            it.writeInt(CRC32().apply { update(type); update(data) }.value.toInt())
            it.write(png, png.size - 12, 12)
        }
        return output.toByteArray()
    }

    private fun encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray())

    private fun decode(text: String): String =
        Base64.getDecoder().decode(text).toString(Charsets.UTF_8)
}
