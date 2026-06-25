# ViewModel、Activity 与 Compose 规范

本文档说明 Feature 的 ViewModel、Activity 和 Compose 页面如何配合。

---

## 1. ViewModel

模板：

```kotlin
class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    initStatus = ChatUiState.None
), KoinComponent {
    private val mChatRepository by inject<ChatRepository>()
    private val mPromptBuilder by inject<ChatPromptBuilder>()

    @UiIntentObserver(ChatUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<ChatUiState.None>()) return
        ChatUiState.Normal(...).setup()
    }

    @UiIntentObserver(ChatUiIntent.Back::class)
    private fun onBack() {
        if (nowUiState is ChatUiState.Finished) return
        ChatUiState.Finished(nowUiState).setup()
    }
}
```

规则：

1. Intent 处理函数必须 `private`，命名为 `on` + Intent 名。
2. 函数开头先做状态守卫：`val uiState = getOrNull<Normal>() ?: return`。
3. 状态更新使用不可变 `copy()` + `setup()`。
4. 页面结束时使用 `Finished(nowUiState).setup()` 保留最后一个可渲染状态，避免 Activity 销毁前闪白；发布前先防止重复包裹 `Finished`。
5. IO 通过 Repository/Codec/Builder/Runtime 执行，或明确切到 `Dispatchers.IO`。
6. 导入导出、请求日志清理、摘要生成、批量保存等长任务使用 `enqueueAsyncTask` 或明确的协程任务，并提供进度/错误状态。
7. 流式生成不要使用会回滚 partial 内容的任务模式；停止生成后要保留用户消息和已收到的 assistant partial 内容。
8. 错误提示不要暴露 API Key、真实请求头、完整堆栈、私密对话和真实路径。
9. 不在 ViewModel 持有 Activity、View、Compose state。

对话框流程：

```text
ViewModel 设置 dialogState
Compose 根据 dialogState 渲染
用户选择通过 UiIntent 回传
ViewModel 执行业务并关闭 dialogState
```

Compose 不直接用本地状态控制业务对话框。

---

## 2. Activity

Activity 负责页面宿主能力，不做业务。

```kotlin
class ChatActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ChatViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is ChatUiState.Finished) finish()
        }

        ChatLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }
}
```

Activity 可以处理：

1. `registerForActivityResult`。
2. 系统文件选择器、导入导出 launcher。
3. 复制到剪贴板、系统分享、打开外部链接。
4. `finish()` 和 `setResult()`。
5. `ViewEvent` 分发。

Activity 不处理：

1. 会话列表过滤排序。
2. Prompt 构建、世界书触发、Regex 执行。
3. 角色卡/世界书 JSON 映射。
4. LLM 请求与流式解析。
5. 业务实体转换。
6. 直接读写 `AppModel`、Room 或 Repository。

---

## 3. Compose

Layout 入口：

```kotlin
@Composable
fun ChatLayout(
    uiState: ChatUiState,
    emit: ChatUiIntent.() -> Unit
) {
    BackHandler { ChatUiIntent.Back.emit() }
    when (uiState) {
        ChatUiState.None -> Unit
        is ChatUiState.Normal -> {
            ChatNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
            LoadSwitch(uiState.loadState, emit)
        }
        is ChatUiState.Finished -> ChatLayout(uiState.previous) { }
    }
}
```

规则：

1. 页面入口可以接收完整 UiState，子组件只接收自己需要的节点状态。
2. 子组件不直接持有 ViewModel。
3. 所有交互通过 `emit` 发送 UiIntent。
4. `Finished` 分支只渲染 `previous`，并传入空 `emit`，让 Activity finish 期间保持原画面但不再触发业务。
5. Compose 不做业务过滤、排序、文件读写、Room 查询、Prompt 构建。
6. 每个主要 Layout 写 Preview，并使用 `AppTheme(dynamicColor = false)`。
7. UI 文案不要解释框架、快捷键或开发细节。
8. 小屏下文本不能溢出或遮挡。
