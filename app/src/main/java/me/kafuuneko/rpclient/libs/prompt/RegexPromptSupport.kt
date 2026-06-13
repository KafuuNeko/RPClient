package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 对激活结果中的每个世界书条目执行一次内容变换，并同步更新所有位置分组。
 *
 * 使用 ID 建立映射可保证同一条目在 activatedEntries、depthEntries 和 outletEntries
 * 中得到完全相同的 Regex 处理结果。
 */
internal fun WorldBookActivationResult.mapEntryContent(
    transform: (LorebookEntry) -> LorebookEntry
): WorldBookActivationResult {
    val transformed = activatedEntries.associate { entry ->
        entry.id to transform(entry)
    }
    fun List<LorebookEntry>.mapped(): List<LorebookEntry> =
        map { transformed[it.id] ?: it }

    return copy(
        activatedEntries = activatedEntries.mapped(),
        beforeCharacter = beforeCharacter.mapped(),
        afterCharacter = afterCharacter.mapped(),
        exampleBefore = exampleBefore.mapped(),
        exampleAfter = exampleAfter.mapped(),
        anTop = anTop.mapped(),
        anBottom = anBottom.mapped(),
        depthEntries = depthEntries.map { group ->
            group.copy(entries = group.entries.mapped().toMutableList())
        },
        outletEntries = outletEntries.mapValues { (_, entries) -> entries.mapped() }
    )
}
