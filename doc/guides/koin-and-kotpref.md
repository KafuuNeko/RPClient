# Koin 与 Kotpref 规范

本文档说明依赖注入和简单偏好存储的使用边界。

---

## 1. Koin

Koin 只注册跨页面共享的对象，如 Repository、Codec、Builder、Runtime、网络客户端、工具类。

```kotlin
private val appModules = module {
    singleOf(::AppLibs)
    singleOf(::ChatRepository)
    singleOf(::LorebookRepository)
    singleOf(::ChatPromptBuilder)
    singleOf(::WorldBookActivator)
    singleOf(::RegexScriptRuntime)
}
```

规则：

1. Repository、Codec、Builder、Runtime 注册到 `RPClientApp.kt`。
2. ViewModel 当前使用 Activity 的 `viewModels()` 创建，不注册到 Koin。
3. Compose 中不调用 Koin。
4. 不在业务代码中手动 new 全局单例依赖。
5. 新增依赖时保持构造函数依赖清晰。
6. 注册对象不得持有 Activity、View 或 Compose state。

ViewModel 注入：

```kotlin
class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    initStatus = ChatUiState.None
), KoinComponent {
    private val mChatRepository by inject<ChatRepository>()
    private val mChatPromptBuilder by inject<ChatPromptBuilder>()
}
```

---

## 2. Kotpref

`AppModel` 只保存小体量全局偏好和默认配置：

```kotlin
object AppModel : KotprefModel() {
    const val THEME_SYSTEM = "system"
    var themeMode by stringPref(default = THEME_SYSTEM)
    var summarizePrompt by stringPref(default = DEFAULT_SUMMARIZE_PROMPT)
}
```

适合：

1. 主题模式。
2. 用户头像或昵称这类轻量设置。
3. 默认 Prompt 模板。
4. 调试开关、全局显示偏好。
5. 列表排序偏好。

不适合：

1. 角色、世界书、会话、消息、群聊成员等业务实体。
2. 请求日志、完整原始响应、大段可恢复 JSON。
3. 角色卡 PNG、头像二进制和文件内容。
4. API Key 以外需要关系查询或迁移的数据主体。
5. 需要事务一致性的配置。

`AppModel` 的读写应由 ViewModel 或 Repository/Manager 进行，Compose 不直接读写。
