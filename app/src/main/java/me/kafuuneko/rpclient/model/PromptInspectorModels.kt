package me.kafuuneko.rpclient.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.LocalAppDarkTheme
import kotlin.math.roundToInt

/** Prompt 检查器顶部主分区 Tab。 */
internal enum class PromptInspectorTab {
    /** 最终消息顺序与来源排查视图。 */
    Messages,

    /** 预算仪表盘、环境参数与裁剪明细视图。 */
    Overview,

    /** Regex 脚本执行记录与错误视图。 */
    Regex
}

/** 消息列表的快速筛选条件。 */
internal enum class PromptInspectorMessageFilter {
    All,

    System,

    User,

    Assistant,

    WorldInfo,

    Images
}

/** 单条消息专注阅读器的查看模式。 */
internal enum class PromptReaderMode {
    /** 按合并前的原始来源逐段拆解阅读。 */
    Segments,

    /** 查看最终合并并发送给模型的原始正文。 */
    Raw
}

/** 按来源类别归组后的展示模型，避免同类别多条目（尤其是世界书）重复刷屏前缀。 */
internal data class PromptSourceGroup(
    /** 当前分组的来源类别。 */
    val kind: PromptSourceKind,
    /** 当前类别下按出现顺序保留的全部原始来源。 */
    val sources: List<PromptSource>
) {
    /** 是否包含需要展开查看的明细或多项条目。 */
    val hasExpandableDetails: Boolean
        get() = sources.size > 1 || sources.any { source -> source.detail.isNotBlank() }
}

/** 角色在检查器中的语义配色组合。 */
internal data class PromptRoleColors(
    /** 左侧色条与强调图标色。 */
    val accent: Color,
    /** 角色徽章背景色。 */
    val container: Color,
    /** 角色徽章前景色。 */
    val content: Color
)

/** 各消息角色汇总的 Token 统计。 */
internal data class PromptRoleTokenStat(
    /** 消息角色。 */
    val role: LLMMessageRole,
    /** 该角色下消息条数。 */
    val messageCount: Int,
    /** 该角色合计估算 Token 数。 */
    val tokenCount: Int
)

/** 卡片内原地展开的最大字符数阈值，超过后引导至分块虚拟化阅读器。 */
internal const val PROMPT_INLINE_EXPAND_CHAR_LIMIT = 2_400

/** 折叠态默认展示的来源分组胶囊上限。 */
internal const val PROMPT_COLLAPSED_SOURCE_GROUP_LIMIT = 4

private const val MAX_LINES_PER_READER_CHUNK = 20
private const val MAX_CHARS_PER_READER_CHUNK = 1_200
private const val MAX_PREVIEW_LINES = 4
private const val MAX_PREVIEW_CHARS = 320

/**
 * 将同类别的来源按首次出现顺序归组，消除重复前缀噪音。
 *
 * @param sources 消息携带的原始来源列表
 * @return 按来源类别聚合后的有序分组列表
 */
internal fun groupPromptSources(sources: List<PromptSource>): List<PromptSourceGroup> {
    if (sources.isEmpty()) return emptyList()
    val grouped = LinkedHashMap<PromptSourceKind, MutableList<PromptSource>>()
    // 保持原始注入顺序，同时将多条同类型来源折叠到同一分组内
    sources.forEach { source ->
        grouped.getOrPut(source.kind) { mutableListOf() }.add(source)
    }
    return grouped.map { (kind, list) ->
        PromptSourceGroup(kind = kind, sources = list)
    }
}

/**
 * 将长正文按自然换行和字符上限拆分为多个有界块，供 LazyColumn 虚拟化平滑渲染。
 *
 * @param text 待拆分的完整正文
 * @return 拆分后的文本块列表；空白文本返回空列表
 */
internal fun splitInspectorTextChunks(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = text.split('\n')
    val chunks = mutableListOf<String>()
    val buffer = StringBuilder()
    var lineCount = 0

    // 优先沿自然换行切分，单行超长时再按字符上限硬切分，防止单个 Text 测量过重
    for (line in lines) {
        if (line.length > MAX_CHARS_PER_READER_CHUNK) {
            if (buffer.isNotEmpty()) {
                chunks += buffer.toString()
                buffer.setLength(0)
                lineCount = 0
            }
            var start = 0
            while (start < line.length) {
                val end = (start + MAX_CHARS_PER_READER_CHUNK).coerceAtMost(line.length)
                chunks += line.substring(start, end)
                start = end
            }
            continue
        }
        val projectedLength = buffer.length + line.length + if (buffer.isEmpty()) 0 else 1
        val exceedsLimit = lineCount >= MAX_LINES_PER_READER_CHUNK
            || (buffer.isNotEmpty() && projectedLength > MAX_CHARS_PER_READER_CHUNK)
        if (exceedsLimit) {
            chunks += buffer.toString()
            buffer.setLength(0)
            lineCount = 0
        }
        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(line)
        lineCount += 1
    }
    if (buffer.isNotEmpty()) {
        chunks += buffer.toString()
    }
    return chunks
}

/** 提取适合卡片折叠态展示的轻量前缀摘要，避免对超长正文做完整布局计算。 */
internal fun buildCompactPreview(text: String): String {
    if (text.isBlank()) return ""
    val bounded = if (text.length > MAX_PREVIEW_CHARS) {
        text.substring(0, MAX_PREVIEW_CHARS)
    } else {
        text
    }
    val lines = bounded.split('\n')
    val trimmed = lines.take(MAX_PREVIEW_LINES).joinToString("\n").trimEnd()
    val truncated = text.length > MAX_PREVIEW_CHARS || lines.size > MAX_PREVIEW_LINES
    return if (truncated) "$trimmed…" else trimmed
}

/** 统计正文的自然行数（空文本返回 0）。 */
internal fun countTextLines(text: String): Int {
    if (text.isEmpty()) return 0
    var lines = 1
    for (index in text.indices) {
        if (text[index] == '\n') lines += 1
    }
    return lines
}

/** 统计忽略大小写的关键词命中次数。 */
internal fun countInspectorMatches(text: String, query: String): Int {
    val needle = query.trim()
    if (needle.isEmpty() || text.isEmpty()) return 0
    var count = 0
    var index = text.indexOf(needle, startIndex = 0, ignoreCase = true)
    while (index >= 0) {
        count += 1
        index = text.indexOf(needle, startIndex = index + needle.length, ignoreCase = true)
    }
    return count
}

/**
 * 构建带关键词高亮标记的展示文本。
 *
 * @param text 原始展示文本
 * @param query 搜索关键词；空白时直接返回无高亮文本
 * @param highlightBackground 命中片段背景色
 * @param highlightForeground 命中片段文字色
 * @return 高亮后的 [AnnotatedString]
 */
internal fun buildHighlightedInspectorText(
    text: String,
    query: String,
    highlightBackground: Color,
    highlightForeground: Color,
): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    // 遍历所有非重叠命中区间，避免正则表达式对用户输入的元字符产生异常或性能回退
    return buildAnnotatedString {
        append(text)
        var startIndex = text.indexOf(needle, startIndex = 0, ignoreCase = true)
        while (startIndex >= 0) {
            val endIndex = startIndex + needle.length
            addStyle(
                style = SpanStyle(
                    background = highlightBackground,
                    color = highlightForeground,
                    fontWeight = FontWeight.SemiBold,
                ),
                start = startIndex,
                end = endIndex,
            )
            startIndex = text.indexOf(needle, startIndex = endIndex, ignoreCase = true)
        }
    }
}

/** 判断消息项是否命中当前角色/类型筛选与关键词搜索。 */
internal fun matchesMessageFilter(
    item: PromptInspectionItem,
    filter: PromptInspectorMessageFilter,
    query: String,
    resolvedSourceLabels: List<String>,
): Boolean {
    val passesCategory = when (filter) {
        PromptInspectorMessageFilter.All -> true
        PromptInspectorMessageFilter.System -> item.role == LLMMessageRole.System
        PromptInspectorMessageFilter.User -> item.role == LLMMessageRole.User
        PromptInspectorMessageFilter.Assistant -> item.role == LLMMessageRole.Assistant
        PromptInspectorMessageFilter.WorldInfo -> item.sources.any { source ->
            source.kind == PromptSourceKind.WorldInfo
        }

        PromptInspectorMessageFilter.Images -> item.images.isNotEmpty()
    }
    if (!passesCategory) return false
    val needle = query.trim()
    if (needle.isEmpty()) return true
    // 同时匹配消息正文、来源明细、来源本地化名称和角色名
    if (item.content.contains(needle, ignoreCase = true)) return true
    if (item.role.name.contains(needle, ignoreCase = true)) return true
    if (item.sources.any { source -> source.detail.contains(needle, ignoreCase = true) }) {
        return true
    }
    return resolvedSourceLabels.any { label -> label.contains(needle, ignoreCase = true) }
}

/** 在分段列表中查找与指定来源匹配的首个分段下标，供点击来源明细直达阅读器。 */
internal fun findSegmentIndexForSource(
    item: PromptInspectionItem,
    target: PromptSource,
): Int? {
    val exactIndex = item.segments.indexOfFirst { segment -> target in segment.sources }
    if (exactIndex >= 0) return exactIndex
    val kindIndex = item.segments.indexOfFirst { segment ->
        segment.sources.any { source -> source.kind == target.kind }
    }
    return kindIndex.takeIf { it >= 0 }
}

/** 计算当前消息在整份输入 Prompt 中的 Token 占比百分比（0..100）。 */
internal fun computeTokenSharePercent(partTokens: Int, totalTokens: Int): Int {
    if (partTokens <= 0 || totalTokens <= 0) return 0
    return ((partTokens.toDouble() / totalTokens.toDouble()) * 100.0)
        .roundToInt()
        .coerceIn(minimumValue = 0, maximumValue = 100)
}

/** 按角色汇总消息数量与 Token 分布，供概览页可视化展示。 */
internal fun summarizeRoleTokens(items: List<PromptInspectionItem>): List<PromptRoleTokenStat> {
    val order = listOf(LLMMessageRole.System, LLMMessageRole.User, LLMMessageRole.Assistant)
    return order.mapNotNull { role ->
        val matching = items.filter { item -> item.role == role }
        if (matching.isEmpty()) {
            null
        } else {
            PromptRoleTokenStat(
                role = role,
                messageCount = matching.size,
                tokenCount = matching.sumOf { item -> item.tokenCount },
            )
        }
    }
}

/** 将整份最终 Prompt 格式化为适合剪贴板复制与外部对比的纯文本。 */
internal fun formatFullPromptForCopy(inspection: PromptInspection): String {
    // 保留每条消息的序号、角色与估算 Token 边界，便于复制到外部文本工具比对
    return inspection.items.joinToString(separator = "\n\n") { item ->
        buildString {
            append("=== #")
            append(item.index)
            append(" ")
            append(item.role.name.uppercase())
            append(" (")
            append(item.tokenCount)
            append(" Tk) ===")
            if (item.content.isNotEmpty()) {
                append('\n')
                append(item.content)
            }
        }
    }
}

/** 根据消息角色解析适配明暗主题的语义配色。 */
@Composable
internal fun resolveRoleColors(role: LLMMessageRole): PromptRoleColors {
    val darkTheme = LocalAppDarkTheme.current
    // System 沿用主题主色，User 采用独立翡翠色，Assistant 使用柔和次级色阶以拉开层次
    return when (role) {
        LLMMessageRole.System -> PromptRoleColors(
            accent = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            content = MaterialTheme.colorScheme.primary,
        )

        LLMMessageRole.User -> {
            val foreground = if (darkTheme) AccentEmeraldColor else Color(0xFF047857)
            PromptRoleColors(
                accent = AccentEmeraldColor,
                container = AccentEmeraldColor.copy(alpha = if (darkTheme) 0.18f else 0.12f),
                content = foreground,
            )
        }

        LLMMessageRole.Assistant -> PromptRoleColors(
            accent = MaterialTheme.colorScheme.secondary,
            container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f),
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 返回来源类别对应的本地化基础名称（不含 detail 补充说明）。 */
@Composable
internal fun PromptSourceKind.baseLabel(): String {
    // 统一维护全部 Prompt 来源枚举与多语言资源映射，供归组胶囊和分段标题复用
    val sourceLabelResId = when (this) {
        PromptSourceKind.MainPrompt -> R.string.prompt_source_main
        PromptSourceKind.WorldInfo -> R.string.prompt_source_world_info
        PromptSourceKind.UserPersona -> R.string.prompt_source_user_persona
        PromptSourceKind.CharacterDescription -> R.string.prompt_source_character_description
        PromptSourceKind.CharacterPersonality -> R.string.prompt_source_character_personality
        PromptSourceKind.Scenario -> R.string.prompt_source_scenario
        PromptSourceKind.Summary -> R.string.prompt_source_summary
        PromptSourceKind.SummaryTask -> R.string.summarize_now
        PromptSourceKind.AuxiliaryPrompt -> R.string.prompt_source_auxiliary
        PromptSourceKind.ExampleDialogue -> R.string.prompt_source_examples
        PromptSourceKind.NewChatMarker -> R.string.prompt_source_new_chat
        PromptSourceKind.ChatHistory -> R.string.prompt_source_history
        PromptSourceKind.UserNote -> R.string.prompt_source_user_note
        PromptSourceKind.CharacterNote -> R.string.prompt_source_character_note
        PromptSourceKind.PostHistoryInstructions -> R.string.prompt_source_post_history
        PromptSourceKind.CharacterReplyNudge -> R.string.prompt_source_character_reply_nudge
        PromptSourceKind.ContinueNudge -> R.string.prompt_source_continue
        PromptSourceKind.ImpersonationNudge -> R.string.prompt_source_impersonation
        PromptSourceKind.GroupIdentity -> R.string.prompt_source_group_identity
        PromptSourceKind.CharacterCard -> R.string.prompt_source_character_card
        PromptSourceKind.GroupNudge -> R.string.prompt_source_group_nudge
        PromptSourceKind.StoryMainPrompt -> R.string.prompt_source_story_main
        PromptSourceKind.StoryMemory -> R.string.prompt_source_story_memory
        PromptSourceKind.StorySummary -> R.string.prompt_source_story_summary
        PromptSourceKind.StoryAuthorNote -> R.string.prompt_source_story_author_note
        PromptSourceKind.StoryCharacter -> R.string.prompt_source_story_character
        PromptSourceKind.StoryDocumentContext -> R.string.prompt_source_story_document
        PromptSourceKind.StoryContinuationGuidance -> R.string.prompt_source_story_continuation_guidance
        PromptSourceKind.StoryTask -> R.string.prompt_source_story_task
        PromptSourceKind.PostProcessing -> R.string.prompt_source_post_processing
        PromptSourceKind.Other -> R.string.prompt_source_other
    }
    return stringResource(id = sourceLabelResId)
}

/** 返回单个来源的完整可读名称（基础名与可选 detail 拼接）。 */
@Composable
internal fun PromptSource.label(): String {
    val base = kind.baseLabel()
    return if (detail.isBlank()) base else "$base · $detail"
}
