package me.kafuuneko.rpclient.feature.worldbookentryedit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.utils.orSingleBlank
import me.kafuuneko.rpclient.libs.utils.trimmedNotBlank
import me.kafuuneko.rpclient.utils.toJsonString

/**
 * 世界书条目编辑表单。
 *
 * 数值字段使用字符串保存，以完整表达 Compose 输入过程；可空布尔值表示继承世界书
 * 或兼容层默认值。保存前由 [toLorebookEntryOrNull] 统一校验和转换。
 */
data class WorldBookEntryEditForm(
    val id: Long = 0L,
    val lorebookId: Long = 0L,
    val name: String = "",
    val keywords: List<String> = listOf(""),
    val secondaryKeywords: List<String> = listOf(""),
    val constant: Boolean = false,
    val order: String = "100",
    val depth: String = "4",
    val category: List<String> = listOf(""),
    val content: String = "",
    val disabled: Boolean = false,
    val position: String = LorebookEntry.POSITION_AT_DEPTH.toString(),
    val role: String = LorebookEntry.ROLE_SYSTEM.toString(),
    val probability: String = "100",
    val ignoreBudget: Boolean = false,
    val scanDepth: String = "",
    val selectiveLogic: String = LorebookEntry.LOGIC_AND_ANY.toString(),
    val matchWholeWords: Boolean? = null,
    val caseSensitive: Boolean? = null,
    val useGroupScoring: Boolean = false,
    val group: String = "",
    val groupOverride: Boolean = false,
    val groupWeight: String = "",
    val preventRecursion: Boolean = false,
    val delayUntilRecursion: Boolean = false,
    val sticky: String = "",
    val cooldown: String = "",
    val delay: String = "",
    val outletName: String = "",
    val triggers: List<String> = listOf(""),
    val matchPersonaDescription: Boolean = false,
    val matchCharacterDescription: Boolean = false,
    val matchCharacterPersonality: Boolean = false,
    val matchCharacterDepthPrompt: Boolean = false,
    val matchScenario: Boolean = false,
    val matchCreatorNotes: Boolean = false,
    val extensionsJson: String = "{}",
    val rawJson: String = "{}"
) {
    /** id 为 0 表示尚未持久化的新条目。 */
    val isNew: Boolean
        get() = id == 0L

    companion object {
        fun from(entry: LorebookEntry): WorldBookEntryEditForm {
            return WorldBookEntryEditForm(
                id = entry.id,
                lorebookId = entry.lorebookId,
                name = entry.name,
                keywords = entry.getKeywordList().orSingleBlank(),
                secondaryKeywords = entry.getSecondaryKeywordList().orSingleBlank(),
                constant = entry.constant,
                order = entry.order.toString(),
                depth = entry.depth.toString(),
                category = entry.getCategoryList().orSingleBlank(),
                content = entry.content,
                disabled = entry.disabled,
                position = entry.position.toString(),
                role = entry.role.toString(),
                probability = entry.probability.toString(),
                ignoreBudget = entry.ignoreBudget,
                scanDepth = entry.scanDepth?.toString().orEmpty(),
                selectiveLogic = entry.selectiveLogic.toString(),
                matchWholeWords = entry.matchWholeWords,
                caseSensitive = entry.caseSensitive,
                useGroupScoring = entry.useGroupScoring,
                group = entry.group,
                groupOverride = entry.groupOverride,
                groupWeight = entry.groupWeight?.toString().orEmpty(),
                preventRecursion = entry.preventRecursion,
                delayUntilRecursion = entry.delayUntilRecursion,
                sticky = entry.sticky?.toString().orEmpty(),
                cooldown = entry.cooldown?.toString().orEmpty(),
                delay = entry.delay?.toString().orEmpty(),
                outletName = entry.outletName,
                triggers = entry.getTriggerList().orSingleBlank(),
                matchPersonaDescription = entry.matchPersonaDescription,
                matchCharacterDescription = entry.matchCharacterDescription,
                matchCharacterPersonality = entry.matchCharacterPersonality,
                matchCharacterDepthPrompt = entry.matchCharacterDepthPrompt,
                matchScenario = entry.matchScenario,
                matchCreatorNotes = entry.matchCreatorNotes,
                extensionsJson = entry.extensionsJson,
                rawJson = entry.rawJson
            )
        }
    }

    /** 将表单转换为实体；必要数字字段无法解析时返回 null。 */
    fun toLorebookEntryOrNull(): LorebookEntry? {
        val orderValue = order.trim().toIntOrNull() ?: return null
        val depthValue = depth.trim().toIntOrNull() ?: return null
        val positionValue = position.trim().toIntOrNull() ?: return null
        val roleValue = role.trim().toIntOrNull() ?: return null
        val probabilityValue = probability.trim().toIntOrNull() ?: return null
        val selectiveLogicValue = selectiveLogic.trim().toIntOrNull() ?: return null
        return LorebookEntry(
            id = id,
            lorebookId = lorebookId,
            name = name.trim(),
            keywords = Gson().toJsonString(keywords.cleanList()),
            secondaryKeywords = Gson().toJsonString(secondaryKeywords.cleanList()),
            constant = constant,
            order = orderValue,
            depth = depthValue,
            category = Gson().toJsonString(category.cleanList()),
            content = content.trim(),
            disabled = disabled,
            position = positionValue,
            role = roleValue,
            probability = probabilityValue.coerceIn(0, 100),
            ignoreBudget = ignoreBudget,
            scanDepth = scanDepth.trim().toIntOrNull(),
            selectiveLogic = selectiveLogicValue,
            matchWholeWords = matchWholeWords,
            caseSensitive = caseSensitive,
            useGroupScoring = useGroupScoring,
            group = group.trim(),
            groupOverride = groupOverride,
            groupWeight = groupWeight.trim().toIntOrNull(),
            preventRecursion = preventRecursion,
            delayUntilRecursion = delayUntilRecursion,
            sticky = sticky.trim().toIntOrNull(),
            cooldown = cooldown.trim().toIntOrNull(),
            delay = delay.trim().toIntOrNull(),
            outletName = outletName.trim(),
            triggers = Gson().toJsonString(triggers.cleanList()),
            matchPersonaDescription = matchPersonaDescription,
            matchCharacterDescription = matchCharacterDescription,
            matchCharacterPersonality = matchCharacterPersonality,
            matchCharacterDepthPrompt = matchCharacterDepthPrompt,
            matchScenario = matchScenario,
            matchCreatorNotes = matchCreatorNotes,
            extensionsJson = extensionsJson.trim().ifBlank { "{}" },
            rawJson = rawJson
        )
    }
}

/** 规范化列表与文本输入，供未保存修改比较使用。 */
fun WorldBookEntryEditForm.toComparableForm(): WorldBookEntryEditForm {
    return copy(
        name = name.trim(),
        keywords = keywords.cleanList(),
        secondaryKeywords = secondaryKeywords.cleanList(),
        constant = constant,
        order = order.trim(),
        depth = depth.trim(),
        category = category.cleanList(),
        content = content.trim(),
        position = position.trim(),
        role = role.trim(),
        probability = probability.trim(),
        scanDepth = scanDepth.trim(),
        selectiveLogic = selectiveLogic.trim(),
        group = group.trim(),
        groupWeight = groupWeight.trim(),
        sticky = sticky.trim(),
        cooldown = cooldown.trim(),
        delay = delay.trim(),
        outletName = outletName.trim(),
        triggers = triggers.cleanList(),
        extensionsJson = extensionsJson.trim().ifBlank { "{}" }
    )
}

/** 判断当前条目表单是否偏离初始快照。 */
fun WorldBookEntryEditForm.hasUnsavedChangesFrom(initialForm: WorldBookEntryEditForm): Boolean {
    return toComparableForm() != initialForm.toComparableForm()
}

private fun List<String>.cleanList(): List<String> {
    return trimmedNotBlank()
}
