package me.kafuuneko.rpclient.libs.llm.model

import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy

/** 请求中的有序内容；图片仅持不可变资源描述，不持有 Bitmap、路径或 Base64。 */
sealed interface LLMContentBlock {
    data class Text(val text: String) : LLMContentBlock
    data class Image(val reference: LLMImageReference) : LLMContentBlock
}

/** 发送版本的资源快照；缓存键包含原图 hash 和处理版本，编码时再次校验。 */
data class LLMImageReference(
    val uuid: String,
    val cacheKey: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteCount: Long,
    val estimatedTokens: Int = MessageImagePolicy.UNKNOWN_IMAGE_TOKENS
)

/** 只合并相邻文本块，保留消息间图文顺序。 */
fun mergeContentBlocks(blocks: List<LLMContentBlock>): List<LLMContentBlock> = buildList {
    blocks.forEach { block ->
        val previous = lastOrNull()
        if (previous is LLMContentBlock.Text && block is LLMContentBlock.Text) {
            removeAt(lastIndex)
            add(LLMContentBlock.Text(listOf(previous.text, block.text)
                .filter { it.isNotBlank() }.joinToString("\n\n")))
        } else if (block !is LLMContentBlock.Text || block.text.isNotBlank()) {
            add(block)
        }
    }
}

/** 从有序内容创建保留文本投影的消息，供既有纯文本工具读取。 */
fun messageWithBlocks(role: LLMMessageRole, blocks: List<LLMContentBlock>): LLMMessage =
    LLMMessage(role, blocks.filterIsInstance<LLMContentBlock.Text>().joinToString("\n\n") { it.text },
        blocks.takeIf { items -> items.any { it is LLMContentBlock.Image } }.orEmpty())
