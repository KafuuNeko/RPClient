package me.kafuuneko.rpclient.feature.chat

import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.LoreBookUiModel
import me.kafuuneko.rpclient.libs.model.LoreEntryUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel

class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    ChatUiState.None
) {
    @UiIntentObserver(ChatUiIntent.Init::class)
    private fun onInit(intent: ChatUiIntent.Init) {
        if (!isStateOf<ChatUiState.None>()) return
        ChatUiState.Normal(
            session = previewSession(intent.sessionId),
            character = previewCharacter(),
            messages = previewMessages(),
            sessionLoreBooks = previewLoreBooks(),
            sessionLoreEntries = previewLoreEntries(),
            inputDraft = "把上一幕的雨声延续下来，但让气氛更安静一点。",
            generationStatus = "已连接，等待发送"
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.Resume::class)
    private fun onResume() = Unit

    @UiIntentObserver(ChatUiIntent.Back::class)
    private fun onBack() {
        ChatUiState.Finished.setup()
    }

    @UiIntentObserver(ChatUiIntent.SendMessage::class)
    private fun onSendMessage() {
        AppViewEvent.PopupToastMessage("发送与流式生成逻辑稍后接入").tryEmit()
    }

    @UiIntentObserver(ChatUiIntent.StopGeneration::class)
    private fun onStopGeneration() {
        AppViewEvent.PopupToastMessage("停止生成逻辑稍后接入").tryEmit()
    }

    @UiIntentObserver(ChatUiIntent.RegenerateLast::class)
    private fun onRegenerateLast() {
        AppViewEvent.PopupToastMessage("重新生成会创建同级分支，稍后接入").tryEmit()
    }

    @UiIntentObserver(ChatUiIntent.OpenSessionLore::class)
    private fun onOpenSessionLore() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(isSessionLoreExpanded = !uiState.isSessionLoreExpanded).setup()
    }

    @UiIntentObserver(ChatUiIntent.ToggleSessionLoreBook::class)
    private fun onToggleSessionLoreBook(intent: ChatUiIntent.ToggleSessionLoreBook) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            sessionLoreBooks = uiState.sessionLoreBooks.map {
                if (it.id == intent.loreBookId) it.copy(enabled = !it.enabled) else it
            }
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ToggleSessionLoreEntry::class)
    private fun onToggleSessionLoreEntry(intent: ChatUiIntent.ToggleSessionLoreEntry) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            sessionLoreEntries = uiState.sessionLoreEntries.map {
                if (it.id == intent.entryId) it.copy(isEnabled = !it.isEnabled) else it
            }
        ).setup()
    }

    private fun previewSession(sessionId: String?) = ChatSessionUiModel(
        id = sessionId ?: "session-rain",
        characterName = "Lyra",
        title = "雨夜里的第七份卷宗",
        preview = "你把湿透的外套搭在椅背上，她已经把案卷翻到了失踪名单那一页。",
        messageCount = 186,
        branchCount = 3,
        updatedAt = "刚刚"
    )

    private fun previewCharacter() = RpCharacterUiModel(
        id = "lyra",
        name = "Lyra",
        subtitle = "雾港档案管理员",
        description = "记忆力惊人的城市档案员。",
        avatarText = "L",
        tags = listOf("悬疑", "慢热", "现代奇幻"),
        sessions = 12,
        updatedAt = "刚刚",
        accentColor = 0xFF315EFD
    )

    private fun previewMessages() = listOf(
        ChatMessageUiModel(
            id = "m1",
            role = MessageRole.Narrator,
            speaker = "旁白",
            content = "雨从霓虹招牌的边缘滑下来，档案馆的门禁灯在凌晨两点仍然亮着。",
            time = "02:13",
            tokenCount = 34
        ),
        ChatMessageUiModel(
            id = "m2",
            role = MessageRole.User,
            speaker = "你",
            content = "我把那枚没有编号的钥匙放到桌上，问她是否见过相同的纹章。",
            time = "02:14",
            tokenCount = 42
        ),
        ChatMessageUiModel(
            id = "m3",
            role = MessageRole.Assistant,
            speaker = "Lyra",
            content = "她没有立刻回答，只是把灯调暗了一些。纹章在冷光下浮出细密的划痕，像一张被故意擦掉名字的地图。",
            time = "02:15",
            tokenCount = 76,
            isStreaming = true
        )
    )

    private fun previewLoreBooks() = listOf(
        LoreBookUiModel("fog-port", "雾港旧城区", "Chat Lore", 18, true, "今天"),
        LoreBookUiModel("case-seven", "第七份卷宗", "Character Lore", 9, true, "昨天"),
        LoreBookUiModel("night-watch", "海关夜巡队", "可选", 12, false, "周二")
    )

    private fun previewLoreEntries() = listOf(
        LoreEntryUiModel(
            id = "fog-port-old-town",
            title = "旧城区排水系统",
            keywords = listOf("旧城区", "档案馆"),
            content = "旧城区地下仍保留战前排水系统。",
            priority = 80,
            isEnabled = true
        ),
        LoreEntryUiModel(
            id = "case-seven-rule",
            title = "第七份卷宗规律",
            keywords = listOf("第七", "失踪名单"),
            content = "出现第七名失踪者时，档案馆会收到无署名材料。",
            priority = 95,
            isEnabled = false
        )
    )
}
