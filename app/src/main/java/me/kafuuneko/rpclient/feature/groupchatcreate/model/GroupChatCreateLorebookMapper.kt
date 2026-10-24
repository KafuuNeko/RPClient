package me.kafuuneko.rpclient.feature.groupchatcreate.model

import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.model.LorebookWithEntries

/** 按条目的 order 构建创建页世界书分组，并排除空分组。 */
internal fun List<LorebookWithEntries>.toGroupChatCreateLorebookGroups(): List<GroupChatLorebookGroupItem> {
    // 保持各创建页原有字段投影，避免搜索能力或默认勾选状态变化。
    return map { lorebookWithEntries ->
        val lorebook = lorebookWithEntries.lorebook
        GroupChatLorebookGroupItem(
            lorebookId = lorebook.id,
            lorebookName = lorebook.name,
            entries = lorebookWithEntries.entries
                .sortedBy { it.order }
                .map { entry ->
                    GroupChatLorebookEntryItem(
                        id = entry.id,
                        lorebookId = lorebook.id,
                        lorebookName = lorebook.name,
                        name = entry.name,
                        content = entry.content,
                        keywords = entry.getKeywordList(),
                        secondaryKeywords = entry.getSecondaryKeywordList(),
                        constant = entry.constant,
                        order = entry.order,
                        depth = entry.depth,
                        enabled = false
                    )
                }
        )
    }.filter { it.entries.isNotEmpty() }
}
