package me.kafuuneko.rpclient.utils

/** 单次消息编辑使用固定偏好，避免编辑期间更改设置导致思考链丢失或重复。 */
class MessageEditPolicy(private val mSeparateThinkBlocks: Boolean = true) {
    companion object {
        private const val OPEN_TAG = "<think>"
        private const val CLOSE_TAG = "</think>"
    }

    fun createDraft(content: String): String =
        if (mSeparateThinkBlocks) stripThinkBlock(content) else content

    /** 在 Source 正则处理完成后，仅在分离模式下恢复原消息的思考链。 */
    fun applyEditedContent(original: String, edited: String): String =
        if (mSeparateThinkBlocks) replaceContentKeepingThink(original, edited) else edited

    /** 取出思维链正文；没有就返回空串。 */
    private fun extractThinkBlock(content: String): String {
        val start = content.indexOf(OPEN_TAG)
        if (start < 0) return ""
        val close = content.indexOf(CLOSE_TAG, start)
        if (close < 0) return ""
        return content.substring(start + OPEN_TAG.length, close).trim()
    }

    /**
     * 剥掉思维链，只留正文。
     *
     * 编辑框里应该只出现这个结果——推理过程不是给用户改的东西。
     *
     * 被打断的思考（有开标记没闭标记）同样要剥：那种情况下标记之后
     * 全是没写完的推理，正文还没开始。第一版对未闭合标记「放行」，
     * 结果是半截推理永远赖在消息里，既删不掉也没法编辑。
     */
    private fun stripThinkBlock(content: String): String {
        var result = content
        while (true) {
            val start = result.indexOf(OPEN_TAG)
            if (start < 0) break
            val close = result.indexOf(CLOSE_TAG, start)
            if (close < 0) {
                // 没闭合：从标记到末尾都是没写完的思考
                result = result.substring(0, start)
                break
            }
            result = result.removeRange(start, close + CLOSE_TAG.length)
        }
        return result.trim()
    }

    /**
     * 用新的正文替换原有的正文，思维链原样保留。
     *
     * 保存编辑时走这里：用户改的是正文，推理过程不该跟着一起被覆盖，
     * 也不该因为编辑而丢失——它是这条回复当时怎么想出来的记录。
     */
    private fun replaceContentKeepingThink(original: String, newContent: String): String {
        val think = extractThinkBlock(original)
        if (think.isBlank()) return newContent.trim()
        return "$OPEN_TAG\n$think\n$CLOSE_TAG\n\n${newContent.trim()}"
    }

}
