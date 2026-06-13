package me.kafuuneko.rpclient.libs.character

import java.io.ByteArrayOutputStream
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
    private val PngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    /**
     * 从 PNG tEXt chunk 中读取角色卡 JSON。
     *
     * 新格式优先读取 ccv3，兼容读取 SillyTavern 常见的 chara 字段。
     */
    fun readCharacterJson(bytes: ByteArray): String {
        val chunks = readChunks(bytes)
        val textChunks = chunks
            .filter { it.name == "tEXt" }
            .mapNotNull { it.decodeTextChunk() }
        val ccv3 = textChunks.firstOrNull { it.keyword.equals("ccv3", ignoreCase = true) }
        if (ccv3 != null) return decodeBase64Text(ccv3.text)
        val chara = textChunks.firstOrNull { it.keyword.equals("chara", ignoreCase = true) }
        if (chara != null) return decodeBase64Text(chara.text)
        error("PNG metadata does not contain character card data")
    }

    /**
     * 写入 PNG 角色卡元数据。
     *
     * 同时写入 chara 与 ccv3，方便旧工具和新工具都能识别。
     */
    fun writeCharacterJson(bytes: ByteArray, json: String): ByteArray {
        val chunks = readChunks(bytes)
            .filterNot { chunk ->
                chunk.name == "tEXt" && chunk.decodeTextChunk()?.keyword?.lowercase() in setOf("chara", "ccv3")
            }
            .toMutableList()
        val insertIndex = chunks.indexOfLast { it.name == "IEND" }.takeIf { it >= 0 } ?: chunks.size
        chunks.add(insertIndex, textChunk("chara", encodeBase64Text(json)))
        val ccv3Json = runCatching {
            val v3 = org.json.JSONObject(json)
                .put("spec", "chara_card_v3")
                .put("spec_version", "3.0")
            v3.toString()
        }.getOrNull()
        if (ccv3Json != null) {
            chunks.add(insertIndex + 1, textChunk("ccv3", encodeBase64Text(ccv3Json)))
        }
        return writeChunks(chunks)
    }

    /** 判断字节数组是否具有完整 PNG 文件签名。 */
    fun isPng(bytes: ByteArray): Boolean {
        return bytes.size >= PngSignature.size && PngSignature.indices.all { bytes[it] == PngSignature[it] }
    }

    private fun readChunks(bytes: ByteArray): List<PngChunk> {
        require(isPng(bytes)) { "Invalid PNG signature" }
        val chunks = mutableListOf<PngChunk>()
        var index = PngSignature.size
        while (index + 8 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.BIG_ENDIAN).int
            index += 4
            val name = bytes.copyOfRange(index, index + 4).toString(Charsets.ISO_8859_1)
            index += 4
            require(length >= 0 && index + length + 4 <= bytes.size) { "Invalid PNG chunk length" }
            val data = bytes.copyOfRange(index, index + length)
            index += length + 4
            chunks += PngChunk(name, data)
            if (name == "IEND") break
        }
        return chunks
    }

    private fun writeChunks(chunks: List<PngChunk>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(PngSignature)
        chunks.forEach { chunk ->
            output.write(intBytes(chunk.data.size))
            val nameBytes = chunk.name.toByteArray(Charsets.ISO_8859_1)
            output.write(nameBytes)
            output.write(chunk.data)
            output.write(intBytes(crc(nameBytes, chunk.data).toInt()))
        }
        return output.toByteArray()
    }

    private fun textChunk(keyword: String, text: String): PngChunk {
        return PngChunk("tEXt", keyword.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1))
    }

    private fun PngChunk.decodeTextChunk(): PngTextChunk? {
        val separator = data.indexOf(0)
        if (separator <= 0) return null
        val keyword = data.copyOfRange(0, separator).toString(Charsets.ISO_8859_1)
        val text = data.copyOfRange(separator + 1, data.size).toString(Charsets.ISO_8859_1)
        return PngTextChunk(keyword, text)
    }

    private fun encodeBase64Text(text: String): String {
        return Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    private fun decodeBase64Text(text: String): String {
        return Base64.getDecoder().decode(text).toString(Charsets.UTF_8)
    }

    private fun intBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
    }

    private fun crc(name: ByteArray, data: ByteArray): Long {
        val crc = CRC32()
        crc.update(name)
        crc.update(data)
        return crc.value
    }

    private data class PngChunk(val name: String, val data: ByteArray)
    private data class PngTextChunk(val keyword: String, val text: String)
}
