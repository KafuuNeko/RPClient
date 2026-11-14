package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind

private val draftMacro = Regex("""\{\{\s*draft\s*\}\}""", RegexOption.IGNORE_CASE)

/**
 * 将未发送草稿作为必需材料追加到单聊或群聊请求末尾。
 * @param template 当前预设的输入框续写指令。
 * @param originalDraft 保留空白和字面宏的输入快照。
 * @param resolve 仅展开所属会话的模板宏，草稿本身不参与替换。
 * @return 可由检查器追踪的任务区段；模板与输入均为空时省略。
 */
internal fun buildUserWritingPrompt(
    template: String,
    originalDraft: String,
    resolve: (String) -> String,
): PromptMessageDraft? {
    // 只解析模板区段；草稿中的字面宏和旧式标签都保持原样。
    val content = if (draftMacro.containsMatchIn(template)) {
        draftMacro.split(template).joinToString(originalDraft, transform = resolve)
    } else {
        listOf(resolve(template), originalDraft)
            .filter { it.isNotBlank() }.joinToString("\n\n")
    }
    if (content.isBlank()) return null
    // 独立来源沿用扮演用户分类，必需材料过长时明确报预算不足，不悄悄丢失用户要求。
    return PromptMessageDraft(
        role = LLMMessageRole.User,
        content = content,
        source = PromptSource(PromptSourceKind.ImpersonationNudge),
        retentionPriority = 1_000,
        canDrop = false,
    )
}
