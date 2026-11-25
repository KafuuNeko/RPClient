package me.kafuuneko.rpclient.libs.character

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32

/**
 * PNG 角色卡元数据编解码器。
 *
 * 仅重写角色卡相关的 tEXt chunk，其他图片块按原顺序保留；写入时重新计算 CRC，
 * 从而在不重新编码图片像素的情况下兼容 chara 与 ccv3 读取方。
 */
object CharacterCardPngCodec {
    private const val TEXT_CHUNK = 0x74455874
    private const val END_CHUNK = 0x49454e44
    private const val MAX_TEXT_KEYWORD_PREFIX = 80
    private val mCardKeywords = setOf("chara", "ccv3")
    private val mPngSignature = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )

    /**
     * 从 PNG tEXt chunk 中读取角色卡 JSON。
     *
     * 新格式优先读取 ccv3，兼容读取 SillyTavern 常见的 chara 字段。
     * @param bytes 原始 PNG。
     * @return 角色卡 JSON。
     */
    fun readCharacterJson(bytes: ByteArray): String {
        val textChunks = readTextChunks(bytes)
        val ccv3 = textChunks.firstOrNull { it.keyword.equals(other = "ccv3", ignoreCase = true) }
        if (ccv3 != null) return decodeBase64Text(ccv3.text)
        val chara = textChunks.firstOrNull { it.keyword.equals(other = "chara", ignoreCase = true) }
        if (chara != null) return decodeBase64Text(chara.text)
        error("PNG metadata does not contain character card data")
    }

    /**
     * 在内存中的 PNG 夹具或小图像上写入角色卡元数据。
     * @param bytes 原始 PNG。
     * @param json 最新角色卡 JSON。
     * @return 保留原图且包含新元数据的 PNG。
     */
    fun writeCharacterJson(bytes: ByteArray, json: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            writeCharacterJson(input = bytes.inputStream(), output = output, json = json)
            output.toByteArray()
        }

    /**
     * 逐块重写角色元数据，图片数据始终通过固定大小缓冲区复制。
     * @param input 原始 PNG 输入流，由调用方关闭。
     * @param output PNG 输出流，由调用方负责缓冲与关闭。
     * @param json 最新角色卡 JSON。
     * @param checkActive 每次复制前调用，用于响应导出取消。
     */
    internal fun writeCharacterJson(
        input: InputStream,
        output: OutputStream,
        json: String,
        checkActive: () -> Unit = {},
    ) {
        val source = DataInputStream(input)
        val target = DataOutputStream(output)
        val signature = ByteArray(mPngSignature.size).also { source.readFully(it) }
        require(isPng(signature)) { "Invalid PNG signature" }
        target.write(signature)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val prefix = ByteArray(MAX_TEXT_KEYWORD_PREFIX)

        // 只查看文本块的关键词，像素和其他元数据沿用原有顺序。
        while (true) {
            checkActive()
            val length = source.readInt()
            require(length >= 0) { "Invalid PNG chunk length" }
            val type = source.readInt()
            val prefixLength = if (type == TEXT_CHUNK) minOf(length, prefix.size) else 0
            source.readFully(prefix, 0, prefixLength)
            val replace = isCardText(prefix, prefixLength)
            if (type == END_CHUNK) writeMetadata(target, json)

            // 保留块沿用原 CRC。旧角色信息连同其 CRC 一起跳过。
            if (!replace) {
                target.writeInt(length)
                target.writeInt(type)
                target.write(prefix, 0, prefixLength)
            }
            var remaining = length - prefixLength
            while (remaining > 0) {
                checkActive()
                val count = minOf(remaining, buffer.size)
                source.readFully(buffer, 0, count)
                if (!replace) target.write(buffer, 0, count)
                remaining -= count
            }
            val checksum = source.readInt()
            if (!replace) target.writeInt(checksum)
            if (type == END_CHUNK) return
        }
    }

    /** 双格式使用同一份最新角色数据，便于不同版本的读取方识别。 */
    private fun writeMetadata(output: DataOutputStream, json: String) {
        writeTextChunk(output, "chara", encodeBase64Text(json))
        // 旧卡仍可读取 chara；只有包含 data 对象时才声明 V3，避免生成畸形的优先读取块。
        val ccv3Json = runCatching {
            val v3 = JsonParser.parseString(json).asJsonObject.apply {
                addProperty("spec", "chara_card_v3")
                addProperty("spec_version", "3.0")
            }
            val data = v3.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null
            // V3 必须携带群聊专用开场白数组，已有合法内容保持原样。
            if (data.get("group_only_greetings")?.isJsonArray != true) {
                data.add("group_only_greetings", JsonArray())
            }
            v3.toString()
        }.getOrNull()
        if (ccv3Json != null) {
            writeTextChunk(output, "ccv3", encodeBase64Text(ccv3Json))
        }
    }

    /**
     * 判断字节数组是否具有完整 PNG 文件签名。
     * @param bytes 至少包含签名长度的候选字节。
     * @return 字节以 PNG 签名开头时为 true。
     */
    fun isPng(bytes: ByteArray): Boolean {
        return bytes.size >= mPngSignature.size && mPngSignature.indices.all { bytes[it] == mPngSignature[it] }
    }

    /** 跳过 PNG 像素块，避免读取角色信息时复制整张图片。 */
    private fun readTextChunks(bytes: ByteArray): List<PngTextChunk> {
        require(isPng(bytes)) { "Invalid PNG signature" }
        val chunks = mutableListOf<PngTextChunk>()
        var index = mPngSignature.size
        // 长度和类型只占固定空间，根据剩余字节判断当前块是否完整。
        while (index + 8 <= bytes.size) {
            val header = ByteBuffer.wrap(bytes, index, 8).order(ByteOrder.BIG_ENDIAN)
            val length = header.int
            val type = header.int
            index += 8
            require(length in 0..(bytes.size - index - 4)) { "Invalid PNG chunk length" }
            if (type == TEXT_CHUNK) {
                bytes.copyOfRange(index, index + length).decodeTextChunk()?.let(chunks::add)
            }
            index += length + 4
            if (type == END_CHUNK) break
        }
        return chunks
    }

    /** PNG 文本关键词最多 79 字节，分隔符一定能在已读前缀中找到。 */
    private fun isCardText(prefix: ByteArray, length: Int): Boolean {
        val separator = prefix.indexOf(0)
        if (separator !in 1 until length) return false
        return String(prefix, 0, separator, Charsets.ISO_8859_1).lowercase() in mCardKeywords
    }

    /** 校验和覆盖块类型、关键词及 Base64 内容。 */
    private fun writeTextChunk(output: DataOutputStream, keyword: String, text: String) {
        val header = "tEXt".toByteArray(Charsets.ISO_8859_1)
        val name = keyword.toByteArray(Charsets.ISO_8859_1)
        val encoded = text.toByteArray(Charsets.ISO_8859_1)
        val crc = CRC32()
        crc.update(header)
        crc.update(name)
        crc.update(0)
        crc.update(encoded)
        output.writeInt(name.size + 1 + encoded.size)
        output.write(header)
        output.write(name)
        output.writeByte(0)
        output.write(encoded)
        output.writeInt(crc.value.toInt())
    }

    private fun ByteArray.decodeTextChunk(): PngTextChunk? {
        val separator = indexOf(0)
        if (separator <= 0) return null
        val keyword =
            String(this, 0, separator, Charsets.ISO_8859_1)
        val text = String(this, separator + 1, size - separator - 1, Charsets.ISO_8859_1)
        return PngTextChunk(keyword, text)
    }

    private fun encodeBase64Text(text: String): String {
        return Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    private fun decodeBase64Text(text: String): String {
        return Base64.getDecoder()
            .decode(text)
            .toString(Charsets.UTF_8)
    }

    private data class PngTextChunk(val keyword: String, val text: String)
}
