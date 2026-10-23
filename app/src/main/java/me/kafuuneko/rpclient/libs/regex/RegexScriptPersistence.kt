package me.kafuuneko.rpclient.libs.regex

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.room.entity.RegexScriptEntity
import java.util.UUID

/** 将兼容外部格式的领域模型映射为 Room 实体。 */
internal fun RegexScript.toEntity(
    characterId: Long?,
    sortOrder: Int,
    gson: Gson,
): RegexScriptEntity {
    return RegexScriptEntity(
        characterId = characterId,
        scriptId = id,
        sortOrder = sortOrder,
        scriptName = scriptName,
        findRegex = findRegex,
        replaceString = replaceString,
        trimStringsJson = gson.toJson(trimStrings),
        placementJson = gson.toJson(placement),
        disabled = disabled,
        markdownOnly = markdownOnly,
        promptOnly = promptOnly,
        runOnEdit = runOnEdit,
        substituteRegex = substituteRegex,
        minDepth = minDepth,
        maxDepth = maxDepth,
        rawJson = rawJson,
    )
}

/** 将 Room 实体恢复为 Regex 执行和导出使用的领域模型。 */
internal fun RegexScriptEntity.toDomain(): RegexScript {
    return RegexScript(
        id = scriptId,
        scriptName = scriptName,
        findRegex = findRegex,
        replaceString = replaceString,
        trimStrings = trimStringsJson.parseStringList(),
        placement = placementJson.parseIntList(),
        disabled = disabled,
        markdownOnly = markdownOnly,
        promptOnly = promptOnly,
        runOnEdit = runOnEdit,
        substituteRegex = substituteRegex,
        minDepth = minDepth,
        maxDepth = maxDepth,
        rawJson = rawJson,
    )
}

/**
 * 修复同一持久化目标中的空白或重复外部 ID。
 *
 * ID 是 Regex 管理页的稳定交互键，因此所有外部数据进入 Room 前都必须在各自作用域内唯一。
 */
internal fun List<RegexScript>.normalizeRegexScriptIds(
    reservedIds: MutableSet<String> = mutableSetOf(),
    createId: () -> String = { UUID.randomUUID().toString() },
): List<RegexScript> {
    return map { script ->
        if (script.id.isBlank() || !reservedIds.add(script.id)) {
            var generated = createId()
            while (generated.isBlank() || !reservedIds.add(generated)) {
                generated = createId()
            }
            script.copy(id = generated)
        } else {
            script
        }
    }
}

private fun String.parseStringList(): List<String> {
    return runCatching {
        JsonParser.parseString(this).asJsonArray.mapNotNull { element ->
            element.takeIf { element -> !element.isJsonNull }?.asString
        }
    }.getOrDefault(emptyList())
}

private fun String.parseIntList(): List<Int> {
    return runCatching {
        JsonParser.parseString(this).asJsonArray.mapNotNull { element ->
            runCatching { element.takeIf { element -> !element.isJsonNull }?.asInt }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
