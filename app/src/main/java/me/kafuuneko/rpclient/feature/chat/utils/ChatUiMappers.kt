package me.kafuuneko.rpclient.feature.chat.utils

import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageContentPart
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.utils.formatTimestamp

data class ChatLorebookEntryData(
    val lorebooks: Map<Long, Lorebook>,
    val entries: List<LorebookEntry>
)

fun ChatSession.toChatSessionItem(
    creatorNotes: String,
    messageCount: Int,
    enabledIds: Set<Long>
): ChatSessionItem {
    return ChatSessionItem(
        id = id,
        title = title,
        summarize = summarize,
        userNote = userNote,
        creatorNotes = creatorNotes,
        messageCount = messageCount,
        enabledLorebookEntryIds = enabledIds
    )
}

fun Character.toChatCharacterItem(): ChatCharacterItem {
    return ChatCharacterItem(
        id = id,
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        examplesOfDialogue = examplesOfDialogue,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        avatarText = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        accentColor = 0xFF315EFD
    )
}

fun List<ChatMessage>.toChatMessageItems(
    characterName: String,
    userName: String,
    systemSpeaker: String,
    streamingMessageId: Long?
): List<ChatMessageUiModel> {
    return map { message ->
        val role = when (message.source) {
            ChatMessage.Source.User -> MessageRole.User
            ChatMessage.Source.Char -> MessageRole.Assistant
            ChatMessage.Source.System -> MessageRole.Narrator
        }
        ChatMessageUiModel(
            id = message.id.toString(),
            role = role,
            speaker = when (message.source) {
                ChatMessage.Source.User -> userName
                ChatMessage.Source.Char -> characterName
                ChatMessage.Source.System -> systemSpeaker
            },
            content = message.content,
            parts = message.content.toContentParts(message.id.toString()),
            time = message.createTime.formatTimestamp("HH:mm"),
            tokenCount = (message.content.length / 3).coerceAtLeast(1),
            isStreaming = message.id == streamingMessageId
        )
    }
}

fun ChatLorebookEntryData.toChatLorebookGroupItems(
    enabledIds: Set<Long>,
    unknownLorebookName: String
): List<ChatLorebookGroupItem> {
    return entries.groupBy { it.lorebookId }
        .toList()
        .sortedWith(compareBy<Pair<Long, List<LorebookEntry>>> { lorebooks[it.first]?.name.orEmpty() }.thenBy { it.first })
        .map { (lorebookId, entries) ->
            val lorebookName = lorebooks[lorebookId]?.name.orEmpty().ifBlank {
                unknownLorebookName
            }
            val entryItems = entries.sortedBy { it.order }.map { entry ->
                ChatLorebookEntryItem(
                    id = entry.id,
                    lorebookId = entry.lorebookId,
                    lorebookName = lorebookName,
                    name = entry.name,
                    keywords = entry.getKeywordList(),
                    secondaryKeywords = entry.getSecondaryKeywordList(),
                    constant = entry.constant,
                    order = entry.order,
                    depth = entry.depth,
                    content = entry.content,
                    enabled = entry.id in enabledIds
                )
            }
            ChatLorebookGroupItem(
                lorebookId = lorebookId,
                lorebookName = lorebookName,
                enabledCount = entryItems.count { it.enabled },
                totalCount = entryItems.size,
                entries = entryItems
            )
        }
}

fun List<ChatMessageUiModel>.replaceStreamingMessage(
    messageId: Long?,
    content: String
): List<ChatMessageUiModel> {
    if (messageId == null) return this
    return map {
        if (it.id == messageId.toString()) {
            it.copy(
                content = content,
                parts = content.toContentParts(it.id),
                isStreaming = true
            )
        } else {
            it
        }
    }
}

fun String.toContentParts(messageId: String): List<ChatMessageContentPart> {
    val regex = Regex("<think>([\\s\\S]*?)(</think>|$)", RegexOption.IGNORE_CASE)
    val parts = mutableListOf<ChatMessageContentPart>()
    var cursor = 0
    regex.findAll(this).forEachIndexed { index, match ->
        if (match.range.first > cursor) {
            parts += ChatMessageContentPart.Text(substring(cursor, match.range.first))
        }
        val thinkContent = match.groupValues[1].trim()
        if (thinkContent.isNotBlank() && !thinkContent.equals("null", ignoreCase = true)) {
            parts += ChatMessageContentPart.Think(
                id = "$messageId:$index",
                content = thinkContent
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        parts += ChatMessageContentPart.Text(substring(cursor))
    }
    return parts.ifEmpty { listOf(ChatMessageContentPart.Text(this)) }
}
