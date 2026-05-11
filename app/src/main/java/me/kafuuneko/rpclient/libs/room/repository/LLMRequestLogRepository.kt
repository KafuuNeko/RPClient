package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog

class LLMRequestLogRepository(
    private val mAppDatabase: AppDatabase
) {
    private val mLLMRequestLogDao = mAppDatabase.getLLMRequestLogDao()

    suspend fun getAllLogs(): List<LLMRequestLog> {
        return mLLMRequestLogDao.getAllLogs()
    }

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

    suspend fun deleteAll() {
        mLLMRequestLogDao.deleteAll()
    }
}
