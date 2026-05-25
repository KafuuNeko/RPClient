package me.kafuuneko.rpclient.libs.utils

fun <T> List<T>.updateAt(index: Int, value: T): List<T> {
    if (index !in indices) return this
    return mapIndexed { currentIndex, item ->
        if (currentIndex == index) value else item
    }
}

fun <T> List<T>.removeAtOrSelf(index: Int): List<T> {
    if (index !in indices) return this
    return filterIndexed { currentIndex, _ -> currentIndex != index }
}

fun List<String>.orSingleBlank(): List<String> {
    return ifEmpty { listOf("") }
}

fun List<String>.trimmedNotBlank(): List<String> {
    return map { it.trim() }.filter { it.isNotEmpty() }
}

fun <T> Set<T>.toggle(item: T): Set<T> {
    return if (item in this) this - item else this + item
}

fun <T> Set<T>.toggleAll(items: Set<T>): Set<T> {
    return if (items.all { it in this }) this - items else this + items
}
