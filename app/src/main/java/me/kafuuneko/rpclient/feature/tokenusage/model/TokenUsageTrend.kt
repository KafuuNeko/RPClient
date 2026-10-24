package me.kafuuneko.rpclient.feature.tokenusage.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsagePeriod
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageDailyStat

/**
 * 根据当前选择的统计周期生成连续的时序数据点。
 *
 * 处理机制与补齐规则：
 * - 今天：若无数据则填充今日单个空点，有数据则直接对应今日指标。
 * - 近 7 天 / 近 30 天：从起始日期按天递增至今天，未匹配到数据库记录的日期自动填 0。
 * - 全部：直接映射已有时序数据，沿用数据库返回的日期顺序。
 *
 * @param period 当前选中的时间统计范围
 * @param rawStats 数据库按日汇总的原始统计结果
 * @return 供图表组件渲染的连续时序点列表
 */
internal fun buildTrendPoints(
    period: TokenUsagePeriod,
    rawStats: List<LLMTokenUsageDailyStat>
): List<TokenUsageTrendPoint> {
    val statMap = rawStats.associateBy { it.dateText }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val shortDateFormatter = DateTimeFormatter.ofPattern("MM-dd")

    val daysCount = when (period) {
        TokenUsagePeriod.Today -> 1
        TokenUsagePeriod.LastSevenDays -> 7
        TokenUsagePeriod.LastThirtyDays -> 30
        TokenUsagePeriod.AllTime -> 0
    }

    // 全部周期直接返回数据库已有的非空时序列表
    if (daysCount == 0) {
        return rawStats.map { stat ->
            TokenUsageTrendPoint(
                key = stat.dateText,
                label = stat.dateText.takeLast(5),
                inputTokens = stat.inputTokens,
                outputTokens = stat.outputTokens,
                cachedInputTokens = stat.cachedInputTokens,
                requestCount = stat.requestCount,
                isCurrent = stat.dateText == today.format(fullDateFormatter)
            )
        }
    }

    // 固定天数周期从过去按天递增补齐完整时间轴
    val startDay = today.minusDays((daysCount - 1).toLong())
    return (0 until daysCount).map { offset ->
        val date = startDay.plusDays(offset.toLong())
        val dateKey = date.format(fullDateFormatter)
        val label = date.format(shortDateFormatter)
        val stat = statMap[dateKey]
        TokenUsageTrendPoint(
            key = dateKey,
            label = label,
            inputTokens = stat?.inputTokens ?: 0L,
            outputTokens = stat?.outputTokens ?: 0L,
            cachedInputTokens = stat?.cachedInputTokens ?: 0L,
            requestCount = stat?.requestCount ?: 0L,
            isCurrent = date == today
        )
    }
}
