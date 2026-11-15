package me.kafuuneko.rpclient.libs.macro

import java.util.Locale

/**
 * 与业务无关的宏展开器，按定义作用域对引用进行按需求值。
 *
 * - 显式栈检测当前引用链，完成结果缓存为共享片段，最后只拼接一次字符串。
 * - 设 S 为模板总长、E 为引用数、K 为实际查找的作用域与名称对、L 为输出长，时间与空间均为 O(S + E + K + L)。
 * - 缓存及引用链属于单次调用，实例仅保存不可变语法，可跨请求复用。
 */
class MacroExpander(aliases: Map<String, String> = emptyMap()) {
    private val mAliases = aliases.mapKeys { it.key.lowercase(Locale.ROOT) }
    private val mPattern = Regex(
        "\\{\\{([^{}]*)\\}\\}" + aliases.keys.joinToString("") { "|${Regex.escape(it)}" },
        RegexOption.IGNORE_CASE,
    )

    /**
     * 展开模板，未知引用保留原始写法，字面绑定始终作为终值。
     * @param template 当前待展开模板。
     * @param scope 业务提供的只读绑定。
     * @param maxOutputChars 单段展开结果允许的最大字符数。
     * @return 正文及本次使用的绑定。
     * @throws MacroExpansionException.Cycle 当前引用链形成环。
     * @throws MacroExpansionException.OutputLimit 展开结果超过字符预算。
     */
    fun expand(
        template: String,
        scope: MacroScope,
        maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS,
    ): MacroExpansion {
        require(maxOutputChars >= 0) { "Macro output limit cannot be negative" }
        return ExpansionSession(maxOutputChars).expand(template, scope)
    }

    /**
     * 读取模板直接包含的引用，供格式包装器判断是否存在正文槽。
     * @param template 待扫描的模板正文。
     * @return 按出现顺序提供的直接引用，不递归访问宏定义。
     */
    fun references(template: String): Sequence<MacroReference> =
        mPattern.findAll(template).map(::reference)

    private fun reference(match: MatchResult): MacroReference = MacroReference.parse(
        mAliases[match.value.lowercase(Locale.ROOT)] ?: match.groupValues[1]
    )

    /** 单次展开只访问可达定义，完成节点组成有向无环的共享文本结构。 */
    private inner class ExpansionSession(private val mLimit: Int) {
        private val mStack = ArrayDeque<Frame>()
        private val mActive = LinkedHashSet<MacroKey>()
        private val mCompleted = HashMap<MacroKey, Text>()
        private val mLookups = HashMap<MacroKey, Pair<MacroKey, MacroValue>?>()
        private val mUsed = LinkedHashSet<MacroKey>()

        /** 求值可达定义，成功后一次性生成最终字符串。 */
        fun expand(template: String, scope: MacroScope): MacroExpansion {
            mStack.addLast(Frame(template, scope))
            // 每个定义只解析一次；返回子定义前保留父帧的扫描位置。
            while (mStack.isNotEmpty()) {
                val frame = mStack.last()
                if (!frame.matches.hasNext()) {
                    frame.append(Leaf(frame.template, frame.position, frame.template.length))
                    val text = frame.finish()
                    mStack.removeLast()
                    frame.key?.let { key ->
                        mActive.remove(key)
                        mCompleted[key] = text
                    }
                    if (mStack.isEmpty()) return MacroExpansion(flatten(text), mUsed)
                    mStack.last().append(text)
                } else {
                    expandReference(frame, frame.matches.next())
                }
            }
            error("Macro expansion has no root frame")
        }

        /** 查找宏并进入其定义；已完成节点直接复用，活跃节点返回完整循环链。 */
        private fun expandReference(frame: Frame, match: MatchResult) {
            frame.append(Leaf(frame.template, frame.position, match.range.first))
            frame.position = match.range.last + 1
            val reference = reference(match)
            val binding = lookup(frame.scope, reference)
            if (binding == null) {
                frame.append(Leaf(match.value))
                return
            }
            val (key, value) = binding
            mUsed += key
            mCompleted[key]?.let { text ->
                frame.append(text)
                return
            }
            when (value) {
                is MacroValue.Literal -> {
                    val text = Leaf(value.text)
                    mCompleted[key] = text
                    frame.append(text)
                }

                is MacroValue.Template -> {
                    // 仅活跃路径算作环；相同宏在兄弟位置的重复引用是正常依赖。
                    if (!mActive.add(key)) {
                        throw MacroExpansionException.Cycle(mActive.dropWhile { it != key } + key)
                    }
                    mStack.addLast(Frame(value.text, value.scope ?: key.scope, key))
                }
            }
        }

        /** 路径上的父级查找结果一并缓存，每个作用域与引用对只查找一次。 */
        private fun lookup(
            scope: MacroScope,
            reference: MacroReference
        ): Pair<MacroKey, MacroValue>? {
            val visited = ArrayList<MacroKey>()
            var current: MacroScope? = scope
            var binding: Pair<MacroKey, MacroValue>? = null
            // 查找保留定义层的身份，子层的同名覆盖不会改变父模板的依赖。
            while (current != null) {
                val key = MacroKey(current, reference)
                if (mLookups.containsKey(key)) {
                    binding = mLookups[key]
                    break
                }
                visited += key
                val value = current.localValue(reference)
                if (value != null) {
                    binding = key to value
                    break
                }
                current = current.parent
            }
            visited.forEach { mLookups[it] = binding }
            return binding
        }

        /** 按序遍历共享片段，只为最终输出复制字符，避免各层各存一份展开字符串。 */
        private fun flatten(text: Text): String {
            val output = StringBuilder(text.length)
            val pending = ArrayDeque<Text>()
            pending.addLast(text)
            while (pending.isNotEmpty()) {
                when (val part = pending.removeLast()) {
                    is Leaf -> output.append(part.value, part.start, part.end)
                    is Branch -> part.parts.asReversed().forEach(pending::addLast)
                }
            }
            return output.toString()
        }

        private inner class Frame(
            val template: String,
            val scope: MacroScope,
            val key: MacroKey? = null,
        ) {
            val matches = mPattern.findAll(template).iterator()
            var position = 0
            private val mParts = ArrayList<Text>()
            private var mLength = 0

            /** 追加前检查长度，重复引用也按其实际输出大小计费。 */
            fun append(text: Text) {
                if (text.length > mLimit - mLength) throw MacroExpansionException.OutputLimit(mLimit)
                if (text.length == 0) return
                mLength += text.length
                mParts += text
            }

            /** 单子节点直接复用，避免纯别名长链在输出时逐层遍历。 */
            fun finish(): Text = when (mParts.size) {
                0 -> Leaf("")
                1 -> mParts.single()
                else -> Branch(mParts, mLength)
            }
        }
    }

    private sealed interface Text {
        val length: Int
    }

    private class Leaf(
        val value: String,
        val start: Int = 0,
        val end: Int = value.length,
    ) : Text {
        override val length: Int get() = end - start
    }

    private class Branch(val parts: List<Text>, override val length: Int) : Text

    companion object {
        /** 独立于 Token 预算的字符分配上限；调用方可按用途传入更小的预算。 */
        const val DEFAULT_MAX_OUTPUT_CHARS = 16 * 1024 * 1024
    }
}
