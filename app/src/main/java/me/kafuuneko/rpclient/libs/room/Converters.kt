package me.kafuuneko.rpclient.libs.room

import androidx.room.TypeConverter
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

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
