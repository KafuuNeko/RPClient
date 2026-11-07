package me.kafuuneko.rpclient.libs.chat

/**
 * 将兼容服务商正文中的思考标签从可发送草稿中分离。
 *
 * - 跨片段保留可能组成标签的尾缀，避免半个标签短暂进入输入框。
 * - 未闭合的思考内容始终留在思考区，普通心理描写不作语义过滤。
 */
class UserWritingOutputParser {
    private val mBody = StringBuilder()
    private val mReasoning = StringBuilder()
    private var mPending = ""
    private var mThinking = false

    val body: String get() = mBody.toString()
    val reasoning: String get() = mReasoning.toString()

    /**
     * 接收正文增量并处理完整标签，推理缓冲仅保留最近的展示内容。
     * @param text 模型正文通道返回的原始增量。
     */
    fun append(text: String) {
        mPending += text
        // 一次增量可能包含多个正文与思考片段，逐段切换输出目标。
        while (mPending.isNotEmpty()) {
            val tag = if (mThinking) CLOSE_TAG else OPEN_TAG
            val index = mPending.indexOf(tag, ignoreCase = true)
            if (index >= 0) {
                appendVisible(mPending.substring(0, index))
                mPending = mPending.substring(index + tag.length)
                mThinking = !mThinking
                continue
            }
            // 保留最长候选尾缀；下一片段到达后再确定它是标签还是正文。
            val held = (1 until tag.length).lastOrNull { length ->
                mPending.endsWith(tag.take(length), ignoreCase = true)
            } ?: 0
            appendVisible(mPending.dropLast(held))
            mPending = mPending.takeLast(held)
            break
        }
    }

    /**
     * 结束时丢弃尚未判定的标签尾缀，避免截断标签留在正文或思考展示中。
     */
    fun finish() {
        mPending = ""
    }

    private fun appendVisible(text: String) {
        if (!mThinking) {
            mBody.append(text)
            return
        }
        mReasoning.append(text)
        if (mReasoning.length > MAX_REASONING_CHARS) {
            mReasoning.delete(0, mReasoning.length - MAX_REASONING_CHARS)
        }
    }

    companion object {
        const val MAX_REASONING_CHARS = 24_000
        private const val OPEN_TAG = "<think>"
        private const val CLOSE_TAG = "</think>"
    }
}
