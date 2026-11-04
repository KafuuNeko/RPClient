package me.kafuuneko.rpclient.feature.charactercontent.model

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.character.CharacterContentKind
import me.kafuuneko.rpclient.utils.stripThinkBlocks
import me.kafuuneko.rpclient.utils.toPreview
import me.kafuuneko.rpclient.libs.room.model.CharacterContentItem as CharacterContentRecord

/** 页面筛选与导航所需的轻量条目。 */
data class CharacterContentItem(
    val type: CharacterContentKind,
    val contentId: Long,
    val title: String,
    val latestTime: Long,
    val preview: String,
    @param:StringRes val previewFallbackResId: Int,
)

/**
 * 将查询结果转换为页面条目，沿用首页的正文清理、长度限制和空内容占位。
 *
 * @return 仅包含展示和导航字段的不可变条目。
 */
internal fun CharacterContentRecord.toUiModel(): CharacterContentItem {
    // 只处理查询返回的预览；隐藏思考块并限制交给 Text 测量的消息长度。
    val preview = when (kind) {
        CharacterContentKind.Story -> previewContent.orEmpty().replace(WhitespaceRegex, " ").trim()
        else -> previewContent?.stripThinkBlocks().orEmpty().toPreview(maxLength = 512)
    }
    // 将占位文案的资源交给 Compose 解析，纯图片消息不显示为空会话。
    val fallbackResId = when {
        kind == CharacterContentKind.Story -> R.string.story_empty_document_preview
        previewHasImages -> R.string.message_image
        else -> R.string.no_messages_yet
    }
    return CharacterContentItem(
        type = kind,
        contentId = contentId,
        title = title,
        latestTime = latestTime,
        preview = preview,
        previewFallbackResId = fallbackResId,
    )
}

private val WhitespaceRegex = Regex("\\s+")
