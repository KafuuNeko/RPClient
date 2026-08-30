package me.kafuuneko.rpclient.feature.worldbookedit.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 世界书元数据编辑表单。
 *
 * 条目正文由独立页面编辑，此处仅保存用于列表展示的条目快照。
 */
data class WorldBookEditForm(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long = 0L,
    /** 供界面展示和业务识别的名称。 */
    val name: String = "",
    /** 用于说明当前对象的描述文本。 */
    val description: String = "",
    /** 世界书关键词扫描的历史消息深度。 */
    val scanDepth: Int = 2,
    /** 世界书采用按比例还是固定 Token 的预算模式。 */
    val tokenBudgetMode: WorldBookBudgetMode = WorldBookBudgetMode.FollowGlobal,
    /** 世界书固定预算模式下的用户输入文本。 */
    val tokenBudgetInput: String = "",
    /** 当前世界书是否允许递归激活扫描。 */
    val recursiveScanning: Boolean = false,
    /** 用于兼容第三方格式的扩展字段 JSON。 */
    val extensionsJson: String = "{}",
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<WorldBookEntryListItem> = emptyList()
) {
    /** id 为 0 表示尚未持久化的新世界书。 */
    val isNew: Boolean
        get() = id == 0L

    /** 解析后的单本固定 Token 上限；固定模式输入无效时返回 null。 */
    val resolvedTokenBudget: Int?
        get() = when (tokenBudgetMode) {
            WorldBookBudgetMode.FollowGlobal -> 0
            WorldBookBudgetMode.FixedTokens -> tokenBudgetInput.toIntOrNull()
                ?.takeIf { it > 0 }
        }

    companion object {
        /** 从世界书实体及其条目构造可编辑表单。 */
        fun from(lorebook: Lorebook, entries: List<LorebookEntry>): WorldBookEditForm {
            return WorldBookEditForm(
                id = lorebook.id,
                name = lorebook.name,
                description = lorebook.description,
                scanDepth = lorebook.scanDepth,
                tokenBudgetMode = if (lorebook.tokenBudget > 0) {
                    WorldBookBudgetMode.FixedTokens
                } else {
                    WorldBookBudgetMode.FollowGlobal
                },
                tokenBudgetInput = lorebook.tokenBudget
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty(),
                recursiveScanning = lorebook.recursiveScanning,
                extensionsJson = lorebook.extensionsJson,
                entries = entries.map { WorldBookEntryListItem.from(it) }
            )
        }
    }

    /** 将表单转换为可保存实体，并规范化名称首尾空白。 */
    fun toLorebook(): Lorebook {
        val tokenBudget = requireNotNull(resolvedTokenBudget) {
            "A fixed lorebook token budget must be a positive integer."
        }
        return Lorebook(
            id = id,
            name = name.trim(),
            description = description,
            scanDepth = scanDepth,
            tokenBudget = tokenBudget,
            recursiveScanning = recursiveScanning,
            extensionsJson = extensionsJson
        )
    }
}

/** 单本世界书预算的编辑模式。 */
enum class WorldBookBudgetMode {
    FollowGlobal,
    FixedTokens
}

/** 世界书编辑页中的条目摘要。 */
data class WorldBookEntryListItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于触发世界书条目的主关键词列表。 */
    val keywords: List<String>,
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean,
    /** 当前对象或功能是否禁用。 */
    val disabled: Boolean = false,
    /** 当前对象在同类数据中的排序值。 */
    val order: Int,
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: Int
) {
    companion object {
        fun from(entry: LorebookEntry): WorldBookEntryListItem {
            return WorldBookEntryListItem(
                id = entry.id,
                name = entry.name,
                keywords = entry.getKeywordList(),
                constant = entry.constant,
                disabled = entry.disabled,
                order = entry.order,
                depth = entry.depth
            )
        }
    }
}

/** 返回更新指定条目禁用状态后的不可变表单；找不到条目时保持原值。 */
fun WorldBookEditForm.withEntryDisabled(entryId: Long, disabled: Boolean): WorldBookEditForm {
    val index = entries.indexOfFirst { it.id == entryId }
    if (index < 0 || entries[index].disabled == disabled) return this
    return copy(
        entries = entries.toMutableList().apply {
            this[index] = this[index].copy(disabled = disabled)
        }
    )
}

/** 生成用于脏数据比较的规范化表单。 */
fun WorldBookEditForm.toComparableForm(): WorldBookEditForm {
    return copy(
        name = name.trim(),
        tokenBudgetInput = when (tokenBudgetMode) {
            WorldBookBudgetMode.FollowGlobal -> ""
            WorldBookBudgetMode.FixedTokens -> resolvedTokenBudget
                ?.toString()
                ?: tokenBudgetInput.trim()
        }
    )
}

/** 判断表单是否包含需要用户确认放弃的修改。 */
fun WorldBookEditForm.hasUnsavedChangesFrom(initialForm: WorldBookEditForm): Boolean {
    return toComparableForm() != initialForm.toComparableForm()
}
