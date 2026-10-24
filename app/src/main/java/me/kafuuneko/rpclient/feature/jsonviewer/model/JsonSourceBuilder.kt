package me.kafuuneko.rpclient.feature.jsonviewer.model

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.json.JsonDocumentCodec
import me.kafuuneko.rpclient.libs.json.JsonDocumentLimits
import me.kafuuneko.rpclient.libs.json.JsonResourceLimitException
import me.kafuuneko.rpclient.libs.json.splitJsonText
import me.kafuuneko.rpclient.utils.JsonSyntaxToken
import me.kafuuneko.rpclient.utils.tokenizeJsonSyntax
import java.io.StringWriter

/** 单个有界源码块，Token 区间已转换为块内偏移。 */
data class JsonSourceBlock(val text: String, val tokens: List<JsonSyntaxToken>)

/** 源码视图独立于树导航的生成状态。 */
sealed class JsonSourceState {
    data object NotLoaded : JsonSourceState()
    data object Loading : JsonSourceState()
    data class Ready(val blocks: List<JsonSourceBlock>, val isRaw: Boolean) : JsonSourceState()
    data object TooLarge : JsonSourceState()
    data object Failed : JsonSourceState()
}

/**
 * 按需生成只读源码预览。
 *
 * - 生成前限制原文，写入中限制输出、行数和结构；
 * - 统一分词后将跨块字符串 Token 投影至每个小块，保持高亮连续；
 * - 非法旧日志展示原文，超预算内容交由文件导出读取。
 */
object JsonSourceBuilder {
    private val mLimits = JsonDocumentLimits(
        inputCharacters = 1_048_576,
        outputCharacters = 2_097_152,
        lines = 50_000,
        tokens = 100_000,
    )

    /**
     * 构建可懒加载的源码块；调用方应在后台线程执行。
     *
     * @param source 冻结的原始 JSON
     * @param checkActive 协程取消检查
     * @return 有界高亮源码、原文降级或资源超限状态
     */
    fun build(source: String, checkActive: () -> Unit = {}): JsonSourceState {
        if (source.length > mLimits.inputCharacters) return JsonSourceState.TooLarge
        checkActive()
        if (source.count { it == '\n' } >= mLimits.lines) return JsonSourceState.TooLarge
        return try {
            var isRaw = false
            // 语法错误和资源超限分开处理，超限时不再尝试生成完整原文高亮。
            val text = try {
                val output = StringWriter()
                JsonDocumentCodec.format(source, output, mLimits, checkActive)
                output.toString()
            } catch (limit: JsonResourceLimitException) {
                throw limit
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                isRaw = true
                source
            }
            val tokens = tokenizeJsonSyntax(text, mLimits.tokens, checkActive)
            JsonSourceState.Ready(projectBlocks(text, tokens, checkActive), isRaw)
        } catch (_: JsonResourceLimitException) {
            JsonSourceState.TooLarge
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            JsonSourceState.Failed
        }
    }

    /** 按字符位置投影全局 Token，避免块中间的字符串被重新识别为其他类型。 */
    private fun projectBlocks(
        source: String,
        tokens: List<JsonSyntaxToken>,
        checkActive: () -> Unit,
    ): List<JsonSourceBlock> {
        var offset = 0
        var tokenIndex = 0
        return splitJsonText(source).map { text ->
            checkActive()
            val end = offset + text.length
            while (tokenIndex < tokens.size && tokens[tokenIndex].end <= offset) tokenIndex++
            val spans = mutableListOf<JsonSyntaxToken>()
            var index = tokenIndex
            // 一个字符串可能覆盖多个块，只投影区间而不复制全局 Token 列表。
            while (index < tokens.size && tokens[index].start < end) {
                val token = tokens[index++]
                spans += token.copy(
                    start = maxOf(token.start, offset) - offset,
                    end = minOf(token.end, end) - offset,
                )
            }
            offset = end
            JsonSourceBlock(text, spans)
        }
    }
}
