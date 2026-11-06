package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 自定义提示词预设的元数据；系统默认预设使用 -1，不存入数据库。 */
@Entity(tableName = "prompt_presets")
data class PromptPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    @ColumnInfo(defaultValue = "1") val revision: Long = 1,
    val createdAt: Long,
    val updatedAt: Long,
)
