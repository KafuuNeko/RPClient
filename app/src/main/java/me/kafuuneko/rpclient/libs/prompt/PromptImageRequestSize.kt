package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy

/**
 * 为既有上下文裁剪估算多模态 JSON 大小，聊天与摘要共用同一口径。
 * 不可用候选没有实际字节元数据，暂以单图发送上限近似；Codec 仍精确校验最终字节。
 */
internal fun estimateImageRequestBytes(
    messages: List<LLMMessage>,
    unavailableCount: Int,
    patchJson: String = "",
): Long {
    val imageBytes = messages.sumOf { message ->
        message.images.sumOf { image -> estimatedEncodedImageBytes(image.byteCount) }
    }
    val unavailableBytes =
        unavailableCount * estimatedEncodedImageBytes(MessageImagePolicy.MAX_SEND_BYTES)
    // JSON 转义最坏为每个字节六个字符，预留协议结构的既有固定开销。
    val textBytes = messages.sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() * 6 }
    return imageBytes + unavailableBytes + textBytes + patchJson.toByteArray(Charsets.UTF_8).size + 4096
}

/** 大于请求上限的异常元数据只需返回超限近似，避免 Base64 长度换算溢出。 */
private fun estimatedEncodedImageBytes(bytes: Long): Long =
    ((bytes.coerceIn(
        minimumValue = 0,
        maximumValue = MessageImagePolicy.MAX_REQUEST_BYTES
    ) + 2) / 3) * 4 + 256
