package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** 每种提示词至多一项；空字符串表示显式清空，缺项才使用默认值。 */
@Entity(
    tableName = "prompt_preset_entries",
    primaryKeys = ["presetId", "promptKey"],
    foreignKeys = [ForeignKey(
        entity = PromptPreset::class,
        parentColumns = ["id"],
        childColumns = ["presetId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class PromptPresetEntry(val presetId: Long, val promptKey: String, val content: String)
