# RPClient 编码规范主入口

本文档是 RPClient 后续开发的通用编码规范入口。AI 编码时先读本文件，再按任务类型读取对应专题文档，避免一次性加载所有规范。

---

## 1. 项目目标

RPClient 是面向 Android 的本地优先 AI 角色扮演聊天客户端。所有功能实现都应优先保证：

1. 架构清晰：页面使用 Feature + MVI 分层。
2. 状态可追踪：UiState 按 View 结构建树。
3. 数据可控：角色卡、世界书、聊天记录、Prompt、Regex 脚本和 Provider 配置的生命周期清晰。
4. 隐私克制：API Key、请求日志、私密对话、导入资源和本地文件路径不随意暴露。
5. 兼容稳定：SillyTavern 兼容逻辑、Prompt 构建、世界书触发和 LLM 协议适配必须可验证。
6. 改动可控：只修改与需求相关的最小范围。
7. 可维护：编写代码时主动考虑必要注释，帮助后续维护者理解关键意图和约束。

---

## 2. 快速阅读路线

按任务选择需要阅读的专题。执行任务前必须读取相关专题文档，并严格遵循文档中的规范。

| 任务类型 | 必读文档 |
|----------|----------|
| 新增页面/调整目录 | [项目结构与分层](./guides/project-structure-and-layers.md) |
| 设计或修改页面状态 | [MVI 与 UiState 树](./guides/mvi-and-uistate-tree.md) |
| 新增 Intent、系统选择器、跳转、一次性提示 | [UiIntent 与 ViewEvent](./guides/intent-and-viewevent.md) |
| 编写 ViewModel、Activity、Compose | [ViewModel、Activity 与 Compose](./guides/viewmodel-activity-compose.md) |
| 编写或修改代码（代码美观与可读性） | [代码美观与可读性规范](./guides/code-elegance-and-readability.md) |
| 编写或修改代码注释与 KDoc | [代码注释与 KDoc 规范](./guides/comments-and-kdoc.md) |
| 新增数据库表、DAO、Repository、文件记录 | [Room 数据层](./guides/room-data-layer.md) |
| 新增依赖注入或偏好配置 | [Koin 与 Kotpref](./guides/koin-and-kotpref.md) |
| 涉及角色卡、世界书、Prompt、Regex、LLM 请求或日志 | [RPClient 领域规范](./guides/rpclient-domain.md) |
| 新增或修改用户可见文案、错误提示 | [文案与本地化](#7-文案与本地化)（本文件） |

如果任务横跨多个模块，只读取相关专题。例如“导入角色卡并创建会话”应读取 ViewEvent、Room、RPClient 领域规范；“调整聊天页生成流程”应读取 MVI、ViewModel、RPClient 领域规范。

涉及页面布局、颜色、主题、图标和组件样式时，应先查看相邻页面的 Compose 实现、`ui/theme/`、`ui/widgets/` 与 README 截图，保持 Material 3、动态配色和现有信息密度的一致性。

---

## 3. 通用分层规则

RPClient 使用 Compose + MVI + Koin + Room + Kotpref。

```text
Compose View --UiIntent--> ViewModel --UiState--> Compose View
                              |
                              | ViewEvent
                              v
                           Activity
```

| 层级 | 负责 | 不负责 |
|------|------|--------|
| Compose `ui/` | 按 `UiState` 渲染、发送 `UiIntent` | 业务判断、IO、Room、网络请求、文件读写 |
| Activity | 创建 ViewModel、收集状态、处理 ViewEvent | 业务计算、数据转换、直接读写偏好或数据库 |
| ViewModel | 处理 Intent、调用数据层、更新 State/Event | 持有 View/Activity、直接操作 Compose 状态、直接访问 DAO |
| Repository/Codec/Builder/Runtime | Room、文件、导入导出、Prompt 构建、LLM 适配、Regex 执行等封装 | 持有页面状态 |
| AppModel | 小体量全局偏好和默认 Prompt 配置 | 角色/会话/世界书实体、API Key 以外的大段可恢复业务数据、请求日志 |

---

## 4. 通用代码风格

1. 4 空格缩进。
2. import 不使用通配符。
3. 代码美观与可读性遵循 [代码美观与可读性规范](./guides/code-elegance-and-readability.md)。
4. 注释遵循 [代码注释与 KDoc 规范](./guides/comments-and-kdoc.md)。
5. 注释与文档中**一律不使用数字序号**（如 `1. xxx`、`// 1. xxx` 等），多项列举统一使用 `- xxx` 短横线无序列表。
6. **行数大于 16 行的方法**，方法体内部必须添加分步行内注释。
7. 不写复述代码的注释。
8. 编写代码时主动评估是否需要补充维护性注释，尤其是兼容性、数据一致性、生命周期、Prompt 预算、协议差异和非直观取舍。
9. 普通函数建议不超过 80 行，嵌套不超过 4 层。
10. 优先 guard clause，少写层层嵌套。
11. 跨行调用全量使用具名实参，杜绝半裸传参；复杂条件提取解释性布尔变量；Modifier 链独立成行。
12. 不做无关重构，不改动与需求无关的框架代码。
13. 不引入未经需求确认的大型框架或架构重写。

### 导入与类型引用

- 类型、构造函数及顶层函数优先通过明确的 `import` 引入，在声明、参数、返回值、泛型和函数体内使用简短名称。
- 不得为了省略 `import` 而反复内联完整包名。例如使用 `val imageState: MessageImageState = MessageImageState()`，避免写成 `val imageState: me.kafuuneko.rpclient.libs.media.MessageImageState = me.kafuuneko.rpclient.libs.media.MessageImageState()`。
- 只有存在名称冲突或必须消除歧义时才使用全限定名；名称冲突优先考虑语义清晰的 `import ... as ...` 别名，需要全限定名时限制在最小范围。
- 修改代码后检查新增引用，清理冗余包名前缀与未使用的导入，不引入通配符导入。

---

## 5. 代码美观与可读性概要

详细规范与丰富示例参见 **[代码美观与可读性规范](./guides/code-elegance-and-readability.md)**。

### 5.1 核心通用原则

- **多行调用全量具名**：跨行函数、构造器及 `copy()` 调用必须全量使用具名实参（`param = value`），坚决杜绝裸传位置参数。
- **严禁半裸传参**：一旦某个参数使用了具名实参（如 Compose 的 `style = ...`），前面的所有参数必须全量具名（如 `text = ...`），严禁混用位置参数与具名参数。
- **字面量强制具名**：布尔值（`true`/`false`）、数字及 `null` 等无上下文语义的字面量必须显式命名形参。
- **解释性变量分解复杂条件**：严禁在 `if` 或条件语句中堆叠多个 `&&`/`||` 与复杂运算，必须提取具有明确业务语义的解释性局部布尔变量；多行条件运算符统一置于行首。
- **卫语句扁平化**：优先使用 Guard Clause 提前返回，消除多层缩进金字塔，主逻辑保持在最外层。
- **Compose 规范**：Modifier 链每个操作符必须单起一行；组件参数内严禁内联巨型计算或长串 `when`，提前提取展示属性；非尾部内容槽位回调必须具名。
- **流式调用与作用域**：多行或嵌套 Lambda 严禁使用隐式 `it`，必须显式声明语义参数；长流式链操作符独占一行；杜绝作用域函数无节制嵌套。

---

## 6. 注释规范概要

详细规范与丰富示例参见 **[代码注释与 KDoc 规范](./guides/comments-and-kdoc.md)**。

### 6.1 核心通用原则

- **标准中文与通俗易懂**：统一使用规范、简洁且表意清晰的中文，直接阐明核心业务逻辑与设计意图。
- **严禁使用数字序号**：文档与行内注释中**一律不使用** `1. xxx`、`2. xxx` 或 `// 1. xxx`、`// Step 1: xxx` 等数字编号；列表统一采用 `-` 无序列表，行内步骤直接自然描述。
- **行数大于 16 行的方法必须写行内注释**：标注前置校验、异步 IO、核心转换计算、持久化与安全收尾节点。
- **不写废话注释**：不写“给变量赋值”“调用方法”“返回结果”等复述代码的注释。
- **隐私与安全**：严禁在注释中包含 API Key、真实请求头、私密对话、真实本地路径或服务商账号信息。

### 6.2 类与方法 KDoc 覆盖要求

- **类级别 KDoc**：所有 `ViewModel`、`Repository`、`Manager`、`Codec`、`Builder`、`Runtime` 等核心业务类必须编写 KDoc，简述架构角色并用 `-` 列举核心职责与关键设计模式。
- **方法级别 KDoc**：所有 `public`/`protected` 方法、所有意图响应函数（`@UiIntentObserver`）及重要内部业务函数必须编写 KDoc，首句说明功能，复杂逻辑用 `-` 列举时序与边界规则，标明 `@param`、`@return`。

### 6.3 TODO 规范

TODO 必须说明后续动作和触发条件，禁止写空泛占位（如 `// TODO: 优化`）。

推荐：
```kotlin
// TODO: 增加 Claude tool use 支持后，将 Anthropic 消息后处理拆出独立 adapter。
```

---

<span id="6-文案与本地化"></span>
## 7. 文案与本地化

文案按用途分为用户可见文案、内部诊断文案和代码注释三类，处理方式不同，不要混用。

### 7.1 用户可见文案

用户可见文案包括按钮、标题、占位文字、状态提示、Toast、对话框、无障碍描述、图表与检查器标签，以及图片、文件、模型能力检查和 Prompt 预算等失败提示。

- 一律通过 Android 字符串资源提供：`res/values/strings.xml` 使用英文，并同步补齐全部已支持语言目录，缺少任一语言的翻译都视为未完成。当前语言目录：`values-b+zh+Hans`、`values-b+zh+Hant`、`values-b+pt+BR`、`values-de`、`values-es`、`values-fr`、`values-id`、`values-it`、`values-ja`、`values-ko`、`values-ru`、`values-vi`。
- 新增或修改文案时，译文必须与英文源保持相同的占位符编号、顺序和类型；含占位符的格式化文案中的撇号必须转义为 `\'`，避免运行时格式化崩溃。
- 仅标记 `translatable="false"` 的字符串（如 `app_name`）可以不翻译。
- Kotlin、Compose、ViewModel、Repository、Codec、Builder、Runtime 中不得硬编码中文或英文提示。
- 动态内容使用资源占位符（`%1$s`、`%2$d` 等），不拼接完整提示句；资源名按 `<模块>_<语义>` 命名，如 `image_prepare_failed`、`prompt_inspector_image_meta`。
- 按展示方式选择承载形态：随状态长期展示的提示在 UiState 中保存 `@StringRes` 资源 ID（如 `MessageImageState.errorResId`）或已本地化字符串（如 `GenerationFailurePresentation.message`）；一次性提示使用 `AppViewEvent.PopupToastMessageByResId`。
- 判断文案是否会展示给用户，不能只看它位于哪个类：内部异常可能被捕获后直接展示。必须追踪异常的使用链路，用户提示映射为本地化资源，不直接展示内部异常原文。
- 保持现有 MVI 分层：Compose 只解析资源并渲染，不为了获取资源而承担业务逻辑；领域层不引入 `Activity`、`View`、`Context` 依赖，需要本地化时把资源 ID 或结构化错误交给上层映射。

### 6.2 内部诊断文案

内部异常、`require`/`check`/`error` 的消息、日志和调试诊断文案统一使用英文，不允许使用中文。

- 典型场景：图片处理与文件草稿校验、模型能力检查、Prompt 预算与摘要校验、请求日志脱敏占位标记。
- 写入请求日志的脱敏与截断占位标记属于诊断内容，不参与界面本地化，保持英文即可。
- 优先使用结构化错误表达失败原因（如 `ImageRequestException`、`GenerationFailure`），由上层映射为本地化资源；只有明确的程序性前置条件才直接抛出带英文消息的异常。

### 6.3 代码注释与 KDoc

中文代码注释和 KDoc 继续遵循 [代码注释与 KDoc 规范](./guides/comments-and-kdoc.md)，不因内部诊断要求英文而批量翻译成英文。

### 6.4 不属于文案的内容

- `@Preview` 与测试中的示例数据只用于开发预览和测试断言，可以使用任意语言，不需要资源化。
- 纯符号或单位标签（如 `←`、`×`、`·`、`Tk`）不视为文案；当它是控件的唯一标签时，按需补充本地化无障碍描述。
- 具有程序语义的字符串不按文案处理：协议字段名、序列化标识、数据库键、MIME 类型、模型输出前置词匹配模式（如 `StoryOutputSanitizer` 的 `KNOWN_PREAMBLES`）等保持原样。

---

## 8. 单元测试规范与反模式

单元测试的目的是**保障核心纯算法正确性、保护协议兼容性**。测试代码同样具有维护成本，严禁追求形式上的测试覆盖率或编写无实质业务价值的垃圾单测。

### 8.1 必须/优先编写单元测试的场景

仅在以下具备高逻辑密度、高易碎性或严格兼容要求的场景下编写单元测试：

1. **复杂纯算法与规则计算**：
   - Prompt Token 预算裁剪与优先级淘汰算法（如 `WorldBookBudgeter`）。
   - 世界书扫描、递归激活、互斥包含组、Depth 与 Sticky/Cooldown 状态计算。
   - 正则脚本执行引擎、文本替换模式过滤（Source / Markdown / Prompt）。
   - 群聊发言者轮询选择算法、输出内容清理（Think 块、空白修剪等）。
2. **协议编解码与外部兼容**：
   - Character Card V1/V2、SillyTavern 世界书 JSON 的解析与向后兼容。
   - 跨版本升级脚本（`AppUpgrade`）的核心迁移逻辑。
   - LLM Provider 协议转换、请求体 Patch、非标响应体解析与容错。

### 8.2 严禁编写的无用单元测试（Anti-Patterns）

AI 在编码过程中**严禁**主动生成以下类型的无意义单元测试：

1. **禁止平凡映射测试 (Trivial Mapping)**：
   - 严禁为简单的 `Enum <-> Entity`、`Entity <-> UiModel` 之间的 1:1 双向映射写测试。
   - 严禁为没有复杂计算转换的单纯属性传递写测试。
2. **禁止测试标准库与框架本身 (Testing the Framework/Language)**：
   - 严禁测试 Kotlin 的 `data class.copy()`、基础 `equals/hashCode`、默认参数值。
   - 严禁测试标准库集合操作（如单纯把 Map 中的键值翻转）、简单日期字符串格式化。
   - 严禁测试 Room、Gson、Koin 等成熟框架自带的基础注解或反序列化能力。
3. **禁止纯 Mock 的仪式性测试 (Pure Mock Ceremonies)**：
   - 严禁编写把所有依赖全部 mock 掉、仅仅断言 `verify(dao).insert(any())` 调用了一次的空洞测试。这类测试不验证任何业务逻辑，反而导致重构极其脆弱。
4. **禁止为简单 UI/ViewModel 状态流转编写测试**：
   - 没有复杂分支计算的 ViewModel（仅单纯接收 Intent、调用 Repository 并把结果赋值给 UiState）不需要写单测。
5. **禁止为了满足 Checklist 凑数而编写测试**：
   - 如果本次需求只是调整 UI 样式、增加简单页面字段、修改文本或进行直观的胶水代码连接，**明确不需要**创建任何新的测试文件。

---

## 9. 命名速查

| 类型 | 命名 |
|------|------|
| Activity | `<Feature>Activity` |
| ViewModel | `<Feature>ViewModel` |
| UiState | `<Feature>UiState` |
| UiIntent | `<Feature>UiIntent` |
| ViewEvent | `<Feature>ViewEvent` |
| 子状态 | `<Feature>TopBarState`、`<Feature>ContentState`、`<Feature>DialogState` |
| UI model | `<Name>UiModel` 或 `<Name>Item` |
| Entity/DAO/Repository | `<Name>`、`<Name>Dao`、`<Name>Repository` |
| Codec/Builder/Runtime | `<Domain>Codec`、`<Domain>Builder`、`<Domain>Runtime` |
| 私有成员 | `m` 前缀，如 `mChatRepository` |

---

## 10. AI 编码 Checklist

新增或修改功能时按此检查：

1. 是否新建或复用了正确 feature，而不是把业务堆进 `main`？
2. UiState 是否按 View 树拆成节点？
3. 数据是否按生命周期放在正确层级？
4. Intent 是否使用用户行为命名？
5. 一次性系统动作是否通过 ViewEvent？
6. Compose 是否只渲染和发 Intent？
7. 数据访问是否通过 Repository/Codec/Builder/Runtime？
8. 新 Repository/Builder/Runtime 是否注册 Koin？
9. 新 Room 表是否更新 `AppDatabase`？
10. 用户可见文案是否全部来自字符串资源，内部异常与日志文案是否统一为英文，且全部语言目录已同步补齐（占位符与英文源一致）？
11. 是否避免在普通状态、提示或非调试日志中泄露 API Key、请求头、私密对话、真实路径和完整原始响应？
12. 是否遵循代码美观与可读性规范（跨行调用全量具名、消除半裸传参、复杂条件提取解释性变量、Compose Modifier 独立行、消除隐式嵌套 it）？
13. 是否仅在引入复杂纯逻辑、关键算法分支、协议编解码兼容或 Bug 回归时补充必要单元测试，严禁堆砌无价值的无用测试？
14. 新增注释是否解释了必要约束，而不是复述代码？
15. 是否运行最小验证？

常用验证：

```powershell
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug
```

如果必须联网下载依赖，先说明原因。

---

## 11. 当前框架入口

常用框架文件：

```text
app/src/main/java/me/kafuuneko/rpclient/RPClientApp.kt
app/src/main/java/me/kafuuneko/rpclient/AppLibs.kt
app/src/main/java/me/kafuuneko/rpclient/libs/AppModel.kt
app/src/main/java/me/kafuuneko/rpclient/libs/core/
app/src/main/java/me/kafuuneko/rpclient/libs/room/
app/src/main/java/me/kafuuneko/rpclient/libs/prompt/
app/src/main/java/me/kafuuneko/rpclient/libs/llm/
app/src/main/java/me/kafuuneko/rpclient/libs/regex/
app/src/main/java/me/kafuuneko/rpclient/feature/main/
app/src/main/java/me/kafuuneko/rpclient/feature/chat/
app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/
```

除非明确修框架问题，否则优先新增业务层代码，不随意修改 `libs/core/`。
