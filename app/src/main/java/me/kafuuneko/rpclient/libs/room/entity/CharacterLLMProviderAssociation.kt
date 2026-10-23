package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 角色与回复模型配置的一对一关联。
 *
 * 表中不存在角色记录时表示该角色跟随全局模型配置；角色或模型配置删除后关联自动清理。
 */
@Entity(
    tableName = "character_llm_provider_associations",
    primaryKeys = ["characterId"],
    foreignKeys = [
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LLMProvider::class,
            parentColumns = ["id"],
            childColumns = ["llmProviderId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("llmProviderId")]
)
data class CharacterLLMProviderAssociation(
    // 关联角色 ID；每个角色最多绑定一个回复模型配置。
    val characterId: Long,
    // 角色回复时使用的模型配置 ID。
    val llmProviderId: Long
)
