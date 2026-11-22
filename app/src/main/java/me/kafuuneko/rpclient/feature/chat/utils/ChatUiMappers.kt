package me.kafuuneko.rpclient.feature.chat.utils

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryData
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.model.withCurrentAsSavedSnapshot
import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState
import me.kafuuneko.rpclient.libs.prompt.macro.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.model.toMessageContentParts
import me.kafuuneko.rpclient.model.SummaryEditorState
import me.kafuuneko.rpclient.ui.theme.DefaultCharacterAccentColor
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 将会话实体和摘要状态等聚合信息转换为聊天页展示模型。 */
fun ChatSession.toChatSessionItem(
    selection: PersonaSelection,
    inheritedDescription: String,
    summary: String,
    canResummarize: Boolean,
    messageCount: Int,
    enabledIds: Set<Long>,
): ChatSessionItem {
    // 设置页保留原始覆盖草稿，有效身份用于展示且不能写回覆盖列。
    return ChatSessionItem(
        id = id,
        title = title,
        summarize = summary,
        canResummarize = canResummarize,
        userNote = userNote,
        userName = userName.orEmpty(),
        persona = selection,
        inheritedDescription = inheritedDescription,
        userDescription = userDescription.orEmpty(),
        creatorNotes = creatorNotes.orEmpty(),
        autoSummaryPaused = autoSummaryPaused,
        messageCount = messageCount,
        enabledLorebookEntryIds = enabledIds,
        includeCharacterExamples = includeCharacterExamples,
    )
}

/**
 * 从持久化会话覆盖值建立设置草稿，角色默认备注只用于占位提示。
 * @param inheritedCreatorNotes 角色卡当前提供的默认备注。
 * @param summary 与持久化摘要同步的编辑基线及覆盖位置。
 * @return 记录加载基线的设置表单。
 */
internal fun ChatSessionItem.toChatSettingsState(
    inheritedCreatorNotes: String,
    summary: SummaryEditorState,
): ChatSettingsState {
    // 原始覆盖与默认正文分开保留，清空覆盖后继续跟随角色卡变化。
    return ChatSettingsState(
        titleDraft = title,
        summary = summary,
        userNoteDraft = userNote,
        persona = persona,
        userNameDraft = userName,
        userDescriptionDraft = userDescription,
        inheritedDescription = inheritedDescription,
        creatorNotesDraft = creatorNotes,
        inheritedCreatorNotes = inheritedCreatorNotes,
        autoSummaryPaused = autoSummaryPaused,
        includeCharacterExamples = includeCharacterExamples,
    ).withCurrentAsSavedSnapshot()
}

/** 将角色实体转换为聊天页展示模型。 */
fun Character.toChatCharacterItem(
    userName: String,
    avatarImage: ImageBitmap? = null,
): ChatCharacterItem {
    return ChatCharacterItem(
        id = id,
        name = name,
        description = resolveCharacterUserMacros(
            template = description,
            characterName = name,
            userName = userName,
        ),
        personality = personality,
        scenario = scenario,
        examplesOfDialogue = examplesOfDialogue,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        avatarText = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        accentColor = DefaultCharacterAccentColor,
        avatarImage = avatarImage,
    )
}

/** 将持久化消息转换为支持流式标记和 think 分段的 UI 模型。 */
fun List<ChatMessage>.toChatMessageItems(
    characterName: String,
    userName: String,
    systemSpeaker: String,
    streamingMessageId: Long?,
): List<ChatMessageUiModel> {
    return map { message ->
        val role = when (message.source) {
            ChatMessage.Source.User -> MessageRole.User
            ChatMessage.Source.Char -> MessageRole.Assistant
            ChatMessage.Source.System -> MessageRole.Narrator
            ChatMessage.Source.Summary -> error("Summary snapshots are not rendered as chat messages")
        }
        ChatMessageUiModel(
            id = message.id.toString(),
            role = role,
            speaker = when (message.source) {
                ChatMessage.Source.User -> userName
                ChatMessage.Source.Char -> characterName
                ChatMessage.Source.System -> systemSpeaker
                ChatMessage.Source.Summary -> error("Summary snapshots are not rendered as chat messages")
            },
            content = message.content,
            parts = message.content.toMessageContentParts(message.id.toString()),
            time = message.createTime.formatTimestamp("HH:mm"),
            tokenCount = (message.content.length / 3).coerceAtLeast(1),
            isStreaming = message.id == streamingMessageId,
        )
    }
}

/** 按世界书分组构造聊天设置页的条目选择模型。 */
fun ChatLorebookEntryData.toChatLorebookGroupItems(
    enabledIds: Set<Long>,
    unknownLorebookName: String,
): List<ChatLorebookGroupItem> {
    return entries.groupBy { it.lorebookId }
        .toList()
        .sortedWith(comparator = compareBy<Pair<Long, List<LorebookEntry>>> { lorebooks[it.first]?.name.orEmpty() }.thenBy { it.first })
        .map { (lorebookId, entries) ->
            val lorebookName = lorebooks[lorebookId]?.name.orEmpty().ifBlank {
                unknownLorebookName
            }
            val entryItems = entries.sortedBy { entry -> entry.order }.map { entry ->
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
                    enabled = entry.id in enabledIds,
                )
            }
            ChatLorebookGroupItem(
                lorebookId = lorebookId,
                lorebookName = lorebookName,
                enabledCount = entryItems.count { chatLorebookEntryItem -> chatLorebookEntryItem.enabled },
                totalCount = entryItems.size,
                entries = entryItems,
            )
        }
}

/** 用尚未落库的流式文本替换对应消息的展示内容。 */
fun List<ChatMessageUiModel>.replaceStreamingMessage(
    messageId: Long?,
    content: String,
): List<ChatMessageUiModel> {
    if (messageId == null) return this
    return map { chatMessageUiModel ->
        if (chatMessageUiModel.id == messageId.toString()) {
            chatMessageUiModel.copy(
                content = content,
                parts = content.toMessageContentParts(chatMessageUiModel.id),
                isStreaming = true,
            )
        } else {
            chatMessageUiModel
        }
    }
}
