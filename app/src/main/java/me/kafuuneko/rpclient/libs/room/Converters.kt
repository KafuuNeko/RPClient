package me.kafuuneko.rpclient.libs.room

import androidx.room.TypeConverter
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 将业务枚举按稳定名称写入 Room 字符串列。 */
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
}
