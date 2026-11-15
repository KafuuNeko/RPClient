package me.kafuuneko.rpclient.libs.macro

import java.util.Locale

/** 宏值的语义；模板继续解析引用，字面值直接插入。 */
sealed interface MacroValue {
    data class Literal(val text: String) : MacroValue

    /** [scope] 为空时使用定义该宏的作用域。 */
    data class Template(val text: String, val scope: MacroScope? = null) : MacroValue
}

/** 已规范化的宏引用；参数保留大小写，供命名插槽等业务使用。 */
data class MacroReference(val name: String, val argument: String? = null) {
    companion object {
        /** 从标签内部读取名称和可选参数。 */
        fun parse(text: String): MacroReference {
            val separator = text.indexOf("::")
            val name = if (separator < 0) text else text.substring(0, separator)
            val argument = if (separator < 0) null else text.substring(separator + 2).trim()
            return MacroReference(name.trim().lowercase(Locale.ROOT), argument)
        }
    }
}

/** 绑定身份包含定义作用域，防止同名宏在不同角色间误用缓存或误判循环。 */
data class MacroKey(val scope: MacroScope, val reference: MacroReference)

/**
 * 一次业务快照提供的只读宏作用域。
 *
 * - 普通绑定在创建时复制并规范化键，查找不再遍历字典。
 * - 子作用域只覆盖自己的绑定，父级模板继续使用父级定义环境。
 * - 参数宏由调用方按快照取值，解析器在单次展开中缓存查找结果。
 */
class MacroScope(
    values: Map<String, MacroValue> = emptyMap(),
    internal val parent: MacroScope? = null,
    private val mResolve: (MacroReference) -> MacroValue? = { null },
) {
    private val mValues = values.mapKeys { MacroReference.parse(it.key) }

    /** 创建局部绑定，适用于原始预设引用和格式槽。 */
    fun withValues(values: Map<String, MacroValue>): MacroScope = MacroScope(values, this)

    /** 读取本层绑定；父级遍历及查找缓存由单次展开会话管理。 */
    internal fun localValue(reference: MacroReference): MacroValue? =
        mValues[reference] ?: mResolve(reference)
}

/** 宏展开成功的正文及实际求值的绑定。 */
data class MacroExpansion(val text: String, val bindings: Set<MacroKey>)

/** 可供业务边界处理的宏展开失败，不包含模板或对话正文。 */
sealed class MacroExpansionException(message: String) : IllegalArgumentException(message) {
    class Cycle(val path: List<MacroKey>) : MacroExpansionException(
        "Circular macro reference: " + path.joinToString(" -> ") { it.reference.name }
    )

    class OutputLimit(val maxOutputChars: Int) : MacroExpansionException(
        "Macro output exceeds $maxOutputChars characters"
    )
}
