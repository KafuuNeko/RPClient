package me.kafuuneko.rpclient.libs.backup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File

/** 单聊正文之外的本地关联信息，消息附加信息与解码后的普通消息顺序一致。 */
internal data class BackupChatMetadata(
    val headerSide: JsonObject?,
    val messageSides: List<JsonObject?>
)

/**
 * 流式读取单聊文档的备份附加信息。
 * - 正文与图片由 ChatArchiveCodec 处理，此处跳过它们，避免再次构建含 Base64 的 JSON 树。
 * - 消息过滤遵循单聊协议的用户、角色和旁白规则。
 */
internal object BackupChatMetadataReader {
    /**
     * 读取已通过聊天协议校验的文档，仅保留本地关联信息。
     * @param file 当前归档暂存目录中的聊天文件。
     * @return 头部附加信息及按消息顺序排列的附加信息。
     */
    fun read(file: File): BackupChatMetadata = file.bufferedReader(Charsets.UTF_8).use { input ->
        val reader = JsonReader(input).apply { strictness = Strictness.LENIENT }
        val header = readObject(reader)
        val messages = mutableListOf<JsonObject?>()
        // 跳过正文和图片载荷，只按消息角色决定是否占用一个附加信息位置。
        while (reader.peek() != JsonToken.END_DOCUMENT) {
            val message = readObject(reader)
            if (message.decoded) messages.add(message.side)
        }
        BackupChatMetadata(headerSide = header.side, messageSides = messages)
    }

    private data class MessageMetadata(val side: JsonObject?, val decoded: Boolean)

    /** 提取角色标记与附加键，不把消息内容或图片载荷加载到内存。 */
    private fun readObject(reader: JsonReader): MessageMetadata {
        var side: JsonObject? = null
        var content = false
        var user = false
        var system = false
        var narrator = false
        reader.beginObject()
        // 旁白优先于系统标记；数字或布尔正文也与协议的 primitive 转字符串规则一致。
        while (reader.hasNext()) {
            when (reader.nextName()) {
                BackupV3.SIDECAR -> side = JsonParser.parseReader(reader).asJsonObject
                "mes" -> {
                    content = reader.peek() in setOf(
                        JsonToken.STRING,
                        JsonToken.NUMBER,
                        JsonToken.BOOLEAN
                    )
                    reader.skipValue()
                }

                "is_user" -> user = readFlag(reader)
                "is_system" -> system = readFlag(reader)
                "extra" -> narrator = readExtraType(reader).equals("narrator", ignoreCase = true)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return MessageMetadata(side, decoded = content && (narrator || user || !system))
    }

    /** 非布尔标记不参与角色判断，和聊天解码器保持一致。 */
    private fun readFlag(reader: JsonReader): Boolean {
        if (reader.peek() == JsonToken.BOOLEAN) return reader.nextBoolean()
        reader.skipValue()
        return false
    }

    /** extra 中只有 type 影响消息过滤，图片数组全部跳过。 */
    private fun readExtraType(reader: JsonReader): String? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var type: String? = null
        reader.beginObject()
        // 只读取类型名称，原生与自有图片字段均由主解码器负责。
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "type" && reader.peek() == JsonToken.STRING) type = reader.nextString()
            else reader.skipValue()
        }
        reader.endObject()
        return type
    }
}
