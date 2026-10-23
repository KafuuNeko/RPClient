package me.kafuuneko.rpclient.utils

/** 创建页共用的角色字面匹配规则，保留整段查询、忽略大小写及原列表顺序。 */
fun matchesCharacterQuery(
    name: String,
    description: String,
    tags: List<String>,
    query: String,
): Boolean {
    val text = query.trim()
    return text.isEmpty() || name.contains(other = text, ignoreCase = true)
        || description.contains(other = text, ignoreCase = true) || tags.any { textValue ->
        textValue.contains(
            other = text,
            ignoreCase = true,
        )
    }
}
