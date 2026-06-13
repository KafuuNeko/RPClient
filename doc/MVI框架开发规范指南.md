# MVI 框架通用开发规范指南

---

## 一、架构概述

本框架采用 **MVI（Model-View-Intent）** 单向数据流架构，结合 Jetpack Compose 构建 UI。

### 核心数据流

```
┌──────────┐  UiIntent   ┌─────────────┐  UiState    ┌──────────┐
│   View   │ ──────────→ │  ViewModel  │ ──────────→ │   View   │
│ (Compose)│             │(CoreViewModel)│            │ (Compose)│
└──────────┘             └──────┬───────┘             └──────────┘
                                │ ViewEvent (一次性事件)
                                ↓
                         ┌──────────────┐
                         │   Activity   │
                         │(CoreActivity)│
                         └──────────────┘
```

| 概念 | 方向 | 载体 | 说明 |
|------|------|------|------|
| **UiIntent** | View → ViewModel | `MutableSharedFlow` | 用户意图，描述"发生了什么" |
| **UiState** | ViewModel → View | `MutableStateFlow` | 页面状态，驱动 Compose 重绘 |
| **ViewEvent** | ViewModel → Activity | `MutableSharedFlow<ViewEventWrapper>` | 一次性事件（Toast、跳转等） |

---

## 二、项目目录结构规范

### Feature 模块标准结构

每个独立页面/功能作为一个 feature 模块，采用以下结构：

```
feature/<feature_name>/
├── <Feature>Activity.kt          # Activity（继承 CoreActivity 或 CoreActivityWithEvent）
├── <Feature>ViewModel.kt         # ViewModel（继承 CoreViewModel 或 CoreViewModelWithEvent）
├── model/                        # （可选）当前 feature 专用的业务数据模型
│   └── SomeEnum.kt
├── presentation/                 # MVI 三件套
│   ├── <Feature>UiState.kt       # 状态定义
│   ├── <Feature>UiIntent.kt      # 意图定义
│   └── <Feature>ViewEvent.kt     # （可选）一次性事件定义
└── ui/                           # Compose UI 组件
    ├── <Feature>Layout.kt        # 顶层布局入口
    ├── SomeSubView.kt            # 子视图
    ├── common/                   # （可选）feature 内通用组件
    └── scaffold/                 # （可选）Scaffold 相关组件（TopBar、Drawer等）
```

### 简单页面（无 ViewModel）

对于纯展示类页面（如"关于"页），可省略 ViewModel、presentation、model 目录：

```
feature/<feature_name>/
├── <Feature>Activity.kt          # 继承 CoreActivity
└── ui/
    └── <Feature>Layout.kt
```

### 层级职责划分

| 目录 | 作用域 | 存放内容 |
|------|--------|----------|
| `feature/xxx/model/` | 仅当前 feature | 枚举、业务数据类等 |
| `feature/xxx/presentation/` | 仅当前 feature | UiState、UiIntent、ViewEvent |
| `feature/xxx/ui/` | 仅当前 feature | Compose 组件 |
| `libs/core/` | 全局 | MVI 框架基类（不应修改） |
| `libs/model/` | 全局 | 跨 feature 共享的数据模型 |
| `libs/extensions/` | 全局 | Kotlin 扩展函数（按类型分文件） |
| `libs/manager/` | 全局 | 业务管理器类 |
| `libs/utils/` | 全局 | 通用工具类 |
| `ui/theme/` | 全局 | Material 主题定义 |
| `ui/dialogs/` | 全局 | 跨 feature 共享的通用对话框 |
| `ui/widges/` | 全局 | 跨 feature 共享的通用 Widget |
| `ui/utils/` | 全局 | UI 层工具类 |

---

## 三、UiState 设计规范

### 3.1 树形结构设计思想

将 UI 的 View 嵌套关系看作一棵树，**UiState 也应设计为与之对应的树形结构**，每个节点对应一个子区域的状态。

### 3.2 顶层 UiState 模板

```kotlin
sealed class <Feature>UiState {
    data object None : <Feature>UiState()           // 初始空状态（必须）
    data class Normal(                               // 正常工作状态（必须）
        val loadState: <Feature>LoadState = <Feature>LoadState.None,
        val dialogState: <Feature>DialogState = <Feature>DialogState.None,
        // ... 其他子状态节点
    ) : <Feature>UiState()
    data object Finished : <Feature>UiState()        // 页面结束状态（按需）
}
```

> [!IMPORTANT]
> 顶层 UiState 的各子类代表**页面级别的互斥状态**（如未初始化、正常、已结束）。
> 同一页面内的并存状态（如加载中 + 弹窗）应作为 `Normal` 的字段存在。

### 3.3 子状态定义

```kotlin
// 加载状态
sealed class <Feature>LoadState {
    data object None : <Feature>LoadState()
    data object Loading : <Feature>LoadState()
    data class Progress(val current: Int, val total: Int) : <Feature>LoadState()
}

// 对话框状态
sealed class <Feature>DialogState {
    data object None : <Feature>DialogState()
    data class SomeConfirm(
        val message: String
    ) : <Feature>DialogState()
}
```

### 3.4 数据存储规则

| 场景 | 存放位置 |
|------|---------|
| 数据仅在某个子状态内使用 | 定义在该子状态的 data class 字段中 |
| 数据生命周期大于某个子状态 | 定义在其**父状态**中 |
| 数据生命周期大于所有状态 | 定义为 **ViewModel 的私有成员变量**，需要时通过 UiState 传递给 View |
| 每个 UiState | 仅存储与之相关的数据，**不存放无关数据** |

### 3.5 性能注意事项

1. **控制树深度**：避免 UiState 树深度过深
2. **状态级联**：子节点状态变化会触发整棵树更新，因此 Compose 函数应尽可能拆分，仅传递相关子数据
3. **避免冗余数据**：UiState 中不放与当前状态无关的数据

---

## 四、UiIntent 设计规范

```kotlin
sealed class <Feature>UiIntent {
    // 生命周期意图
    data object Init : <Feature>UiIntent()       // 页面初始化（必须）
    data object Resume : <Feature>UiIntent()     // 页面恢复（按需）
    data object Back : <Feature>UiIntent()       // 返回操作（按需）

    // 用户交互意图 —— 无参用 data object，有参用 data class
    data object SomeButtonClick : <Feature>UiIntent()
    data class ItemSelected(val id: String) : <Feature>UiIntent()
    data class ToggleSetting(val enabled: Boolean) : <Feature>UiIntent()
}
```

### 命名原则

- ✅ 以**用户行为**命名：`Init`、`Back`、`ItemSelected`、`DeleteClick`、`ToggleXxx`
- ❌ 不以实现细节命名：`LoadDataFromDatabase`、`UpdateStateToLoading`
- ✅ 无参意图用 `data object`，有参意图用 `data class`

---

## 五、ViewEvent 设计规范

```kotlin
sealed class <Feature>ViewEvent : IViewEvent {
    data class StartSomeActivity(val params: Bundle) : <Feature>ViewEvent()
    data object RequestSomePermission : <Feature>ViewEvent()
}
```

### 使用场景

ViewEvent 仅用于**必须由 Activity 处理的一次性操作**：
- 需要 Activity 上下文的页面跳转
- 系统权限请求
- 与系统 API 交互（安装 APK 等）

### 内置通用 ViewEvent

框架内置了 `AppViewEvent`，已处理常见场景，**无需在 Feature 中重复定义**：

```kotlin
sealed class AppViewEvent : IViewEvent {
    data class PopupToastMessage(val message: String) : AppViewEvent()
    data class PopupToastMessageByResId(@StringRes val message: Int) : AppViewEvent()
    data class StartActivity(val activity: Class<*>, val extras: Bundle? = null) : AppViewEvent()
    data class StartActivityByIntent(val intent: Intent) : AppViewEvent()
    data class SetResult(val resultCode: Int, val intent: Intent? = null) : AppViewEvent()
}
```

> [!TIP]
> 仅当 `AppViewEvent` 无法覆盖的场景才创建 Feature 专属 ViewEvent。

---

## 六、ViewModel 编写规范

### 6.1 类定义模板

```kotlin
// 有 ViewEvent 的标准 ViewModel
class <Feature>ViewModel : CoreViewModelWithEvent<<Feature>UiIntent, <Feature>UiState>(
    initStatus = <Feature>UiState.None
), KoinComponent {
    // Koin 注入
    private val mSomeManager by inject<SomeManager>()

    // 私有状态（生命周期大于所有 UiState 的数据）
    private var mSomeCache: SomeType = ...
}
```

### 6.2 Intent 处理方法

使用 `@UiIntentObserver` 注解将方法绑定到特定 Intent：

```kotlin
// 无参处理
@UiIntentObserver(<Feature>UiIntent.Init::class)
private fun onInit() {
    if (!isStateOf<<Feature>UiState.None>()) return  // 状态守卫
    <Feature>UiState.Normal(...).setup()
}

// 有参处理
@UiIntentObserver(<Feature>UiIntent.ItemSelected::class)
private suspend fun onItemSelected(intent: <Feature>UiIntent.ItemSelected) {
    val uiState = getOrNull<<Feature>UiState.Normal>() ?: return  // 状态守卫
    uiState.copy(...).setup()
}
```

**方法签名规则**：
- 访问修饰符：`private`
- 参数：无参（仅 `this`）或一个 Intent 参数
- 可以是 `suspend` 或普通函数
- 命名：`on` + Intent 名称，如 `onInit`、`onItemSelected`

### 6.3 核心 API

```kotlin
// === 状态管理 ===
fun S.setup()                                    // 将状态推送到 UI
inline fun <reified T : S> getOrNull(): T?       // 安全获取当前状态（类型不匹配返回 null）
inline fun <reified T : S> isStateOf(): Boolean   // 判断当前状态类型
suspend fun <reified T> awaitStateOf(): T         // 挂起直到状态变为指定类型
suspend fun <reified T> awaitStateOf(predicate): T // 带条件的挂起等待

// === ViewEvent（仅 CoreViewModelWithEvent）===
suspend fun IViewEvent.emit()                    // 发送 ViewEvent（缓冲区满则挂起）
fun IViewEvent.tryEmit(): Boolean                // 非阻塞发送
suspend fun IViewEvent.emitAndAwait()             // 发送并等待消费完成

// === 异步任务 ===
suspend fun enqueueAsyncTask(block)              // 加入异步队列（串行执行）
suspend fun cancelActiveTaskAndRestore()          // 取消当前异步任务并恢复状态快照

// === Intent 发送（供 Activity 调用）===
fun emit(uiIntent: I)                            // 发送一个 Intent
```

### 6.4 状态操作惯用模式

```kotlin
// ✅ 通过 copy + setup 更新状态（保持不可变性）
val uiState = getOrNull<<Feature>UiState.Normal>() ?: return
uiState.copy(loadState = LoadState.Loading).setup()

// ✅ 将复杂状态操作封装为 UiState 的扩展函数
private suspend fun <Feature>UiState.Normal.refresh(): <Feature>UiState.Normal {
    copy(loadState = LoadState.Loading).setup()
    val data = withContext(Dispatchers.IO) { loadData() }
    return copy(loadState = LoadState.None, data = data)
}

// ✅ 链式调用
uiState.copy(sortType = newSort).refresh().setup()
```

### 6.5 对话框交互模式

> [!IMPORTANT]
> 对话框的**展示和关闭**由 ViewModel 通过 UiState 中的 `dialogState` 控制，对话框的**用户操作结果**通过 UiIntent 回传给 ViewModel。**严禁**在 Compose 中直接控制对话框逻辑或绕过 ViewModel 流程。

完整流程：

```
ViewModel 设置 dialogState → Compose 根据 dialogState 渲染对话框
     ↑                                      ↓
     └──── 用户操作通过 UiIntent 回传 ←────────┘
```

**Step 1: 定义 DialogState（不含 DeferredResult）**

```kotlin
sealed class <Feature>DialogState {
    data object None : <Feature>DialogState()
    data class DeleteConfirm(
        val fileName: String
    ) : <Feature>DialogState()
}
```

**Step 2: 定义对话框相关的 UiIntent**

```kotlin
sealed class <Feature>UiIntent {
    // ... 其他意图

    // 对话框操作意图
    data object ShowDeleteDialog : <Feature>UiIntent()   // 请求展示对话框
    data object ConfirmDelete : <Feature>UiIntent()       // 用户确认
    data object DismissDialog : <Feature>UiIntent()       // 用户取消/关闭
}
```

**Step 3: ViewModel 中处理对话框流程**

```kotlin
// 展示对话框 —— 通过修改 dialogState
@UiIntentObserver(<Feature>UiIntent.ShowDeleteDialog::class)
private fun onShowDeleteDialog() {
    val uiState = getOrNull<<Feature>UiState.Normal>() ?: return
    uiState.copy(
        dialogState = <Feature>DialogState.DeleteConfirm(fileName = "example.txt")
    ).setup()
}

// 用户确认 —— 执行业务逻辑后关闭对话框
@UiIntentObserver(<Feature>UiIntent.ConfirmDelete::class)
private suspend fun onConfirmDelete() {
    val uiState = getOrNull<<Feature>UiState.Normal>() ?: return
    val dialog = uiState.dialogState as? <Feature>DialogState.DeleteConfirm ?: return
    // 执行删除逻辑
    performDelete(dialog.fileName)
    // 关闭对话框
    uiState.copy(dialogState = <Feature>DialogState.None).setup()
}

// 用户取消 —— 仅关闭对话框
@UiIntentObserver(<Feature>UiIntent.DismissDialog::class)
private fun onDismissDialog() {
    val uiState = getOrNull<<Feature>UiState.Normal>() ?: return
    uiState.copy(dialogState = <Feature>DialogState.None).setup()
}
```

**Step 4: Compose 中渲染对话框并通过 UiIntent 回传结果**

```kotlin
@Composable
private fun DialogSwitch(
    dialogState: <Feature>DialogState,
    emitIntent: (<Feature>UiIntent) -> Unit
) {
    when (dialogState) {
        is <Feature>DialogState.None -> Unit
        is <Feature>DialogState.DeleteConfirm -> TextConfirmDialog(
            message = "确认删除 ${dialogState.fileName}?",
            onDismissRequest = { emitIntent(<Feature>UiIntent.DismissDialog) },
            onConfirmRequest = { emitIntent(<Feature>UiIntent.ConfirmDelete) }
        )
    }
}
```

### 6.6 异步任务

```kotlin
// 将耗时操作加入异步队列（可取消，自动保存/恢复状态快照）
@UiIntentObserver(SomeIntent::class)
private suspend fun onSomeIntent() {
    val uiState = getOrNull<UiState.Normal>() ?: return
    enqueueAsyncTask {
        uiState.copy(loadState = LoadState.Processing).setup()
        // ... 耗时操作
        currentCoroutineContext().ensureActive()  // 检查取消
        uiState.copy(loadState = LoadState.None).setup()
    }
}
```

---

## 七、Activity 编写规范

### 7.1 标准 Activity（有 ViewModel + ViewEvent）

```kotlin
class <Feature>Activity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<<Feature>ViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        // 监听 Finished 状态自动关闭页面
        LaunchedEffect(uiState) {
            if (uiState is <Feature>UiState.Finished) finish()
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            <Feature>Layout(
                uiState = uiState,
                emitIntent = { intent -> mViewModel.emit(intent) }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(<Feature>UiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(<Feature>UiIntent.Resume)
    }

    // 处理 Feature 专属 ViewEvent
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)  // 先处理通用 AppViewEvent
        when (viewEvent) {
            is <Feature>ViewEvent.SomeEvent -> { /* 处理 */ }
        }
    }
}
```

### 7.2 简单 Activity（无 ViewModel）

```kotlin
class <Feature>Activity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        Surface(modifier = Modifier.fillMaxSize()) {
            <Feature>Layout(onBack = { finish() })
        }
    }
}
```

---

## 八、Compose UI 编写规范

### 8.1 Layout 入口组件

```kotlin
@Composable
fun <Feature>Layout(
    uiState: <Feature>UiState,
    emitIntent: (uiIntent: <Feature>UiIntent) -> Unit = {}
) {
    when (uiState) {
        <Feature>UiState.None, <Feature>UiState.Finished -> Unit

        is <Feature>UiState.Normal -> {
            NormalView(uiState, emitIntent)
            LoadDialogSwitch(uiState.loadState, emitIntent)
            DialogSwitch(uiState.dialogState)
        }
    }
}
```

### 8.2 组件拆分原则

**仅传递子组件所需的数据**，避免传递整个 UiState：

```kotlin
// ✅ 仅传递相关子状态
@Composable
private fun ContentView(
    listState: <Feature>ListState,
    emitIntent: (<Feature>UiIntent) -> Unit
) { ... }

// ❌ 传递整个父状态会导致不必要的重组
@Composable
private fun ContentView(uiState: <Feature>UiState.Normal) { ... }
```

### 8.3 对话框渲染

对话框通过 `when` switch 独立于主内容渲染，**所有用户操作通过 `emitIntent` 回传 ViewModel**：

```kotlin
@Composable
private fun DialogSwitch(
    dialogState: <Feature>DialogState,
    emitIntent: (<Feature>UiIntent) -> Unit
) {
    when (dialogState) {
        is <Feature>DialogState.None -> Unit
        is <Feature>DialogState.SomeConfirm -> TextConfirmDialog(
            message = dialogState.message,
            onDismissRequest = { emitIntent(<Feature>UiIntent.DismissDialog) },
            onConfirmRequest = { emitIntent(<Feature>UiIntent.ConfirmDialog) }
        )
    }
}
```

> [!WARNING]
> **不得**在 Compose 中直接控制对话框的展示/关闭逻辑（如直接修改 `mutableStateOf` 变量控制显隐）。对话框的状态必须来自 ViewModel 推送的 UiState，用户操作必须通过 UiIntent 回传。

### 8.4 Intent 传递方式

通过 `emitIntent` lambda 统一传递，**不在 Compose 中直接持有 ViewModel 引用**：

```kotlin
// ✅ 通过 lambda
Button(onClick = { emitIntent(<Feature>UiIntent.SomeAction) })
// ✅ 或者使用更优秀的拓展lambda
Button(onClick = { <Feature>UiIntent.SomeAction.emit() })

// ❌ 直接引用 ViewModel
Button(onClick = { viewModel.emit(...) })
```

### 8.5 Preview 规范

每个 UI 组件应编写 `@Preview` 函数：

```kotlin
@Preview(widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun SomePreview() {
    AppTheme(dynamicColor = false) {
        <Feature>Layout(
            uiState = <Feature>UiState.Normal(
                // 构造测试数据
            )
        )
    }
}
```

- 使用 `AppTheme(dynamicColor = false)` 包裹
- Preview 函数标记为 `private`
- 需要时用 `TestUtils` 构造测试数据

---

## 九、依赖注入（Koin）规范

### 模块注册

在 Application 类中注册全局单例：

```kotlin
private val appModules = module {
    singleOf(::SomeManager)
    singleOf(::AnotherService)
}
```

### ViewModel 中使用

```kotlin
class <Feature>ViewModel : CoreViewModelWithEvent<...>(...), KoinComponent {
    private val mSomeManager by inject<SomeManager>()   // 延迟注入
    val context = get<Context>()                          // 即时获取
}
```

---

## 十、数据持久化规范

使用 Kotpref（`KotprefModel`）存储简单配置：

```kotlin
object AppModel : KotprefModel() {
    var someBooleanSetting by booleanPref(default = false)
    var someIntSetting by intPref(default = 0)
    var someNullableString by nullableStringPref(default = null)
    
    const val SOME_CONSTANT = "value"
}
```

> [!IMPORTANT]
> AppModel 的读写应在 ViewModel 中进行，**不在 Compose 中直接读写**。

---

## 十一、命名规范

| 类型 | 命名模式 | 说明 |
|------|---------|------|
| Activity | `<Feature>Activity` | |
| ViewModel | `<Feature>ViewModel` | |
| UiState | `<Feature>UiState` | 顶层 sealed class |
| UiIntent | `<Feature>UiIntent` | 顶层 sealed class |
| ViewEvent | `<Feature>ViewEvent` | 顶层 sealed class，按需创建 |
| 子状态 | `<Feature>DialogState` / `<Feature>LoadState` | |
| 顶层 Layout | `<Feature>Layout` | Compose 入口函数 |
| 私有成员变量 | `m` 前缀 + 大驼峰 | 如 `mSomeManager` |
| Intent 处理方法 | `on` + Intent名 | 如 `onInit`、`onItemSelected` |
| 枚举 | 语义化命名或 `XxxEnum` 后缀 | |
| 扩展函数文件 | `<Type>Extensions.kt` | 如 `FileExtensions.kt` |

### 代码风格

- **注释语言**：中文
- **缩进**：4 空格
- **文件末尾**：保留一个空行
- **import**：不使用通配符

### 中文注释规范

注释用于说明代码本身无法直接表达的业务语义、生命周期和约束，不应逐字复述变量名或语句。

1. **公共及跨模块声明使用 KDoc**
   - `public` / `internal` 的类、接口、数据结构、枚举和可复用顶层函数应说明职责。
   - 数据结构应说明它属于持久化模型、领域模型还是 UI 模型；字段含义不直观时使用属性 KDoc。
   - Repository、Builder、Engine、Codec、Adapter 等跨模块组件应说明输入、输出和主要副作用。
2. **ViewModel 注释关注状态与生命周期**
   - 长生命周期成员应说明其缓存内容、清理时机和并发职责。
   - Intent 处理函数仅在包含业务分支、持久化、副作用或特殊状态恢复时添加注释。
   - 不为 `onBack()`、单字段 `copy()` 等直观操作添加重复说明。
3. **私有函数注释解释原因和约束**
   - 对 Prompt 顺序、预算裁剪、流式落库、兼容回退、事务边界等复杂流程说明“为什么这样做”。
   - 简单映射、格式化和单行委托函数通常不需要注释。
4. **Compose 注释以组件职责为单位**
   - 页面入口和可复用组件使用 KDoc。
   - 复杂布局可在分区前使用短行注释，不对每个 `Row`、`Text`、`Modifier` 逐行解释。
5. **注释必须随行为更新**
   - 修改状态机、执行顺序、持久化语义或兼容行为时，同步检查相邻 KDoc。
   - 禁止保留与当前实现冲突的历史说明、TODO 或被注释掉的旧代码。

推荐写法：

```kotlin
/** 构建最终出站 Prompt，并返回成功生成后才应持久化的世界书时序状态。 */
fun buildWithMetadata(context: PromptBuildContext): PromptBuildResult

// 流式阶段只更新内存 UI；结束、停止或异常时再统一决定落库或删除占位消息。
private var mStreamingContent: String = ""
```

不推荐写法：

```kotlin
// 设置标题
val title = value

// 点击返回
private fun onBack()
```

---

## 十二、新功能开发 Checklist

1. **创建 feature 目录**：`feature/<name>/` 及子目录 `model/`、`presentation/`、`ui/`
2. **定义 presentation 三件套**：
   - `<Feature>UiState`：必含 `None` + `Normal`，按需 `Finished`
   - `<Feature>UiIntent`：必含 `Init`，按需 `Back`、`Resume`
   - `<Feature>ViewEvent`：仅在需要 Activity 级操作时创建
3. **实现 ViewModel**：继承 `CoreViewModelWithEvent`，实现 `KoinComponent`，用 `@UiIntentObserver` 处理 Intent
4. **实现 Activity**：继承 `CoreActivityWithEvent`，在 `ViewContent()` 中 collect state 并传给 Layout
5. **实现 Compose UI**：顶层 `<Feature>Layout` 接收 `uiState` + `emitIntent`，按状态树拆分子组件
6. **编写 Preview**：每个组件编写 `@Preview`
7. **注册**：`AndroidManifest.xml` 注册 Activity；新 Manager/Service 在 Koin 模块中注册

---

## 十三、最佳实践

### 13.1 高内聚低耦合

- 每个模块（feature、manager、工具类）应职责单一，对外暴露最小必要接口
- 模块之间通过 Koin 注入解耦，不直接创建依赖实例
- 相关联的逻辑应聚合在同一个类/文件中，不分散到多处

### 13.2 业务逻辑分层

> [!IMPORTANT]
> **严禁在 Compose / UI / Activity 层编写具体的业务逻辑。**

各层职责如下：

| 层级 | 允许做的事 | 禁止做的事 |
|------|-----------|-----------|
| **Compose (ui/)** | 根据 UiState 渲染 UI、发送 UiIntent | 数据处理、IO 操作、条件判断业务逻辑 |
| **Activity** | 收集状态、处理 ViewEvent（跳转/权限）| 业务计算、数据转换 |
| **ViewModel** | 处理 Intent、执行业务逻辑、更新状态 | 直接操作 View、持有 Activity 引用 |
| **Manager / Service (libs/)** | 封装可复用的业务能力 | 持有 UI 状态 |

```kotlin
// ✅ Compose 只负责渲染和发送意图
@Composable
fun SomeView(items: List<Item>, emitIntent: (UiIntent) -> Unit) {
    LazyColumn {
        items(items) { item ->
            ItemCard(item = item, onClick = { emitIntent(UiIntent.ItemClick(item.id)) })
        }
    }
}

// ❌ 不要在 Compose 中做业务判断
@Composable
fun SomeView(items: List<Item>) {
    val filtered = items.filter { it.isValid && it.size > 1024 }  // 业务逻辑泄露到 UI
    val sorted = filtered.sortedBy { it.name }                     // 应在 ViewModel 中完成
}
```

### 13.3 合理封装

- 将复杂的状态操作封装为 ViewModel 的私有方法或 UiState 的扩展函数
- 将可复用的业务能力封装到 `libs/manager/` 或 `libs/utils/` 中
- 将通用 UI 组件封装到 `ui/widges/` 或 `ui/dialogs/` 中
- 避免方法/函数过长，单个函数建议不超过 80 行，超出时应拆分

### 13.4 控制代码嵌套深度

> [!WARNING]
> **普通代码嵌套层级不得超过 4 层。** Compose 可适当放宽，但出现大量嵌套时也必须按页面模块拆分 Composable 函数。

**降低嵌套的技巧**：

1. **提前 return（卫语句）**：

```kotlin
// ✅ 用 guard clause 减少嵌套
@UiIntentObserver(SomeIntent::class)
private suspend fun onSomeIntent(intent: SomeIntent) {
    val uiState = getOrNull<UiState.Normal>() ?: return
    val listState = uiState.listState as? ListState.Ready ?: return
    if (uiState.loadState !is LoadState.None) return

    // 主逻辑（仅1层嵌套）
    uiState.copy(...).setup()
}

// ❌ 层层嵌套
private suspend fun onSomeIntent(intent: SomeIntent) {
    val uiState = getOrNull<UiState.Normal>()
    if (uiState != null) {
        val listState = uiState.listState
        if (listState is ListState.Ready) {
            if (uiState.loadState is LoadState.None) {
                // 主逻辑已在第4层
            }
        }
    }
}
```

2. **抽取子函数**：

```kotlin
// ✅ 将嵌套逻辑抽取为独立函数
private suspend fun UiState.Normal.processItems(): UiState.Normal {
    val result = withContext(Dispatchers.IO) { doHeavyWork() }
    return copy(data = result)
}

// 调用处保持简洁
uiState.processItems().setup()
```

3. **Compose 拆分**：

```kotlin
// ✅ 嵌套过深时按模块拆分
@Composable
fun FeatureLayout(uiState: UiState.Normal, emitIntent: (UiIntent) -> Unit) {
    Scaffold(topBar = { FeatureTopBar(uiState.title) }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ContentSection(uiState.listState, emitIntent)  // 拆分
        }
    }
}

@Composable
private fun ContentSection(listState: ListState, emitIntent: (UiIntent) -> Unit) {
    // 独立的子组件，避免在 FeatureLayout 中继续嵌套
}

// ❌ 所有内容堆在一个函数里
@Composable
fun FeatureLayout(uiState: UiState.Normal) {
    Scaffold(topBar = { ... }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column {
                Row {
                    LazyColumn {
                        items(uiState.items) { item ->
                            Card {
                                // 已经嵌套了7层...
                            }
                        }
                    }
                }
            }
        }
    }
}
```

### 13.5 其他

1. **状态不可变**：始终通过 `copy()` + `setup()` 更新，不直接修改
2. **状态特化**：ViewModel 中的操作应根据当前状态特化，部分意图只能在特定状态下执行
3. **IO 线程切换**：IO 操作使用 `withContext(Dispatchers.IO)`
4. **长耗时任务**：使用 `enqueueAsyncTask` 入队，支持取消与状态恢复
5. **协程取消检查**：长循环中调用 `currentCoroutineContext().ensureActive()`
6. **错误处理**：用 `runCatching` 包裹可能失败的操作，通过 `AppViewEvent.PopupToastMessage` 展示错误
7. **Compose 重组优化**：拆分 Composable 函数，仅传递相关子状态数据，减少不必要的重组
