# MVI 与 UiState 树规范

本文档说明 RPClient 的 MVI 数据流和树形 UiState 设计方法。新增或修改页面状态时优先阅读本文档。

---

## 1. 数据流

```text
Compose View --UiIntent--> ViewModel --UiState--> Compose View
                              |
                              | ViewEvent
                              v
                           Activity
```

规则：

1. Compose 只渲染 `UiState`，并发送 `UiIntent`。
2. ViewModel 处理 `UiIntent`，调用数据层，发布新 `UiState` 或 `ViewEvent`。
3. Activity 处理必须由系统/页面宿主完成的一次性动作。

---

## 2. View 树 = State 树

UiState 应尽量和页面 View 结构对应。设计时先画页面结构树，再为每个需要表达状态的节点建立 state。

聊天页示例：

```text
ChatRoot
├── TopBar
│   ├── Title
│   └── ActionArea
├── MessageList
│   ├── Empty
│   ├── Messages
│   └── GeneratingIndicator
├── SessionLorePanel
├── InputArea
└── DialogHost
```

对应 UiState：

```kotlin
sealed class ChatUiState {
    data object None : ChatUiState()

    data class Normal(
        val sessionId: String,
        val topBarState: ChatTopBarState,
        val messageListState: ChatMessageListState,
        val inputState: ChatInputState,
        val sessionLoreState: ChatSessionLoreState = ChatSessionLoreState.None,
        val loadState: ChatLoadState = ChatLoadState.None,
        val dialogState: ChatDialogState = ChatDialogState.None
    ) : ChatUiState()

    data class Finished(val previous: ChatUiState) : ChatUiState()
}
```

子节点继续使用 sealed class 表达互斥状态：

```kotlin
sealed class ChatMessageListState {
    data object Empty : ChatMessageListState()

    data class Messages(
        val items: List<ChatMessageItem>,
        val generationState: ChatGenerationState = ChatGenerationState.None
    ) : ChatMessageListState()
}

sealed class ChatGenerationState {
    data object None : ChatGenerationState()
    data class Streaming(val messageId: Long, val partialText: String) : ChatGenerationState()
}
```

---

## 3. 数据生命周期放置规则

| 数据生命周期 | 放置位置 | 示例 |
|--------------|----------|------|
| 只属于某个互斥状态 | 当前子状态 | `Messages.items`、`Streaming.partialText` |
| 被同一父节点下多个状态共享 | 父状态 | `sessionId`、`TopBar.title` |
| 横跨整个页面且不总是展示 | ViewModel 私有快照 | 原始消息列表、Prompt 检查结果、导入草稿 |
| 需要一次性执行 | ViewEvent | 打开导入器、复制文本、页面跳转 |
| 应长期持久化 | Room/Kotpref | 角色、会话、消息、世界书、Provider、全局偏好 |

状态切换会丢弃原子状态的数据。例如从普通消息列表切到编辑对话框后，编辑草稿如果需要跨状态保留，应放在 `dialogState` 或 ViewModel 私有快照中。

选择建议：

1. 数据只为 UI 渲染服务，且切换状态后仍常用：上移到父状态。
2. 数据较大、敏感或不总是展示：放 ViewModel 私有快照。
3. 数据需要应用重启后仍存在：放 Room 或 Kotpref。

---

## 4. 状态特化规则

ViewModel 每个操作只处理自己能理解的当前状态。

```kotlin
@UiIntentObserver(ChatUiIntent.ToggleLorebookEntry::class)
private fun onToggleLorebookEntry(intent: ChatUiIntent.ToggleLorebookEntry) {
    val uiState = getOrNull<ChatUiState.Normal>() ?: return
    val loreState = uiState.sessionLoreState as? ChatSessionLoreState.Expanded ?: return

    val updated = loreState.items.map { item ->
        if (item.entryId == intent.entryId) {
            item.copy(enabled = !item.enabled)
        } else {
            item
        }
    }

    uiState.copy(
        sessionLoreState = loreState.copy(items = updated)
    ).setup()
}
```

如果当前没有展开会话世界书面板，`ToggleLorebookEntry` 直接返回，不读取不存在的子状态。

---

## 5. Finished 状态

页面结束时使用 `Finished` 状态通知 Activity 执行 `finish()`，但 `Finished` 必须携带最后一个可渲染状态，避免 Compose 在 Activity 销毁前先渲染空内容造成闪白。

推荐结构：

```kotlin
sealed class ChatUiState {
    data object None : ChatUiState()
    data class Normal(...) : ChatUiState()
    data class Finished(val previous: ChatUiState) : ChatUiState()
}
```

ViewModel 处理返回时，应把当前状态作为 `previous`：

```kotlin
@UiIntentObserver(ChatUiIntent.Back::class)
private fun onBack() {
    if (nowUiState is ChatUiState.Finished) return
    ChatUiState.Finished(nowUiState).setup()
}
```

Compose 遇到 `Finished` 时继续渲染 `previous`，但不再向 ViewModel 发送新的业务 Intent：

```kotlin
when (uiState) {
    ChatUiState.None -> Unit
    is ChatUiState.Normal -> ChatNormal(uiState, emit)
    is ChatUiState.Finished -> ChatLayout(uiState.previous) { }
}
```

规则：

1. `previous` 只用于 Activity 结束过渡期的视觉保留，不作为恢复页面业务状态的入口。
2. `previous` 应是最后一个非 `Finished` 的可渲染状态，避免 `Finished` 嵌套导致递归渲染。
3. Activity 仍然通过监听 `Finished` 执行 `finish()`，不要把 `finish()` 放进 Compose。
4. 如果结束原因要求停止生成或保存草稿，先在 ViewModel 完成业务处理，再发布 `Finished`。

---

## 6. UiState 禁止项

UiState 不应保存：

1. `Context`、`Activity`、`ViewModel`。
2. 输入流、文件句柄、数据库连接、网络响应体。
3. API Key、认证 token、真实请求头。
4. 完整原始请求/响应 JSON，除非是 request log 页面明确展示的脱敏数据。
5. 真实私有文件路径，除非已确认该路径可暴露给 UI。
6. 可变集合引用。
