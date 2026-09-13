package me.kafuuneko.rpclient.libs.room.repository

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
        offset: Int
    ): List<LLMRequestLogOverview> {
        return mLLMRequestLogDao.getLogOverviews(previewLength, limit, offset)
    }

    /** 按需读取单条日志的完整请求 JSON。 */
    suspend fun getRequestJson(id: Long): String? {
        return mLLMRequestLogDao.getRequestJson(id)
    }

    /** 按需读取单条日志的完整响应 JSON。 */
    suspend fun getResponseJson(id: Long): String? {
        return mLLMRequestLogDao.getResponseJson(id)
    }

    /** 条件写入一次完整请求/响应；非调试模式直接返回 0。 */
    suspend fun saveLog(
        provider: LLMProviderConfig,
        model: String,
        isStreaming: Boolean,
        requestJson: String,
        responseJson: String
    ): Long {
        if (!AppModel.debugModeEnabled) return 0L
        return mLLMRequestLogDao.insertOrReplace(
            LLMRequestLog(
                providerName = provider.name,
                providerType = provider.providerType,
                protocol = provider.protocol,
                model = model,
                isStreaming = isStreaming,
                requestJson = ImageLogSanitizer.sanitize(requestJson),
                responseJson = ImageLogSanitizer.sanitize(responseJson)
            )
        )
    }

    /** 清空本地调试日志。 */
    suspend fun deleteAll() {
        mLLMRequestLogDao.deleteAll()
    }
}
