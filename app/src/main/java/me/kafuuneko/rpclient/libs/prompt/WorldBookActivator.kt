package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

class WorldBookActivator {
    fun activate(context: PromptBuildContext): List<LorebookEntry> {
        val scanBuffer = buildScanBuffer(context)
        if (scanBuffer.isBlank()) return emptyList()
        return context.candidateLorebookEntries
            .filter { it.matches(scanBuffer) }
            .sortedWith(compareBy<LorebookEntry> { it.order }.thenBy { it.id })
    }

    private fun buildScanBuffer(context: PromptBuildContext): String {
        val recentMessages = context.messages.takeLast(SCAN_MESSAGE_COUNT)
            .joinToString("\n") { it.content }
        return listOfNotNull(recentMessages, context.currentUserMessage)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun LorebookEntry.matches(scanBuffer: String): Boolean {
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

