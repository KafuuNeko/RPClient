package me.kafuuneko.rpclient.libs.llm.model

/** 请求中的有序内容；图片仅持不可变资源描述，不持有 Bitmap、路径或 Base64。 */
sealed interface LLMContentBlock {
    data class Text(val text: String) : LLMContentBlock

    data class Image(val reference: LLMImageReference) : LLMContentBlock
}

/** 已提交图片的资源快照；文件内容固定，后续请求直接读取仓库。 */
data class LLMImageReference(
    val uuid: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteCount: Long
)

/** 只合并相邻文本块，保留消息间图文顺序。 */
fun mergeContentBlocks(blocks: List<LLMContentBlock>): List<LLMContentBlock> = buildList {
    blocks.forEach { block ->
        val previous = lastOrNull()
        if (previous is LLMContentBlock.Text && block is LLMContentBlock.Text) {
            removeAt(lastIndex)
            add(
                element = LLMContentBlock.Text(
                    text = listOf(previous.text, block.text)
                .filter { text -> text.isNotBlank() }.joinToString("\n\n"),
                ),
            )
        } else if (block !is LLMContentBlock.Text || block.text.isNotBlank()) {
            add(block)
        }
    }
}

/** 从有序内容创建保留文本投影的消息，供既有纯文本工具读取。 */
fun messageWithBlocks(role: LLMMessageRole, blocks: List<LLMContentBlock>): LLMMessage =
    LLMMessage(
        role = role,
        content = blocks.filterIsInstance<LLMContentBlock.Text>().joinToString("\n\n") { it.text },
        blocks = blocks.takeIf { items -> items.any { block -> block is LLMContentBlock.Image } }.orEmpty(),
    )
