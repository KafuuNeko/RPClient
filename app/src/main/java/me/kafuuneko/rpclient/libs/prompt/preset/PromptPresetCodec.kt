package me.kafuuneko.rpclient.libs.prompt.preset

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import me.kafuuneko.rpclient.libs.memory.MemoryPromptRenderer
import java.io.StringReader

/** 便携预设只交换元数据和覆盖项，不携带当前选择、会话引用或凭据。 */
data class PortablePromptPreset(
    val name: String,
    val description: String,
    val overrides: Map<String, String>,
)

/**
 * 版本化提示词预设交换格式。
 * - 缺项和显式空串保留各自语义；未知键按原文往返。
 * - 严格检查版本、重复键、字段类型与记忆正文槽，拒绝有损导入。
 */
object PromptPresetCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val FORMAT = "rpclient.prompt-preset"
    private const val VERSION = 1

    /**
     * 编码用户主动导出的预设。
     * @param preset 元数据及稀疏覆盖项。
     * @return UTF-8 JSON 文本。
     */
    fun encode(preset: PortablePromptPreset): String {
        validate(preset)
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            addProperty("name", preset.name)
            addProperty("description", preset.description)
            add("overrides", JsonObject().apply {
                preset.overrides.forEach { (key, value) -> addProperty(key, value) }
            })
        }
        // 统一大小上限，防止导出的文件无法由同一版本重新读取。
        val json = GsonBuilder().setPrettyPrinting().create().toJson(root)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Prompt preset exceeds size limit" }
        return json
    }

    /**
     * 严格读取便携包，不通过 Gson 的默认值掩盖缺项或错误类型。
     * @param json 已受文件大小限制的 UTF-8 正文。
     * @return 可事务导入的新预设内容。
     */
    fun decode(json: String): PortablePromptPreset {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Prompt preset exceeds size limit" }
        JsonReader(StringReader(json)).use { reader ->
            reader.strictness = Strictness.STRICT
            val metadata = mutableMapOf<String, String>()
            var overrides: Map<String, String>? = null
            val keys = mutableSetOf<String>()
            // 使用流式读取检测重复字段，避免最后一项静默覆盖用户内容。
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(keys.add(key)) { "Duplicate prompt preset field" }
                when (key) {
                    "overrides" -> overrides = readOverrides(reader)
                    "version" -> {
                        require(reader.peek() == JsonToken.NUMBER && reader.nextString() == VERSION.toString()) {
                            "Unsupported prompt preset version"
                        }
                    }

                    "format", "name", "description" -> metadata[key] = readString(reader)
                    else -> error("Unknown prompt preset metadata field")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected trailing prompt preset data" }
            require("version" in keys && metadata["format"] == FORMAT) { "Unsupported prompt preset format" }
            // 无当前选择字段，调用方只创建新副本，不能随导入改变活动预设。
            return PortablePromptPreset(
                name = requireNotNull(metadata["name"]) { "Missing prompt preset name" },
                description = requireNotNull(metadata["description"]) { "Missing prompt preset description" },
                overrides = requireNotNull(overrides) { "Missing prompt preset overrides" },
            ).also(::validate)
        }
    }

    private fun readOverrides(reader: JsonReader): Map<String, String> {
        val values = linkedMapOf<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(key.isNotBlank() && key !in values) { "Invalid prompt preset key" }
            values[key] = readString(reader)
        }
        reader.endObject()
        return values
    }

    private fun readString(reader: JsonReader): String {
        require(reader.peek() == JsonToken.STRING) { "Prompt preset field must be a string" }
        return reader.nextString()
    }

    /** 保存、导出和导入共享已知格式校验，未知键不参与当前版本的宏解析。 */
    private fun validate(preset: PortablePromptPreset) {
        require(preset.name.isNotBlank()) { "Prompt preset name is empty" }
        require(preset.overrides.keys.all(String::isNotBlank)) { "Prompt preset key is empty" }
        PromptType.entries.forEach { type ->
            preset.overrides[type.key]?.let { MemoryPromptRenderer.validate(type, it) }
        }
    }
}
