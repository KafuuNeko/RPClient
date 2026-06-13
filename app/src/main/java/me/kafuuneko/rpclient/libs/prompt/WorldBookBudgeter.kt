package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

internal data class WorldInfoSelection(
    val result: WorldBookActivationResult,
    val omittedItems: List<PromptOmittedItem>
)

internal fun fitWorldInfoToBudget(
    result: WorldBookActivationResult,
    globalTokenBudget: Int,
    promptTokenBudget: Int,
    lorebooks: Map<Long, Lorebook>,
    tokenizer: PromptTokenizer
): WorldInfoSelection {
    val selected = mutableListOf<LorebookEntry>()
    val omitted = mutableListOf<PromptOmittedItem>()
    val usedByLorebook = mutableMapOf<Long, Int>()
    var globalUsedTokens = 0

    result.activatedEntries
        .sortedWith(
            compareByDescending<LorebookEntry> { it.constant }
                .thenByDescending { it.order }
                .thenBy { it.id }
        )
        .forEach { entry ->
            val nextTokens = tokenizer.countText(entry.content)
            val lorebookBudget = lorebooks[entry.lorebookId]
                ?.resolveTokenBudget(promptTokenBudget)
            val lorebookUsedTokens = usedByLorebook[entry.lorebookId] ?: 0
            val exceedsGlobalBudget = globalUsedTokens + nextTokens > globalTokenBudget
            val exceedsLorebookBudget = lorebookBudget != null &&
                lorebookUsedTokens + nextTokens > lorebookBudget

            if (!entry.ignoreBudget && (exceedsGlobalBudget || exceedsLorebookBudget)) {
                omitted += PromptOmittedItem(
                    source = PromptSource(
                        kind = PromptSourceKind.WorldInfo,
                        detail = entry.name,
                        referenceId = entry.id
                    ),
                    tokenCount = nextTokens,
                    reason = PromptOmissionReason.WorldInfoBudget
                )
                return@forEach
            }

            selected += entry
            if (!entry.ignoreBudget) {
                globalUsedTokens += nextTokens
                usedByLorebook[entry.lorebookId] = lorebookUsedTokens + nextTokens
            }
        }

    val selectedIds = selected.map { it.id }.toSet()
    return WorldInfoSelection(
        result = result.filterEntries(selectedIds),
        omittedItems = omitted
    )
}

private fun Lorebook.resolveTokenBudget(promptTokenBudget: Int): Int {
    if (tokenBudget <= 0) return 0
    // 兼容项目既有 25% 默认值；大于 100 的 Character Book 预算按绝对 Token 上限处理。
    return if (tokenBudget <= 100) {
        promptTokenBudget * tokenBudget / 100
    } else {
        tokenBudget
    }
}

/**
 * 最终 Prompt 预算还可能移除世界书消息；时序状态只能包含实际保留的条目。
 *
 * Outlet 内容会通过其他 Prompt 的宏展开注入，无法从最终消息来源反推，因此沿用其激活结果。
 */
internal fun WorldBookActivationResult.retainStateEntries(
    inspection: PromptInspection
): WorldBookActivationResult {
    val retainedIds = inspection.items
        .flatMap { it.sources }
        .filter { it.kind == PromptSourceKind.WorldInfo }
        .mapNotNull { it.referenceId }
        .toMutableSet()
    outletEntries.values.flatten().forEach { retainedIds += it.id }
    return copy(
        activatedEntries = activatedEntries.filter { it.id in retainedIds }
    )
}

internal fun WorldBookActivationResult.filterEntries(
    selectedIds: Set<Long>
): WorldBookActivationResult {
    return copy(
        activatedEntries = activatedEntries.filter { it.id in selectedIds },
        beforeCharacter = beforeCharacter.filter { it.id in selectedIds },
        afterCharacter = afterCharacter.filter { it.id in selectedIds },
        exampleBefore = exampleBefore.filter { it.id in selectedIds },
        exampleAfter = exampleAfter.filter { it.id in selectedIds },
        anTop = anTop.filter { it.id in selectedIds },
        anBottom = anBottom.filter { it.id in selectedIds },
        depthEntries = depthEntries.mapNotNull { group ->
            val entries = group.entries.filter { it.id in selectedIds }.toMutableList()
            if (entries.isEmpty()) null else group.copy(entries = entries)
        },
        outletEntries = outletEntries.mapValues { (_, entries) ->
            entries.filter { it.id in selectedIds }
        }.filterValues { it.isNotEmpty() }
    )
}
