package me.kafuuneko.rpclient.libs.room.model

import androidx.room.Embedded
import me.kafuuneko.rpclient.libs.room.entity.PromptPreset

/**
 * 提示词预设列表的只读聚合投影，避免列表查询时额外拉取完整提示词正文。
 *
 * @property preset 自定义预设元数据实体。
 * @property overrideCount 相对系统默认值已覆盖的条目数量。
 */
data class PromptPresetSummary(
    @Embedded val preset: PromptPreset,
    val overrideCount: Int,
)
