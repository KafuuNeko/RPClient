package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageDashboard

/**
 * Token 消耗统计仓库。
 *
 * - 持久化每次成功生成的脱敏用量快照。
 * - 为统计页组合时间范围总览、模型与 Host 聚合、近期明细。
 * - 清理操作只影响用量统计，不触碰聊天和调试请求日志。
 */
class LLMTokenUsageRepository(
    appDatabase: AppDatabase
) {
    private val mLLMTokenUsageDao = appDatabase.getLLMTokenUsageDao()

    /** 保存一次成功请求的用量快照。 */
    suspend fun saveRecord(record: LLMTokenUsageRecord): Long {
        return mLLMTokenUsageDao.insertOrReplace(record)
    }

    /** 一次性加载统计页当前时间范围所需的数据。 */
    suspend fun getDashboard(
        startTime: Long,
        recentLimit: Int = DEFAULT_RECENT_LIMIT
    ): LLMTokenUsageDashboard {
        return LLMTokenUsageDashboard(
            summary = mLLMTokenUsageDao.getSummary(startTime),
            groups = mLLMTokenUsageDao.getGroups(startTime),
            recentRecords = mLLMTokenUsageDao.getRecentRecords(startTime, recentLimit)
        )
    }

    /** 清空全部 Token 消耗统计记录。 */
    suspend fun deleteAll() {
        mLLMTokenUsageDao.deleteAll()
    }

    private companion object {
        const val DEFAULT_RECENT_LIMIT = 50
    }
}
