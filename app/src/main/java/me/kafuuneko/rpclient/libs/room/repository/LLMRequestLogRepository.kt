package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog

/** 调试请求日志仓库；只有开启 [AppModel.debugModeEnabled] 时才写入原始内容。 */
class LLMRequestLogRepository(
    private val mAppDatabase: AppDatabase
) {
    private val mLLMRequestLogDao = mAppDatabase.getLLMRequestLogDao()

    /** 按最新优先读取全部调试日志。 */
    suspend fun getAllLogs(): List<LLMRequestLog> {
        return mLLMRequestLogDao.getAllLogs()
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
                requestJson = requestJson,
                responseJson = responseJson
            )
        )
    }

    /** 清空本地调试日志。 */
    suspend fun deleteAll() {
        mLLMRequestLogDao.deleteAll()
    }
}
