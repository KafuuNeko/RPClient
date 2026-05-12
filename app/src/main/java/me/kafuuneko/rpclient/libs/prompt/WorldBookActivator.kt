package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

class WorldBookActivator {
    /**
     * 根据最近聊天与当前用户消息触发世界书条目。
     *
     * 初版只使用 session 启用条目作为候选，并按 primary/secondary keyword 判断是否命中。
     */
    fun activate(context: PromptBuildContext): List<LorebookEntry> {
        val scanBuffer = buildScanBuffer(context)
        return context.candidateLorebookEntries
            .filter { entry ->
                entry.constant || (scanBuffer.isNotBlank() && entry.matches(scanBuffer))
            }
            .sortedWith(compareBy<LorebookEntry> { it.order }.thenBy { it.id })
    }

    private fun buildScanBuffer(context: PromptBuildContext): String {
        // 世界书扫描不直接扫描最终 prompt，只扫描有限的最近聊天窗口，降低旧 key 长期误触发。
        val recentMessages = context.messages.takeLast(SCAN_MESSAGE_COUNT)
            .joinToString("\n") { it.content }
        return listOfNotNull(recentMessages, context.currentUserMessage)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun LorebookEntry.matches(scanBuffer: String): Boolean {
        // primary keyword 必须命中；secondary keyword 作为可选过滤条件减少泛 key 误触发。
        val primaryKeywords = getKeywordList().filter { it.isNotBlank() }
        if (primaryKeywords.isEmpty()) return false
        val primaryHit = primaryKeywords.any { scanBuffer.contains(it, ignoreCase = true) }
        if (!primaryHit) return false
        val secondaryKeywords = getSecondaryKeywordList().filter { it.isNotBlank() }
        return secondaryKeywords.isEmpty() ||
            secondaryKeywords.any { scanBuffer.contains(it, ignoreCase = true) }
    }

    private companion object {
        const val SCAN_MESSAGE_COUNT = 4
    }
}
