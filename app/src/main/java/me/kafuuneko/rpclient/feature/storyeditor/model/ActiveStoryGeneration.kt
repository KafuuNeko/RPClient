package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryStreamState
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookRuntimeState
import me.kafuuneko.rpclient.libs.story.StoryEditTarget
import me.kafuuneko.rpclient.libs.story.prepareStoryContinuationText

/**
 * 活跃的故事 AI 续写任务快照，记录生成上下文与局部生成增量。
 */
internal data class ActiveStoryGeneration(
    val token: Any,
    val provider: LLMProvider,
    val target: StoryEditTarget,
    val chapterId: Long,
    val baseStoryRevision: Long,
    val baseChapterRevision: Long,
    val sourceContent: String,
    val previousEditedRange: StoryEditedTextRange?,
    val previousWorldInfoStates: List<StoryLorebookRuntimeState>,
    val previousWorldInfoGenerationStep: Int,
    val nextWorldInfoStates: List<StoryLorebookRuntimeState>,
    val startedAtElapsedRealtime: Long,
    val partialText: String = "",
    val reasoningText: String = "",
    val streamState: StoryStreamState = StoryStreamState.AwaitingResponse,
    val isReasoningExpanded: Boolean = false
) {
    /** 追加模型构思，并在正文已经开始后保持写作阶段不倒退。 */
    fun appendReasoning(content: String): ActiveStoryGeneration {
        val nextReasoning = (reasoningText + content).takeLast(MAX_REASONING_DISPLAY_CHARS)
        return copy(
            reasoningText = nextReasoning,
            streamState = if (streamState == StoryStreamState.Writing) {
                StoryStreamState.Writing
            } else {
                StoryStreamState.Reasoning
            },
        )
    }

    /** 追加最终正文，并保持用户当前选择的构思展开状态。 */
    fun appendGeneratedText(content: String): ActiveStoryGeneration {
        return copy(
            partialText = partialText + content,
            streamState = StoryStreamState.Writing,
        )
    }

    /**
     * 合并同一任务的最新交互状态。
     *
     * 模型文本由收包协程持有，而展开状态可由 UI Intent 独立更新；每次处理流事件前合并，
     * 防止协程中的旧快照覆盖用户刚刚完成的操作。
     */
    fun withLatestInteractionState(latest: ActiveStoryGeneration?): ActiveStoryGeneration {
        if (latest?.token !== token) return this
        return copy(isReasoningExpanded = latest.isReasoningExpanded)
    }

    /** 构造只包含当前展示所需推理信息的流式页面状态。 */
    fun toStreamingState(reasoningDetail: String = ""): StoryGenerationState.Streaming {
        return StoryGenerationState.Streaming(
            partialText = partialText,
            streamState = streamState,
            startedAtElapsedRealtime = startedAtElapsedRealtime,
            reasoningPreview = reasoningText.toReasoningPreview(),
            reasoningDetail = reasoningDetail,
            isReasoningExpanded = isReasoningExpanded,
        )
    }

    /** 清除只属于活跃请求的推理信息，避免失败恢复快照延长其内存生命周期。 */
    fun withoutReasoning(): ActiveStoryGeneration {
        return copy(
            reasoningText = "",
            isReasoningExpanded = false,
        )
    }

    /** 生成拼接了增量续写预览文本的完整正文。 */
    fun previewContent(): String {
        val previewText = prepareStoryContinuationText(sourceContent, partialText)
        return sourceContent.replaceRange(target.start, target.end, previewText)
    }

    /** 计算当前生成结果在正文中的范围。 */
    fun resultTextRange(text: String = partialText): StoryEditedTextRange? {
        if (text.isEmpty()) return null
        return StoryEditedTextRange(
            start = target.start,
            end = target.start + text.length,
        )
    }

    private companion object {
        /** 防止异常服务返回无限推理文本并长期占用编辑器内存。 */
        const val MAX_REASONING_DISPLAY_CHARS = 24_000
    }
}

/** 将推理文本压缩为状态卡使用的单行最近片段。 */
private fun String.toReasoningPreview(): String {
    val normalized = lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
    if (normalized.length <= REASONING_PREVIEW_CHARS) return normalized
    return "…${normalized.takeLast(REASONING_PREVIEW_CHARS - 1)}"
}

private const val REASONING_PREVIEW_CHARS = 220
