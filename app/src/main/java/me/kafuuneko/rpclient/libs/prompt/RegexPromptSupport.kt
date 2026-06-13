package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

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
