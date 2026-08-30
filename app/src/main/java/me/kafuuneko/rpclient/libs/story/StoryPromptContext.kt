package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterCandidate
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookEntryCandidate
import java.security.MessageDigest

/** 一次续写要插入的 UTF-16 正文位置。 */
data class StoryEditTarget(
    /** 当前区间的起始位置，包含该位置。 */
    val start: Int,
    /** 当前区间的结束位置，不包含该位置。 */
    val end: Int
) {
    init {
        require(start >= 0 && end == start) { "Story continuation target must be a cursor" }
    }

    fun originalText(content: String): String {
        require(end == content.length) { "Story continuation target must be at document end" }
        return content.substring(start, end)
    }
}

/** 构建一轮故事写作 Prompt 所需的完整、只读输入。 */
data class StoryPromptContext(
    /** 当前页面展示或编辑的故事数据。 */
    val story: Story,
    /** 本次故事 Prompt 可参与激活的角色候选列表。 */
    val characterCandidates: List<StoryCharacterCandidate>,
    /** 当前操作作用的目标。 */
    val target: StoryEditTarget,
    /** 执行变更前保存的原始正文。 */
    val sourceContent: String,
    /** 当前请求关联的模型供应商类型。 */
    val provider: LLMProvider,
    /** 通过作用域筛选后待扫描的世界书条目列表。 */
    val candidateLorebookEntries: List<StoryLorebookEntryCandidate>,
    /** 本次扫描可能参与激活的世界书列表。 */
    val candidateLorebooks: Map<Long, Lorebook>,
    /** 允许参与递归激活扫描的世界书 ID 集合。 */
    val recursiveScanningLorebookIds: Set<Long>,
    /** 当前会话或 Prompt 使用的用户名称。 */
    val userName: String,
    /** 当前会话或 Prompt 使用的用户设定。 */
    val userDescription: String,
    /** 用户为下一次故事续写提供的临时指导。 */
    val continuationGuidance: String = ""
)

/** 对正文目标做稳定校验时使用的 SHA-256。 */
fun storyTextHash(text: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
