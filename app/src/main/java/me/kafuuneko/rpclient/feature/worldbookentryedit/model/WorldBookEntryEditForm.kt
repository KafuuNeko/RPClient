package me.kafuuneko.rpclient.feature.worldbookentryedit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.utils.orSingleBlank
import me.kafuuneko.rpclient.utils.trimmedNotBlank
import me.kafuuneko.rpclient.utils.toJsonString

/**
 * 世界书条目编辑表单。
 *
 * 数值字段使用字符串保存，以完整表达 Compose 输入过程；可空布尔值表示继承世界书
 * 或兼容层默认值。保存前由 [toLorebookEntryOrNull] 统一校验和转换。
 */
data class WorldBookEntryEditForm(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long = 0L,
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long = 0L,
    /** 供界面展示和业务识别的名称。 */
    val name: String = "",
    /** 用于触发世界书条目的主关键词列表。 */
    val keywords: List<String> = listOf(""),
    /** 与主关键词共同参与筛选的次要关键词列表。 */
    val secondaryKeywords: List<String> = listOf(""),
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean = false,
    /** 当前对象在同类数据中的排序值。 */
    val order: String = "100",
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: String = "4",
    /** 当前世界书条目的分类名称。 */
    val category: List<String> = listOf(""),
    /** 当前对象承载的正文内容。 */
    val content: String = "",
    /** 当前对象或功能是否禁用。 */
    val disabled: Boolean = false,
    /** 当前对象在所属结构中的位置。 */
    val position: String = LorebookEntry.POSITION_AT_DEPTH.toString(),
    /** 世界书内容注入 Prompt 时采用的消息角色。 */
    val role: String = LorebookEntry.ROLE_SYSTEM.toString(),
    /** 世界书条目命中后通过随机激活的概率。 */
    val probability: String = "100",
    /** 当前世界书条目是否不受普通 Token 预算淘汰。 */
    val ignoreBudget: Boolean = false,
    /** 世界书关键词扫描的历史消息深度。 */
    val scanDepth: String = "",
    /** 主关键词与次要关键词采用的组合匹配规则。 */
    val selectiveLogic: String = LorebookEntry.LOGIC_AND_ANY.toString(),
    /** 普通关键词是否只匹配完整单词。 */
    val matchWholeWords: Boolean? = null,
    /** 世界书关键词匹配是否区分大小写。 */
    val caseSensitive: Boolean? = null,
    /** 互斥分组是否累计关键词命中分数参与选择。 */
    val useGroupScoring: Boolean = false,
    /** 世界书条目所属的互斥分组名称。 */
    val group: String = "",
    /** 当前条目命中时是否覆盖同组普通候选。 */
    val groupOverride: Boolean = false,
    /** 同组候选随机选择时使用的权重。 */
    val groupWeight: String = "",
    /** 当前条目是否阻止其内容触发后续递归扫描。 */
    val preventRecursion: Boolean = false,
    /** 世界书条目是否延迟到递归扫描阶段才允许激活。 */
    val delayUntilRecursion: Boolean = false,
    /** 世界书条目命中后继续保持激活的生成轮数。 */
    val sticky: String = "",
    /** 世界书条目失活后禁止再次激活的生成轮数。 */
    val cooldown: String = "",
    /** 世界书条目允许首次激活前需要等待的生成轮数。 */
    val delay: String = "",
    /** 自定义世界书插槽的名称。 */
    val outletName: String = "",
    /** 用于记录条目激活原因的匹配结果列表。 */
    val triggers: List<String> = listOf(""),
    /** 世界书扫描是否包含用户设定。 */
    val matchPersonaDescription: Boolean = false,
    /** 世界书扫描是否包含角色描述。 */
    val matchCharacterDescription: Boolean = false,
    /** 世界书扫描是否包含角色性格设定。 */
    val matchCharacterPersonality: Boolean = false,
    /** 世界书扫描是否包含角色附加提示词。 */
    val matchCharacterDepthPrompt: Boolean = false,
    /** 世界书扫描是否包含场景设定。 */
    val matchScenario: Boolean = false,
    /** 世界书扫描是否包含作者备注。 */
    val matchCreatorNotes: Boolean = false,
    /** 用于兼容第三方格式的扩展字段 JSON。 */
    val extensionsJson: String = "{}",
    /** 未经业务转换的原始 JSON 文本。 */
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
