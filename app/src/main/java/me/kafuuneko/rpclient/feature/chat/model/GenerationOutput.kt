package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.regex.RegexMessageSource
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

/**
 * 大模型生成结果的目标输出形态。
 */
internal sealed class GenerationOutput {
    /** 创建一条新消息并写入指定 source */
    data class Create(
        /** 产生当前数据的来源。 */
        val source: ChatMessage.Source
    ) : GenerationOutput()
    /** 更新覆盖已有的消息记录（如重新生成） */
    data class Update(
        /** 当前操作关联的消息 ID。 */
        val messageId: Long
    ) : GenerationOutput()
}

/**
 * 获取当前生成目标对应的消息来源。
 */
internal fun GenerationOutput.source(): ChatMessage.Source {
    return when (this) {
        is GenerationOutput.Create -> source
        is GenerationOutput.Update -> ChatMessage.Source.Char
    }
}

/** 将单聊消息来源映射为 Regex 流水线需要的有限来源集合。 */
internal fun ChatMessage.Source.toRegexMessageSource(): RegexMessageSource {
    return if (this == ChatMessage.Source.User) {
        RegexMessageSource.User
    } else {
        RegexMessageSource.Character
    }
}
