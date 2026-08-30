package me.kafuuneko.rpclient.libs.room

import androidx.room.TypeConverter
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LocalTokenEstimatorType
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageSource

/**
 * 将业务枚举按名称写入 Room 字符串列。
 *
 * 枚举名称属于持久化格式的一部分；重命名成员时必须提供数据库迁移，不能只修改 Kotlin 名称。
 */
class Converters {
    @TypeConverter
    fun toChatMessageSource(value: String): ChatMessage.Source {
        return ChatMessage.Source.valueOf(value)
    }

    @TypeConverter
    fun fromChatMessageSource(value: ChatMessage.Source): String {
        return value.name
    }

    @TypeConverter
    fun toGroupChatMessageSource(value: String): GroupChatMessage.Source {
        return GroupChatMessage.Source.valueOf(value)
    }

    @TypeConverter
    fun fromGroupChatMessageSource(value: GroupChatMessage.Source): String {
        return value.name
    }

    @TypeConverter
    fun toGroupChatActivationStrategy(value: String): GroupChatSession.ActivationStrategy {
        return GroupChatSession.ActivationStrategy.valueOf(value)
    }

    @TypeConverter
    fun fromGroupChatActivationStrategy(value: GroupChatSession.ActivationStrategy): String {
        return value.name
    }

    @TypeConverter
    fun toGroupChatCharacterCardMode(value: String): GroupChatSession.CharacterCardMode {
        return GroupChatSession.CharacterCardMode.valueOf(value)
    }

    @TypeConverter
    fun fromGroupChatCharacterCardMode(value: GroupChatSession.CharacterCardMode): String {
        return value.name
    }

    @TypeConverter
    fun toLLMProviderType(value: String): LLMProviderType {
        return LLMProviderType.valueOf(value)
    }

    @TypeConverter
    fun fromLLMProviderType(value: LLMProviderType): String {
        return value.name
    }

    @TypeConverter
    fun toLLMProviderProtocol(value: String): LLMProviderProtocol {
        return LLMProviderProtocol.valueOf(value)
    }

    @TypeConverter
    fun fromLLMProviderProtocol(value: LLMProviderProtocol): String {
        return value.name
    }

    /** 从数据库持久化名称恢复本地 Token 预估器类型。 */
    @TypeConverter
    fun toLocalTokenEstimatorType(value: String): LocalTokenEstimatorType {
        return LocalTokenEstimatorType.valueOf(value)
    }

    /** 将本地 Token 预估器类型转换为稳定的数据库持久化名称。 */
    @TypeConverter
    fun fromLocalTokenEstimatorType(value: LocalTokenEstimatorType): String {
        return value.name
    }

    /** 从数据库持久化名称恢复 Token 用量来源。 */
    @TypeConverter
    fun toLLMTokenUsageSource(value: String): LLMTokenUsageSource {
        return LLMTokenUsageSource.valueOf(value)
    }

    /** 将 Token 用量来源转换为稳定的数据库持久化名称。 */
    @TypeConverter
    fun fromLLMTokenUsageSource(value: LLMTokenUsageSource): String {
        return value.name
    }
}
