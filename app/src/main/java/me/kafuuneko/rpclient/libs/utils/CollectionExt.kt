package me.kafuuneko.rpclient.libs.utils

/** 返回替换指定下标后的新列表；下标无效时保持原列表。 */
fun <T> List<T>.updateAt(index: Int, value: T): List<T> {
    if (index !in indices) return this
    return mapIndexed { currentIndex, item ->
        if (currentIndex == index) value else item
    }
}

/** 返回删除指定下标后的新列表；下标无效时保持原列表。 */
fun <T> List<T>.removeAtOrSelf(index: Int): List<T> {
    if (index !in indices) return this
    return filterIndexed { currentIndex, _ -> currentIndex != index }
}

/** 空列表转换为单个空字符串，便于动态输入框始终保留一行。 */
fun List<String>.orSingleBlank(): List<String> {
    return ifEmpty { listOf("") }
}

/** 去除每项首尾空白并过滤空项。 */
fun List<String>.trimmedNotBlank(): List<String> {
    return map { it.trim() }.filter { it.isNotEmpty() }
}

/** 切换集合中指定元素的存在状态。 */
fun <T> Set<T>.toggle(item: T): Set<T> {
    return if (item in this) this - item else this + item
}

/** 若目标元素已全部选中则全部移除，否则全部加入。 */
fun <T> Set<T>.toggleAll(items: Set<T>): Set<T> {
    return if (items.all { it in this }) this - items else this + items
}
