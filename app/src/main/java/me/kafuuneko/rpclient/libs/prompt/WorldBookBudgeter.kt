package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/** 世界书预算裁剪结果，以及未被选中的条目记录。 */
internal data class WorldInfoSelection(
    val result: WorldBookActivationResult,
    val omittedItems: List<PromptOmittedItem>
)

/**
 * 按 SillyTavern 的规则计算本轮世界书预算。
 *
 * 计算逻辑：
 * - 先按扣除回复预留后的输入预算计算百分比；
 * - 再应用可选的固定 Token 上限（上限为 0 时不限制）；
 * - 百分比结果按四舍五入计算，并至少保留 1 Token。
 */
internal fun resolveWorldInfoBudget(
    promptTokenBudget: Int,
    contextPercent: Int,
    tokenBudgetCap: Int,
): Int {
    val normalizedPromptBudget = promptTokenBudget.coerceAtLeast(0)
    val normalizedPercent = contextPercent.coerceIn(minimumValue = 0, maximumValue = 100)
    val percentageBudget = (
        (normalizedPromptBudget.toLong() * normalizedPercent + 50L) / 100L
        ).toInt().coerceAtLeast(1)
    return if (tokenBudgetCap in 1..<percentageBudget) {
        tokenBudgetCap
    } else {
        percentageBudget
    }
}

/**
 * 同时应用全局世界书预算与每本世界书的独立预算。
 *
 * 拟合规则：
 * - 常驻和高顺序条目优先选择；
 * - [LorebookEntry.ignoreBudget] 仅跳过预算占用计数，不改变条目的激活和插入语义。
 */
internal fun fitWorldInfoToBudget(
    result: WorldBookActivationResult,
    globalTokenBudget: Int,
    lorebooks: Map<Long, Lorebook>,
    tokenizer: PromptTokenizer,
): WorldInfoSelection {
    val selected = mutableListOf<LorebookEntry>()
    val omitted = mutableListOf<PromptOmittedItem>()
    val usedByLorebook = mutableMapOf<Long, Int>()
    var globalUsedTokens = 0

    // 按常驻优先、Order 降序、ID 升序进行预算分配
    result.activatedEntries
        .sortedWith(
            comparator = compareByDescending<LorebookEntry> { it.constant }
                .thenByDescending { it.order }
                .thenBy { it.id },
        )
        .forEach { entry ->
            val nextTokens = tokenizer.countText(entry.content)
            val lorebookBudget = lorebooks[entry.lorebookId]
                ?.resolveTokenBudget()
            val lorebookUsedTokens = usedByLorebook[entry.lorebookId] ?: 0
            // 校验是否超出全局预算或单本世界书预算
            val exceedsGlobalBudget = globalUsedTokens + nextTokens >= globalTokenBudget
            val exceedsLorebookBudget = lorebookBudget != null
                && lorebookUsedTokens + nextTokens > lorebookBudget

            // 超出预算且未忽略预算时记录遗漏并跳过
            if (!entry.ignoreBudget && (exceedsGlobalBudget || exceedsLorebookBudget)) {
                omitted += PromptOmittedItem(
                    source = PromptSource(
                        kind = PromptSourceKind.WorldInfo,
                        detail = entry.name,
                        referenceId = entry.id,
                    ),
                    tokenCount = nextTokens,
                    reason = PromptOmissionReason.WorldInfoBudget,
                )
                return@forEach
            }

            // 纳入当前条目并累加已用 Token
            selected += entry
            if (!entry.ignoreBudget) {
                globalUsedTokens += nextTokens
                usedByLorebook[entry.lorebookId] = lorebookUsedTokens + nextTokens
            }
        }

    // 重构按位置分组的裁剪结果
    val selectedIds = selected.map { it.id }.toSet()
    return WorldInfoSelection(
        result = result.filterEntries(selectedIds),
        omittedItems = omitted,
    )
}

/** 解析单本世界书配置的独立 Token 预算（<= 0 表示不限制）。 */
private fun Lorebook.resolveTokenBudget(): Int? {
    // 0 表示跟随全局预算，不对这本世界书增加第二层限制。
    if (tokenBudget <= 0) return null
    return tokenBudget
}

/**
 * 最终 Prompt 预算还可能移除世界书消息；时序状态只能包含实际保留的条目。
 *
 * Outlet 内容会通过其他 Prompt 的宏展开注入，无法从最终消息来源反推，因此沿用其激活结果。
 */
internal fun WorldBookActivationResult.retainStateEntries(
    inspection: PromptInspection,
): WorldBookActivationResult {
    val retainedIds = inspection.items
        .flatMap { it.sources }
        .filter { it.kind == PromptSourceKind.WorldInfo }
        .mapNotNull { it.referenceId }
        .toMutableSet()
    outletEntries.values.flatten().forEach { retainedIds += it.id }
    return copy(activatedEntries = activatedEntries.filter { it.id in retainedIds })
}

/**
 * 用同一 ID 集合裁剪激活结果的所有位置索引。
 *
 * 不能只过滤 [WorldBookActivationResult.activatedEntries]，否则已被预算移除的条目仍可能
 * 从 depth、示例或 outlet 分组注入最终 Prompt。
 */
internal fun WorldBookActivationResult.filterEntries(
    selectedIds: Set<Long>,
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
            val entries = group.entries.filter { entry -> entry.id in selectedIds }.toMutableList()
            if (entries.isEmpty()) null else group.copy(entries = entries)
        },
        outletEntries = outletEntries.mapValues { (_, entries) ->
            entries.filter { entry -> entry.id in selectedIds }
        }.filterValues { it.isNotEmpty() },
    )
}

/**
 * 在预算计算前排除用户明确禁用的示例位置条目。
 *
 * 这些条目并非因预算不足而遗漏，因此不能进入遗漏记录，也不能推进世界书时序状态。
 */
internal fun WorldBookActivationResult.filterForExampleBehavior(
    behavior: ExampleDialogueBehavior,
): WorldBookActivationResult {
    if (behavior != ExampleDialogueBehavior.Disabled) return this
    val disabledIds = (exampleBefore + exampleAfter).mapTo(mutableSetOf()) { it.id }
    if (disabledIds.isEmpty()) return this
    val selectedIds = activatedEntries
        .mapNotNullTo(mutableSetOf()) { entry -> entry.id.takeUnless(disabledIds::contains) }
    return filterEntries(selectedIds)
}
