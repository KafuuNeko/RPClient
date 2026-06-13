package me.kafuuneko.rpclient.feature.worldbookedit.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 世界书元数据编辑表单。
 *
 * 条目正文由独立页面编辑，此处仅保存用于列表展示的条目快照。
 */
data class WorldBookEditForm(
    val id: Long = 0L,
    val name: String = "",
    val description: String = "",
    val scanDepth: Int = 2,
    val tokenBudget: Int = 25,
    val recursiveScanning: Boolean = false,
    val extensionsJson: String = "{}",
    val entries: List<WorldBookEntryListItem> = emptyList()
) {
    /** id 为 0 表示尚未持久化的新世界书。 */
    val isNew: Boolean
        get() = id == 0L

    companion object {
        /** 从世界书实体及其条目构造可编辑表单。 */
        fun from(lorebook: Lorebook, entries: List<LorebookEntry>): WorldBookEditForm {
            return WorldBookEditForm(
                id = lorebook.id,
                name = lorebook.name,
                description = lorebook.description,
                scanDepth = lorebook.scanDepth,
                tokenBudget = lorebook.tokenBudget,
                recursiveScanning = lorebook.recursiveScanning,
                extensionsJson = lorebook.extensionsJson,
                entries = entries.map { WorldBookEntryListItem.from(it) }
            )
        }
    }

    /** 将表单转换为可保存实体，并规范化名称首尾空白。 */
    fun toLorebook(): Lorebook {
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

/** 世界书编辑页中的条目摘要。 */
data class WorldBookEntryListItem(
    val id: Long,
    val name: String,
    val keywords: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int
) {
    companion object {
        fun from(entry: LorebookEntry): WorldBookEntryListItem {
            return WorldBookEntryListItem(
                id = entry.id,
                name = entry.name,
                keywords = entry.getKeywordList(),
                constant = entry.constant,
                order = entry.order,
                depth = entry.depth
            )
        }
    }
}

/** 生成用于脏数据比较的规范化表单。 */
fun WorldBookEditForm.toComparableForm(): WorldBookEditForm {
    return copy(
        name = name.trim()
    )
}

/** 判断表单是否包含需要用户确认放弃的修改。 */
fun WorldBookEditForm.hasUnsavedChangesFrom(initialForm: WorldBookEditForm): Boolean {
    return toComparableForm() != initialForm.toComparableForm()
}
