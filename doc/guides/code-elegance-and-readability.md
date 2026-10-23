# 代码美观与可读性规范

本文档确立并规范 RPClient 项目中的代码美观度（Elegance）、排版格式（Formatting）与可读性（Readability）标准。AI 与开发者在编写、重构或审查代码时，必须严格遵守本文档。

---

## 核心设计哲学

- **代码首先是写给人读的，顺便由机器执行**：优秀的实现必须具备高自解释性，降低后续维护者与代码审查的心智负担。
- **结构清晰先于行数节省**：严禁为了追求“少写几行”而将复杂逻辑挤成密密麻麻的一行，拒绝难以调试和阅读的代码高尔夫（Code Golf）。
- **杜绝猜谜与隐式调用**：通过显式具名实参、语义明确的解释性中间变量与扁平的卫语句，让控制流与数据流一目了然。
- **列表严禁使用数字序号**：文档与注释中一律使用 `- xxx` 短横线无序列表。

---

## 具名实参（Named Arguments）与调用排版

### 规则：多行调用全量显式命名

- 只要函数调用、构造器实例化或对象创建发生换行跨多行，**所有参数必须全量使用具名参数**（`param = value`）。
- 严禁在跨行调用中直接裸传位置参数。

反例：
```kotlin
// 难看：多行裸传位置参数，阅读者完全无法一眼看出 a, b, c 到底对应什么语义
call(
    a,
    b,
    c
)
```

优雅正例：
```kotlin
// 优雅：每个实参意图清晰明确，参数调整与重构时具备高安全性
call(
    p1 = a,
    p2 = b,
    p3 = c,
)
```

### 规则：严禁混用位置参数与具名参数（拒绝半裸传参）

- 在函数调用（特别是 Jetpack Compose 组件）中，一旦有一个参数使用了具名参数（如 `style = ...`、`modifier = ...`），前面的所有参数也必须补齐形参名（如 `text = ...`），严禁“半裸”调用。

反例：
```kotlin
// 难看：第一个参数裸传位置实参，后续参数具名，风格割裂且极易产生阅读歧义
Text(  
    entry.name.ifBlank { stringResource(R.string.unnamed_entry) },  
    style = MaterialTheme.typography.titleSmall  
)
```

优雅正例：
```kotlin
// 优雅：统一具名，结构对齐，符合 Compose 标准声明范式
Text(  
    text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },  
    style = MaterialTheme.typography.titleSmall,
)
```

### 规则：无上下文自解释性的字面量必须具名

- 传给函数的布尔字面量（`true` / `false`）、纯数值（如超时毫秒数、重试次数、容量限制）或 `null`，必须显式指明参数名，消除“神秘字面量（Magic Literals）”。

反例：
```kotlin
// 难看：true 和 false 具体代表什么含义完全无法判断
mSessionRepository.updateStatus(sessionId, true, false, 5000)
```

优雅正例：
```kotlin
// 优雅：业务意图直接体现在调用点
mSessionRepository.updateStatus(
    sessionId = sessionId,
    isActive = true,
    notifyObservers = false,
    timeoutMs = 5000,
)
```

### 规则：多参数数据类实例化与 copy() 必须全量具名

- 参数多于 2 个的数据类构造、实体对象创建及 `uiState.copy(...)`，必须全量使用具名参数。

反例：
```kotlin
// 难看：大体量实体构造跨行堆叠未具名参数，参数顺序极易错位
val match = SearchChunkMatch(kind, source.parentId, source.sourceId, 0, owner.latestTime,
    owner.title, owner.names, "", support.sourceLabel(kind, source.sourceId, owner.names))
```

优雅正例：
```kotlin
// 优雅：字段映射一目了然，类型与顺序安全
val match = SearchChunkMatch(
    kind = kind,
    parentId = source.parentId,
    sourceId = source.sourceId,
    phase = 0,
    latestTime = owner.latestTime,
    title = owner.title,
    names = owner.names,
    snippet = "",
    label = support.sourceLabel(kind, source.sourceId, owner.names),
)
```

### 规则：调用括号断行与尾随逗号格式化

- 多行参数排版时：
  - 左括号 `(` 后立即换行。
  - 每个参数独占一行，统一缩进 4 空格。
  - 行尾保留 Kotlin 尾随逗号（trailing comma），便于后续新增或调整参数顺序时的 Git Diff 最小化。
  - 右括号 `)` 单独成行，并与函数声明行起始缩进保持对齐。

---

## 复杂条件判定与解释性变量（Explanatory Variables）

### 规则：严禁在条件语句中堆砌长串复杂布尔运算

- 逻辑操作符（`&&` / `||`）超过 2 个、或包含多层括号、非空检查与复杂方法比对时，严禁硬塞在单行或单个 `if (...)`、`while (...)` 内部。
- 必须提取具有明确业务语义的解释性局部布尔变量（Explanatory Variables）。

反例：
```kotlin
// 难看：长串条件全部塞进 if，边界条件与排除逻辑混成一团，排查问题与断点调试极其痛苦
if (!support.after(hit, boundary) && !(request.parentId != null && boundary != null &&  
        hit.latestTime == boundary.time && hit.parentId == boundary.id && hit.sourceId < boundary.sourceId)) continue
```

优雅正例：
```kotlin
// 优雅：拆解为语义明晰的局部布尔变量，逻辑断点分明，主控制流一目了然
val afterBoundary = support.after(hit, boundary)

val sameBoundaryParent = request.parentId != null
    && boundary != null
    && hit.latestTime == boundary.time
    && hit.parentId == boundary.id
    && hit.sourceId < boundary.sourceId

if (!afterBoundary && !sameBoundaryParent) {
    continue
}
```

### 规则：多行逻辑运算符统一放置在行首

- 当复杂的布尔表达式需要拆解跨行书写时，逻辑连接符 `&&` 与 `||` 统一置于新行的行首并对齐，禁止甩在前一行的行尾。

反例：
```kotlin
// 难看：连接符甩在末尾，容易忽略逻辑关系
val canSendMessage = isNetworkAvailable &&
    !isGenerating &&
    inputContent.isNotBlank()
```

优雅正例：
```kotlin
// 优雅：操作符置于行首，每一项条件平行对齐，层级与逻辑关系清晰可见
val canSendMessage = isNetworkAvailable
    && !isGenerating
    && inputContent.isNotBlank()
```

### 规则：卫语句（Guard Clause）消灭嵌套深度

- 优先使用卫语句（`if (!valid) return`、`if (!valid) continue`）提前拦截非法状态，消除多层缩进金字塔。
- 主流程逻辑始终保持在最外层（零级业务缩进）。

反例：
```kotlin
// 难看：层层嵌套，主逻辑深陷缩进内部
fun processMessage(message: ChatMessage?) {
    if (message != null) {
        if (!message.isDeleted) {
            val content = message.content
            if (content.isNotBlank()) {
                sendToProvider(content)
            }
        }
    }
}
```

优雅正例：
```kotlin
// 优雅：前置守卫提前退出，核心流程扁平通畅
fun processMessage(message: ChatMessage?) {
    if (message == null || message.isDeleted) return
    val content = message.content
    if (content.isBlank()) return
    sendToProvider(content)
}
```

---

## Jetpack Compose 布局与组件声明优雅度

### 规则：Modifier 链式调用独立成行与缩进

- 当 `Modifier` 包含超过 1 个链式调用时，每个修饰符操作（`.fillMaxWidth()`、`.padding(...)` 等）必须独占一行，并保持 4 空格缩进。
- 严禁将一长串修饰符挤在单行之中。

反例：
```kotlin
// 难看：Modifier 长链挤在单行，无法快速阅读组件尺寸、内外边距与交互行为
Box(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp).background(Color.Red).clickable { onClick() })
```

优雅正例：
```kotlin
// 优雅：尺寸、间距、外观与行为纵向排列，层次分明
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .padding(horizontal = 16.dp)
        .background(color = MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick),
)
```

### 规则：尾随 Lambda（Trailing Lambda）的使用边界

- 只有组件参数列表末尾的**唯一内容布局插槽**（Slot，如 `content: @Composable () -> Unit`），才允许使用尾随 Lambda。
- 多个事件回调（如 `onClick`、`onValueChange`）或多个 Composable 插槽时，必须全部使用具名参数标注，严禁随意留出不明用途的悬空 Lambda。

反例：
```kotlin
// 难看：多个 lambda 回调混用尾随或匿名传参，调用意图不明
AppDialog(onDismissRequest, { ConfirmButton() }) {
    DialogBody()
}
```

优雅正例：
```kotlin
// 优雅：事件与插槽职责明确分工
AppDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { ConfirmButton() },
    content = {
        DialogBody()
    },
)
```

### 规则：严禁在组件声明内联巨型计算或深层 when 分支

- 不得在 Compose 组件入参括号内直接塞入大段 `when`、多层三元运算或复杂集合过滤。
- 复杂衍生状态必须在进入组件声明前提取为局部变量（如 `val iconVector = ...`、`val containerColor = ...`）。

反例：
```kotlin
// 难看：在参数内联复杂的条件判断与资源解析，破坏声明式 UI 的整洁性
Icon(
    imageVector = if (uiState.isLoading) Icons.Default.Refresh else if (uiState.isSuccess) Icons.Default.Check else Icons.Default.Warning,
    tint = if (uiState.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    contentDescription = if (uiState.isSuccess) stringResource(R.string.status_success) else stringResource(R.string.status_failed),
)
```

优雅正例：
```kotlin
// 优雅：提前提取表现层局部变量，组件树清爽干净
val statusIcon = when {
    uiState.isLoading -> Icons.Default.Refresh
    uiState.isSuccess -> Icons.Default.Check
    else -> Icons.Default.Warning
}
val statusTint = if (uiState.isSuccess) {
    MaterialTheme.colorScheme.primary
} else {
    MaterialTheme.colorScheme.error
}
val statusDescResId = if (uiState.isSuccess) {
    R.string.status_success
} else {
    R.string.status_failed
}

Icon(
    imageVector = statusIcon,
    tint = statusTint,
    contentDescription = stringResource(statusDescResId),
)
```

---

## 集合流式操作与高阶函数

### 规则：多步处理或嵌套 Lambda 严禁使用隐式 it

- 仅在非常简短的单行表达式（如 `.map { it.id }`）中允许使用 `it`。
- 一旦包含多行逻辑、条件判断或出现 Lambda 嵌套，**必须显式声明语义参数名**。

反例：
```kotlin
// 难看：嵌套使用隐式 it，根本无法分辨哪个 it 指代消息，哪个 it 指代附件
messages
    .filter { it.attachments.isNotEmpty() }
    .flatMap { it.attachments.filter { it.isImage && it.size > 0 } }
```

优雅正例：
```kotlin
// 优雅：参数对象语义明确，杜绝闭包上下文混淆
messages
    .filter { message -> message.attachments.isNotEmpty() }
    .flatMap { message ->
        message.attachments.filter { attachment ->
            attachment.isImage && attachment.size > 0
        }
    }
```

### 规则：长流式调用独立成行与对齐

- 集合操作链超过 2 个步骤时，每个操作符（`.filter`、`.map`、`.sortedBy`、`.firstOrNull` 等）独占一行，操作点 `.` 统一置于行首。

反例：
```kotlin
// 难看：整条流挤在一行，横向超出屏幕
val activeNames = users.filter { it.isActive }.sortedByDescending { it.lastActiveTime }.map { it.displayName }.take(10)
```

优雅正例：
```kotlin
// 优雅：处理流向纵向展开，步骤一目了然
val activeNames = users
    .filter { user -> user.isActive }
    .sortedByDescending { user -> user.lastActiveTime }
    .map { user -> user.displayName }
    .take(10)
```

### 规则：杜绝作用域函数（Scope Functions）的俄罗斯套娃

- 严禁无节制地嵌套使用 `let`、`run`、`also`、`apply`（如 `a.let { it.run { it.also { ... } } }`）。
- 作用域函数应仅用于清晰的可空拆包（`?.let`）或对象属性构建（`apply`），多步骤计算优先使用普通局部变量逐步推进。

---

## 函数签名与长表达式排版

### 规则：多参数声明统一多行排版

- 函数形参超过 3 个或单行超出屏幕宽度时，采用标准多行函数签名排版。

优雅正例：
```kotlin
suspend fun executeGenerationPipeline(
    sessionId: Long,
    promptMode: PromptGenerationMode,
    characterContext: CharacterModel,
    excludedMessageIds: Set<Long> = emptySet(),
): PipelineResult
```

### 规则：字符串模板内严禁嵌入复杂计算

- Kotlin 字符串模板 `${...}` 内部仅允许纯属性访问或方法调用（如 `${user.name}`）。
- 严禁在模板内部塞入多分支三元判定、条件运算或集合转换。

反例：
```kotlin
// 难看：在字符串模板内拼装大段运算逻辑
val info = "Status: ${if (session.isActive && !session.isMuted) "Active (${session.memberCount})" else "Muted/Inactive"}"
```

优雅正例：
```kotlin
// 优雅：提前提取字符串结果
val isStreamingActive = session.isActive && !session.isMuted
val statusDetail = if (isStreamingActive) {
    "Active (${session.memberCount})"
} else {
    "Muted/Inactive"
}
val info = "Status: $statusDetail"
```

---

## 优雅度与代码美观自检清单（Checklist）

编写或修改代码提交前，对照此清单快速自检：

- 跨行函数调用、构造器与 `uiState.copy(...)` 是否全量使用了具名参数？
- 是否彻底杜绝了“半裸”传参（如 `Text(title, style = ...)` 已纠正为 `Text(text = title, style = ...)`）？
- 复杂布尔运算是否已提取语义清晰的局部变量（如 `isAfterBoundary`、`isSameBoundaryParent`）？
- 多行条件的操作符（`&&`、`||`）是否统一置于行首对齐？
- 是否消除了深层嵌套，优先采用了卫语句提前返回？
- Compose 组件的 Modifier 链是否每步独立成行？
- 多行或嵌套 Lambda 是否已消除隐式 `it`，改用显式形参命名？
- 组件声明参数和字符串模板内部是否已剥离巨型计算与复杂内联表达式？
