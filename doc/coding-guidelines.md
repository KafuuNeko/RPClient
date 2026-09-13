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
| 编写或修改代码注释与 KDoc | [代码注释与 KDoc 规范](./guides/comments-and-kdoc.md) |
| 新增数据库表、DAO、Repository、文件记录 | [Room 数据层](./guides/room-data-layer.md) |
| 新增依赖注入或偏好配置 | [Koin 与 Kotpref](./guides/koin-and-kotpref.md) |
| 涉及角色卡、世界书、Prompt、Regex、LLM 请求或日志 | [RPClient 领域规范](./guides/rpclient-domain.md) |

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
3. 注释遵循 [代码注释与 KDoc 规范](./guides/comments-and-kdoc.md)。
4. 注释与文档中**一律不使用数字序号**（如 `1. xxx`、`// 1. xxx` 等），多项列举统一使用 `- xxx` 短横线无序列表。
5. **行数大于 16 行的方法**，方法体内部必须添加分步行内注释。
6. 不写复述代码的注释。
7. 编写代码时主动评估是否需要补充维护性注释，尤其是兼容性、数据一致性、生命周期、Prompt 预算、协议差异和非直观取舍。
8. 普通函数建议不超过 80 行，嵌套不超过 4 层。
9. 优先 guard clause，少写层层嵌套。
10. 不做无关重构，不改动与需求无关的框架代码。
11. 不引入未经需求确认的大型框架或架构重写。

### 导入与类型引用

- 类型、构造函数及顶层函数优先通过明确的 `import` 引入，在声明、参数、返回值、泛型和函数体内使用简短名称。
- 不得为了省略 `import` 而反复内联完整包名。例如使用 `val imageState: MessageImageState = MessageImageState()`，避免写成 `val imageState: me.kafuuneko.rpclient.feature.common.media.MessageImageState = me.kafuuneko.rpclient.feature.common.media.MessageImageState()`。
- 只有存在名称冲突或必须消除歧义时才使用全限定名；名称冲突优先考虑语义清晰的 `import ... as ...` 别名，需要全限定名时限制在最小范围。
- 修改代码后检查新增引用，清理冗余包名前缀与未使用的导入，不引入通配符导入。

---

## 5. 注释规范概要

详细规范与丰富示例参见 **[代码注释与 KDoc 规范](./guides/comments-and-kdoc.md)**。

### 5.1 核心通用原则

- **标准中文与通俗易懂**：统一使用规范、简洁且表意清晰的中文，直接阐明核心业务逻辑与设计意图。
- **严禁使用数字序号**：文档与行内注释中**一律不使用** `1. xxx`、`2. xxx` 或 `// 1. xxx`、`// Step 1: xxx` 等数字编号；列表统一采用 `-` 无序列表，行内步骤直接自然描述。
- **行数大于 16 行的方法必须写行内注释**：标注前置校验、异步 IO、核心转换计算、持久化与安全收尾节点。
- **不写废话注释**：不写“给变量赋值”“调用方法”“返回结果”等复述代码的注释。
- **隐私与安全**：严禁在注释中包含 API Key、真实请求头、私密对话、真实本地路径或服务商账号信息。

### 5.2 类与方法 KDoc 覆盖要求

- **类级别 KDoc**：所有 `ViewModel`、`Repository`、`Manager`、`Codec`、`Builder`、`Runtime` 等核心业务类必须编写 KDoc，简述架构角色并用 `-` 列举核心职责与关键设计模式。
- **方法级别 KDoc**：所有 `public`/`protected` 方法、所有意图响应函数（`@UiIntentObserver`）及重要内部业务函数必须编写 KDoc，首句说明功能，复杂逻辑用 `-` 列举时序与边界规则，标明 `@param`、`@return`。

### 5.3 TODO 规范

TODO 必须说明后续动作和触发条件，禁止写空泛占位（如 `// TODO: 优化`）。

推荐：
```kotlin
// TODO: 增加 Claude tool use 支持后，将 Anthropic 消息后处理拆出独立 adapter。
```

---

## 6. 单元测试规范与反模式

单元测试的目的是**保障核心纯算法正确性、保护协议兼容性**。测试代码同样具有维护成本，严禁追求形式上的测试覆盖率或编写无实质业务价值的垃圾单测。

### 6.1 必须/优先编写单元测试的场景

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

### 6.2 严禁编写的无用单元测试（Anti-Patterns）

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

## 7. 命名速查

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

## 8. AI 编码 Checklist

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
10. 是否避免在普通状态、提示或非调试日志中泄露 API Key、请求头、私密对话、真实路径和完整原始响应？
11. 是否仅在引入复杂纯逻辑、关键算法分支、协议编解码兼容或 Bug 回归时补充必要单元测试，严禁堆砌无价值的无用测试？
12. 新增注释是否解释了必要约束，而不是复述代码？
13. 是否运行最小验证？

常用验证：

```powershell
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug
```

如果必须联网下载依赖，先说明原因。

---

## 9. 当前框架入口

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
