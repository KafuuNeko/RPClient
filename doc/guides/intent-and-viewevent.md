# UiIntent 与 ViewEvent 规范

本文档说明用户意图和一次性事件的设计边界。

---

## 1. UiIntent

`UiIntent` 描述用户行为，不描述实现细节。

```kotlin
sealed class ChatUiIntent {
    data object Init : ChatUiIntent()
    data object Resume : ChatUiIntent()
    data object Back : ChatUiIntent()
    data class ChangeInput(val value: String) : ChatUiIntent()
    data object SendMessage : ChatUiIntent()
    data object StopGeneration : ChatUiIntent()
    data class RegenerateMessage(val messageId: Long) : ChatUiIntent()
    data class ToggleLorebookEntry(val entryId: Long) : ChatUiIntent()
    data object ShowDeleteDialog : ChatUiIntent()
    data object ConfirmDelete : ChatUiIntent()
    data object DismissDialog : ChatUiIntent()
}
```

命名规则：

1. 生命周期：`Init`、`Resume`、`Back`。
2. 用户动作：`SendMessage`、`StopGeneration`、`ToggleLorebookEntry`、`ChangeName`。
3. 对话框：`ShowXxxDialog`、`ConfirmXxx`、`DismissDialog`。
4. 导入导出：`ImportClick`、`ExportClick`、`ImportResult`，系统选择器本身用 ViewEvent 打开。
5. 无参数用 `data object`，有参数用 `data class`。
6. 不使用 `LoadFromRoom`、`UpdateLoading`、`CallRepository` 这类实现细节命名。

---

## 2. ViewEvent

`ViewEvent` 只用于 Activity 必须处理的一次性动作：

1. 页面跳转。
2. 系统文件选择器、图片选择器、导入导出 launcher。
3. 复制到剪贴板、系统分享、打开外部链接。
4. Toast 等一次性提示。
5. `setResult()`、`finish()` 之外需要宿主执行的动作。

优先使用 `AppViewEvent`；无法覆盖时再建 `<Feature>ViewEvent`。

```kotlin
sealed class CharacterListViewEvent : IViewEvent {
    data object OpenCharacterCardImporter : CharacterListViewEvent()
    data class OpenCharacterCardJsonExporter(
        val fileName: String,
        val json: String
    ) : CharacterListViewEvent()
}
```

禁止把一次性事件塞进 `UiState`，例如“跳转到某页”“打开系统选择器”“弹一次 Toast”“复制一次文本”。
