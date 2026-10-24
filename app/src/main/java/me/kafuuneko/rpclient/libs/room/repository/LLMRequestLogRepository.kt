package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.libs.json.JsonResourceLimitException

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.adapter.ImageLogSanitizer
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.model.LLMRequestLogOverview

/** 调试请求日志仓库；只有开启 [AppModel.debugModeEnabled] 时才写入原始内容。 */
class LLMRequestLogRepository(
    private val mRequestLogDatabase: RequestLogDatabase
) {
    private val mLLMRequestLogDao = mRequestLogDatabase.getLLMRequestLogDao()

    /** 按最新优先读取列表摘要，不把完整原始载荷带入页面状态。 */
    suspend fun getLogOverviews(
        previewLength: Int,
        limit: Int,
        offset: Int,
    ): List<LLMRequestLogOverview> {
        return mLLMRequestLogDao.getLogOverviews(previewLength, limit, offset)
    }

    /** 按需读取单条日志的完整请求 JSON。 */
    suspend fun getRequestJson(id: Long): String? {
        return loadPayload(id, request = true)
    }

    /** 按需读取单条日志的完整响应 JSON。 */
    suspend fun getResponseJson(id: Long): String? {
        return loadPayload(id, request = false)
    }

    /** 在同一事务内拼接 UTF-8 字节，保留代理对和 NUL，避免与清空操作交错。 */
    private suspend fun loadPayload(id: Long, request: Boolean): String? =
        mRequestLogDatabase.withTransaction {
            val result = ByteArrayOutputStream()
            var start = 1
            // 单次查询只返回小片段，数据库窗口不受完整正文大小影响。
            while (true) {
                currentCoroutineContext().ensureActive()
                val chunk = if (request) {
                    mLLMRequestLogDao.getRequestJsonChunk(id, start, CHUNK_SIZE)
                } else {
                    mLLMRequestLogDao.getResponseJsonChunk(id, start, CHUNK_SIZE)
                } ?: return@withTransaction null
                if (result.size().toLong() + chunk.size > MAX_READ_BYTES) {
                    throw JsonResourceLimitException()
                }
                result.write(chunk)
                if (chunk.size < CHUNK_SIZE) break
                start += CHUNK_SIZE
            }
            result.toString(Charsets.UTF_8.name())
        }

    /** 条件写入一次完整请求/响应；非调试模式直接返回 0。 */
    suspend fun saveLog(
        provider: LLMProviderConfig,
        model: String,
        isStreaming: Boolean,
        requestJson: String,
        responseJson: String,
    ): Long {
        if (!AppModel.debugModeEnabled) return 0L
        return mLLMRequestLogDao.insertOrReplace(
            data = LLMRequestLog(
                providerName = provider.name,
                providerType = provider.providerType,
                protocol = provider.protocol,
                model = model,
                isStreaming = isStreaming,
                requestJson = ImageLogSanitizer.sanitizeLog(requestJson),
                responseJson = ImageLogSanitizer.sanitizeLog(responseJson),
            ),
        )
    }

    /** 清空本地调试日志。 */
    suspend fun deleteAll() {
        mLLMRequestLogDao.deleteAll()
    }

    private companion object {
        const val CHUNK_SIZE = 65_536
        const val MAX_READ_BYTES = 16_777_216
    }
}
