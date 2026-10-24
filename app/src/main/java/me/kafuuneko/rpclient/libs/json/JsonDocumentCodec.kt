package me.kafuuneko.rpclient.libs.json

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.StringReader
import java.io.Writer

/** JSON 处理超过预先约定的资源预算，不表示原文语法错误。 */
class JsonResourceLimitException : Exception("JSON resource budget exceeded.")

/** 严格解析、格式化时使用的独立资源预算，长度单位为 UTF-16 code unit。 */
data class JsonDocumentLimits(
    val inputCharacters: Int = 8_388_608,
    val outputCharacters: Int = 67_108_864,
    val lines: Int = Int.MAX_VALUE,
    val tokens: Int = 2_000_000,
    val depth: Int = 128,
)

/**
 * JSON 文档的受控流式编解码器。
 *
 * - 严格校验单个根值，不创建额外 DOM；
 * - 保留字段顺序、重复键和数字词法，按写入进度限制输出预算；
 * - 由调用方提供取消检查，供查看器和文件导出共享。
 */
object JsonDocumentCodec {
    /**
     * 校验文档语法及结构预算，不生成格式化内容。
     *
     * @param source 原始 JSON
     * @param limits 输入和结构预算
     * @param checkActive 调用方的协程取消检查
     */
    fun validate(
        source: String,
        limits: JsonDocumentLimits = JsonDocumentLimits(),
        checkActive: () -> Unit = {},
    ) = transfer(source, null, limits, checkActive)

    /**
     * 以两空格缩进写出 JSON；输出流的关闭由调用方管理。
     *
     * @param source 原始 JSON
     * @param output 目标字符流
     * @param limits 输入、结构及输出预算
     * @param checkActive 调用方的协程取消检查
     */
    fun format(
        source: String,
        output: Writer,
        limits: JsonDocumentLimits = JsonDocumentLimits(),
        checkActive: () -> Unit = {},
    ) {
        val writer = JsonWriter(BudgetWriter(output, limits, checkActive))
        writer.setStrictness(Strictness.STRICT)
        writer.setIndent("  ")
        transfer(source, writer, limits, checkActive)
        writer.flush()
    }

    /** 逐 Token 转写，显式管理深度以避免递归及未校验的尾随内容。 */
    private fun transfer(
        source: String,
        output: JsonWriter?,
        limits: JsonDocumentLimits,
        checkActive: () -> Unit,
    ) {
        if (source.length > limits.inputCharacters) throw JsonResourceLimitException()
        JsonReader(StringReader(source)).use { reader ->
            reader.setStrictness(Strictness.STRICT)
            var depth = 0
            var tokens = 0
            // 包含结束括号的每个 Token 都计入预算，防止宽数组和深层容器耗尽内存。
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                checkActive()
                if (++tokens > limits.tokens) throw JsonResourceLimitException()
                val token = reader.peek()
                if (token == JsonToken.BEGIN_ARRAY || token == JsonToken.BEGIN_OBJECT) {
                    if (++depth > limits.depth) throw JsonResourceLimitException()
                }
                transferToken(reader, output, token)
                if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) depth--
            }
            require(tokens > 0 && depth == 0) { "Expected one complete JSON value." }
        }
    }

    /** 数字以已校验词法写出，避免 Double 转换损失精度或增加字符串引号。 */
    private fun transferToken(reader: JsonReader, output: JsonWriter?, token: JsonToken) {
        // 校验路径与格式化路径共用完全相同的消费规则。
        when (token) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                output?.beginObject()
            }

            JsonToken.END_OBJECT -> {
                reader.endObject()
                output?.endObject()
            }

            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                output?.beginArray()
            }

            JsonToken.END_ARRAY -> {
                reader.endArray()
                output?.endArray()
            }

            JsonToken.NAME -> {
                val name = reader.nextName()
                output?.name(name)
            }

            JsonToken.STRING -> {
                val value = reader.nextString()
                output?.value(value)
            }

            JsonToken.NUMBER -> {
                val number = reader.nextString()
                output?.jsonValue(number)
            }

            JsonToken.BOOLEAN -> {
                val value = reader.nextBoolean()
                output?.value(value)
            }

            JsonToken.NULL -> {
                reader.nextNull()
                output?.nullValue()
            }

            else -> error("Unexpected JSON token.")
        }
    }

    /** 在写入前校验预算，避免先构建超大字符串后才发现超限。 */
    private class BudgetWriter(
        private val mOutput: Writer,
        private val mLimits: JsonDocumentLimits,
        private val mCheckActive: () -> Unit,
    ) : Writer() {
        private var mCharacters = 0L
        private var mLines = 1L

        /** 按实际输出增量统计字符和换行，取消时不再向目标写入。 */
        override fun write(buffer: CharArray, offset: Int, length: Int) {
            mCheckActive()
            mCharacters += length
            for (index in offset until offset + length) {
                if (buffer[index] == '\n') mLines++
            }
            if (mCharacters > mLimits.outputCharacters || mLines > mLimits.lines) {
                throw JsonResourceLimitException()
            }
            mOutput.write(buffer, offset, length)
        }

        /** 仅刷新调用方的目标流。 */
        override fun flush() = mOutput.flush()

        /** 包装器不取得目标流所有权。 */
        override fun close() = flush()
    }
}
