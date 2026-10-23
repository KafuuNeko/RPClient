# 代码美观与可读性规范

本文档确立并规范 RPClient 项目中的代码美观度（Elegance）、排版格式（Formatting）与可读性（Readability）标准。AI 与开发者在编写、重构或审查代码时，必须严格遵守本文档。

---

## 核心设计哲学

- **代码首先是写给人读的，顺便由机器执行**：优秀的实现必须具备高自解释性，降低后续维护者与代码审查的心智负担。
- **结构清晰先于行数节省**：严禁为了追求“少写几行”而将复杂逻辑挤成密密麻麻的一行，拒绝难以调试和阅读的代码高尔夫（Code Golf）。
- **杜绝猜谜与隐式调用**：通过显式具名实参、语义明确的解释性中间变量与扁平的卫语句，让控制流与数据流一目了然。
- **保留清晰的紧凑表达**：单个含义明确的实参、简短 Lambda 和构造表达式可以保持简洁；根据参数语义与表达式复杂度决定换行，避免机械地增加参数名、局部变量或行数。
- **格式化服务于阅读**：自动格式化的结果仍须按本文复核。审查调用时同时考虑参数数量、同类型参数的辨识难度、集合转换与回调的组合，不仅根据字符数判断是否需要整理。
- **列表严禁使用数字序号**：文档与注释中一律使用 `- xxx` 短横线无序列表。

---

## 具名实参（Named Arguments）与调用排版

### 规则：多行调用优先全量显式命名

- 函数、构造器及 `copy()` 调用跨行时，语言与 API 支持的情况下，**默认全量使用具名实参**（`param = value`），让调用点直接说明每个参数的作用。
- Java 方法、函数类型调用或部分 vararg API 受语言限制时，保留合法的位置实参形式；不能为了具名而改变 API、参数顺序或展开方式。
- 本文中的位置参数分组示例仅说明未具名调用的换行方式，不代表一般性放宽多行调用的具名要求，也不应据此删除已有的形参名。
- Android Studio 显示的灰色形参提示不属于源码，不能作为已经使用具名实参的依据。

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

- 在函数调用（特别是 Jetpack Compose 组件）中，一旦有一个参数使用了具名参数（如 `style = ...`、`modifier = ...`），其余可以具名的参数也应补齐形参名（如 `text = ...`），避免“半裸”调用。受语言或 vararg 语法限制的部分保留合法写法。

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

- 传给函数的布尔字面量（`true` / `false`）、纯数值（如超时毫秒数、重试次数、容量限制）或 `null`，如果无法从调用上下文直接判断含义，必须在 API 支持时显式指明参数名，消除“神秘字面量（Magic Literals）”。
- 单行调用只有一个实参，且函数或构造器已经明确表达其含义时，优先使用简洁的位置参数，包括含义明确的数字字面量；例如 `Color(0xFF3B66F5)`。
- 存在含义歧义、多个容易混淆的同类型参数或需要说明单位时，仍使用具名参数。单参数例外不改变多行调用优先全量具名的规则。

单个含义明确的参数：

```kotlin
val PrimaryColor = Color(0xFF3B66F5)
```

这里构造器已经表明数值是颜色，可以省略 `color =`。

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

- 调用需要跨行时，左括号 `(` 后换行，参数相对调用所在语句缩进 4 空格。
- 右括号 `)` 单独成行，与调用所在语句的起始缩进对齐；即使最后一个参数是 Lambda，也不能把右括号挤到 `}` 后面形成 `})`。
- 具名参数较长、表达式复杂或需要逐项对应时，每个参数独占一行；较短、含义清楚且关系紧密的参数可以放在同一行，分组不能牺牲形参名和阅读清晰度。
- 多行调用与声明继续保留 Kotlin 尾随逗号（trailing comma），便于新增或调整参数时保持清晰的 Git Diff。

反例：

```kotlin
is ChatUiState.Finished -> ChatLayout(
    uiState = uiState.previous,
    background = background,
    emit = {})
```

推荐：

```kotlin
is ChatUiState.Finished -> ChatLayout(
    uiState = uiState.previous,
    background = background,
    emit = {},
)
```

### 规则：未具名调用按参数复杂度分组换行

- 对受语言限制或局部整理时尚未具名的调用，连续的简单参数可以放在同一行，带集合转换、Lambda 等相对复杂表达式的参数另起一行，末尾回调可以单独一行。
- 下面的位置参数示例仅表达分组偏好；在支持具名参数的多行调用中，仍优先采用后面的全量具名写法。
- 简短的 `metadata.mapTo(HashSet()) { it.key }` 可以保留在参数中；确实过长、重复使用或需要解释业务含义时，再提取局部变量。

未具名时的分组示例：

```kotlin
else -> body(
    request, query, cursor, initial, page,
    metadata.mapTo(HashSet()) { it.key },
    onPartial,
)
```

支持具名参数时优先采用：

```kotlin
else -> body(
    request = request,
    query = query,
    cursor = cursor,
    initial = initial,
    metadata = page,
    excluded = metadata.mapTo(HashSet()) { it.key },
    onPartial = onPartial,
)
```

### 规则：赋值表达式优先从调用参数处换行

- 长调用赋给局部变量时，优先保留 `val name = receiver.method(` 在同一行，从参数列表开始换行；该行本身过长时再进一步拆分。
- 参数较短且同一行仍易读时可以紧凑分组，同时遵守多行调用优先全量具名的要求。
- 简短的 Elvis 提前返回表达式（如 `?: return@launch`）跟在结束右括号后，不单独悬挂到更深缩进的一行；右侧表达式较复杂时另行展开。

反例：

```kotlin
val failure =
    error.toGenerationFailurePresentation(mContext, R.string.generation_failed)
        ?: return@launch
```

推荐：

```kotlin
val failure = error.toGenerationFailurePresentation(
    context = mContext, fallbackMessageResId = R.string.generation_failed,
) ?: return@launch
```

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

### 规则：未使用的单个 Lambda 参数省略声明

- Lambda 提供的单个参数没有被使用，且 Kotlin 语法与作用域允许省略时，直接省略 `_ ->`。
- 多行或嵌套 Lambda 的显式命名要求针对实际使用的参数，不应机械地为未使用参数补充 `_ ->`。
- 多参数 Lambda 中仅部分参数未使用，或省略声明会影响类型推断、引用解析时，保留必要的参数声明与 `_` 占位。

反例：

```kotlin
single { _ -> MessageImageRuntime(get(), get()) }
```

推荐：

```kotlin
single { MessageImageRuntime(get(), get()) }
```

这种简短的依赖注册保持单行即可。

### 规则：多步处理或嵌套 Lambda 严禁使用隐式 it

- 仅在非常简短的单行表达式（如 `.map { it.id }`）中允许使用 `it`。
- 实际使用 Lambda 参数，且包含多行逻辑、复杂条件判断或出现 Lambda 嵌套时，**必须显式声明语义参数名**。
- 判断依据是 Lambda 自身的逻辑和嵌套关系；外层函数调用跨行，不会让简单的 `{ it.key }` 自动变成需要改名的多行 Lambda。

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

### 规则：相邻类型声明之间统一空一行

- 同一层级的相邻 `class`、`data class`、`object`、`data object` 等类型声明之间统一保留一个空行，不因声明是单行还是多行而区别处理。
- 连续的单行类型声明也要分隔；一个多行声明结束后同样只空一行。
- KDoc 和注解紧贴其所属声明，空行放在前一个声明与下一组 KDoc、注解或声明之间。
- 这条规则针对独立类型声明，不要求构造器参数、枚举项或一组紧密相关的简单属性逐项插入空行。

示例：

```kotlin
sealed class ChatUiIntent {
    data object ImpersonateUser : ChatUiIntent()

    data class RegenerateFromMessage(val messageId: String) : ChatUiIntent()

    data class BranchFromMessage(
        val messageId: String,
    ) : ChatUiIntent()

    data object ShowSessionLoreDialog : ChatUiIntent()

    data object OpenWorldBookManager : ChatUiIntent()
}
```

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

- 跨行函数调用、构造器与 `uiState.copy(...)` 是否在语言及 API 支持时优先全量具名，且未把位置参数分组示例当成一般例外？
- 是否避免了可以具名却混用位置实参的“半裸”传参？
- 单个含义明确的实参是否保留了简洁写法，对无上下文语义的字面量是否仍使用具名参数？
- 多行调用是否从参数列表换行，右括号是否单独成行，短参数分组和复杂参数换行是否清晰？
- 赋值表达式是否优先保留 `val name = receiver.method(`，简短的 `?: return` 是否紧跟结束右括号？
- 相邻类型声明之间是否统一空一行，KDoc 与注解是否仍归属于正确声明？
- 复杂布尔运算是否已提取语义清晰的局部变量（如 `isAfterBoundary`、`isSameBoundaryParent`）？
- 多行条件的操作符（`&&`、`||`）是否统一置于行首对齐？
- 是否消除了深层嵌套，优先采用了卫语句提前返回？
- Compose 组件的 Modifier 链是否每步独立成行？
- 多行或嵌套 Lambda 中实际使用的参数是否显式命名，未使用的单个参数是否省略了多余的 `_ ->`？
- 组件声明参数和字符串模板内部是否已剥离巨型计算与复杂内联表达式？
