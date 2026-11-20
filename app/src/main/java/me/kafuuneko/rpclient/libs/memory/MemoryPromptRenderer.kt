package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType
import me.kafuuneko.rpclient.libs.prompt.macro.promptMacroExpander

/**
 * 普通召回、Agent 和预览共用的记忆格式渲染器。
 * - 只展开用户模板；事实先转义再作为字面值插入，不能执行其中的宏。
 * - 空模板明确省略内容，正文槽缺失则拒绝保存或导入。
 */
object MemoryPromptRenderer {
    /**
     * 渲染一条已授权事实；调用方负责作用域过滤和预算。
     * @param fact 原始事实正文，不包含本地来源或业务 ID。
     * @param snapshot 本轮已冻结的预设。
     * @param scope 本轮实际角色、人设与群成员的宏作用域。
     * @return 可直接加入参考块的文本；显式清空时为空。
     */
    fun renderItem(fact: String, snapshot: PromptTemplateSnapshot, scope: MacroScope): String =
        render(PromptType.CharacterMemoryItem, snapshot, scope, "fact", escape(fact))

    /**
     * 包装已经渲染并通过预算选择的条目，不重复展开条目中的宏。
     * @param items 同一身份可见、按优先级选择的条目。
     * @param snapshot 本轮已冻结的预设。
     * @param scope 本轮宏作用域。
     * @return 参考块正文；无条目或外层模板清空时为空。
     */
    fun renderContext(
        items: List<String>,
        snapshot: PromptTemplateSnapshot,
        scope: MacroScope
    ): String {
        val body = items.filter(String::isNotBlank).joinToString("\n")
        if (body.isEmpty()) return ""
        return render(PromptType.CharacterMemoryContext, snapshot, scope, "memory", body)
    }

    /**
     * 校验非空记忆格式中的正文槽，未知键由预设交换层原样保留。
     * @param type 模板类型。
     * @param content 用户模板原文。
     * @throws MissingMemorySlotException 非空格式遗漏必需槽时抛出。
     */
    fun validate(type: PromptType, content: String) {
        if (content.isBlank()) return
        val slot = when (type) {
            PromptType.CharacterMemoryContext -> "memory"
            PromptType.CharacterMemoryItem -> "fact"
            else -> return
        }
        if (promptMacroExpander.references(content)
                .none { it.name == slot && it.argument == null }
        ) {
            throw MissingMemorySlotException(type, slot)
        }
    }

    /** 在独立插槽作用域展开一次，插入结果永不作为新模板执行。 */
    private fun render(
        type: PromptType,
        snapshot: PromptTemplateSnapshot,
        scope: MacroScope,
        slot: String,
        body: String,
    ): String {
        val template = snapshot.values.getValue(type)
        if (template.isBlank()) return ""
        validate(type, template)
        return promptMacroExpander.expand(
            template,
            scope.withValues(mapOf(slot to MacroValue.Literal(body))),
        ).text
    }

    /** 保留数据的字面含义，避免事实正文闭合参考块标签。 */
    private fun escape(text: String): String = text.replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}

/** 错误只携带模板类型与必需槽，由界面定位字段并显示本地化提示。 */
class MissingMemorySlotException(val type: PromptType, val slot: String) :
    IllegalArgumentException("Memory template is missing its required content slot")
