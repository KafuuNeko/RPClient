package me.kafuuneko.rpclient.libs.character

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.FilterReader
import java.io.Reader

/** 限制单条记录和书籍元数据的复杂度，避免紧凑 JSON 在建树时放大成大量对象。 */
private const val MAX_VALUE_TOKENS = 65_536
private const val MAX_VALUE_DEPTH = 128
private const val MAX_VALUE_CHARS = 8 * 1024 * 1024

/**
 * 逐 Token 预检同一份 JSON，文件导入和导出均在提交前执行。
 * @param source 当前书籍的字符流。
 * @param maxEntries 当前单本和批次剩余额度允许的最大条目数。
 * @param checkActive 检查调用方的任务是否仍在运行。
 * @return 文档内的条目数，用于累计 ZIP 的实体预算。
 */
internal fun inspectLorebookJson(source: Reader, maxEntries: Int, checkActive: () -> Unit): Int {
    val bounded = LorebookValueReader(source)
    val reader = JsonReader(bounded).apply { strictness = Strictness.LENIENT }
    var count = 0
    var hasEntries = false
    reader.beginObject()
    // 根字段和条目均可乱序；先计量，再由编解码器创建需要持久化的对象。
    while (reader.hasNext()) {
        bounded.beginValue()
        if (reader.nextName() != "entries") {
            reader.inspectValue(checkActive)
            continue
        }
        reader.beginObject()
        hasEntries = true
        while (reader.hasNext()) {
            if (++count > maxEntries) throw LorebookArchiveLimitException()
            bounded.beginValue()
            reader.skipValue() // 此时位于属性名，只跳过条目 ID。
            reader.inspectValue(checkActive)
        }
        reader.endObject()
    }
    reader.endObject()
    require(hasEntries && reader.peek() == JsonToken.END_DOCUMENT) { "Expected one world book with entries" }
    return count
}

/**
 * 安全识别完整备份的根清单，无需加载清单或把普通同名世界书整本建树。
 * @param source 已限制解压字节数的清单流。
 * @param checkActive 检查调用方的任务是否仍在运行。
 * @return 是否声明了受支持的完整备份格式。
 */
internal fun isLorebookBackupManifest(source: Reader, checkActive: () -> Unit): Boolean {
    val reader = JsonReader(source).apply { strictness = Strictness.LENIENT }
    reader.beginObject()
    while (reader.hasNext()) {
        if (reader.nextName() == "format" && reader.peek() == JsonToken.STRING) {
            if (reader.nextString() in BACKUP_FORMATS) return true
        } else {
            // 一个清单字段可能包含整本世界书，只限制深度，逐条预检随后执行。
            reader.inspectValue(checkActive, maxTokens = Int.MAX_VALUE)
        }
    }
    return false
}

private val BACKUP_FORMATS = setOf("rpclient-portable-data", "rpclient-backup")

/** 只扫描容器边界，标量和属性名直接跳过，预检不保存第三方扩展树。 */
private fun JsonReader.inspectValue(checkActive: () -> Unit, maxTokens: Int = MAX_VALUE_TOKENS) {
    var depth = 0
    var tokens = 0
    do {
        if (++tokens > maxTokens) throw LorebookArchiveLimitException()
        if (tokens == 1 || tokens % 1024 == 0) checkActive()
        // 手动进入容器，宽数组、深嵌套与同名字段都计入复杂度。
        when (peek()) {
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                depth++
            }

            JsonToken.BEGIN_OBJECT -> {
                beginObject()
                depth++
            }

            JsonToken.END_ARRAY -> {
                endArray()
                depth--
            }

            JsonToken.END_OBJECT -> {
                endObject()
                depth--
            }

            else -> skipValue()
        }
        if (depth > MAX_VALUE_DEPTH) throw LorebookArchiveLimitException()
    } while (depth > 0)
}

/** 在 Gson 扩容长字符串前限制单个字段或条目的读取量，包含解析器最多一个缓冲区的预读。 */
private class LorebookValueReader(source: Reader) : FilterReader(source) {
    private var mValueChars = 0

    /** 开始计量下一个独立字段或条目。 */
    fun beginValue() {
        mValueChars = 0
    }

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) mValueChars += count
        if (mValueChars > MAX_VALUE_CHARS) throw LorebookArchiveLimitException()
        return count
    }
}
