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
3. 注释遵循本文档的“注释规范”。
4. 不写复述代码的注释。
5. 编写代码时主动评估是否需要补充维护性注释，尤其是兼容性、数据一致性、生命周期、Prompt 预算、协议差异和非直观取舍。
6. 普通函数建议不超过 80 行，嵌套不超过 4 层。
7. 优先 guard clause，少写层层嵌套。
8. 不做无关重构，不改动与需求无关的框架代码。
9. 不引入未经需求确认的大型框架或架构重写。

---

## 5. 注释规范

注释用于解释代码背后的意图、约束、风险和非显而易见的取舍，不用于复述代码已经清楚表达的内容。

### 5.1 基础规则

1. 注释使用中文。
2. 公共类、跨模块能力、Repository、Codec、Builder、Runtime、复杂 UseCase 需要写 KDoc。
3. 私有函数通常不强制写 KDoc，只有存在兼容性约束、生命周期约束、复杂算法或非直观副作用时才补充说明。
4. 不写“给变量赋值”“调用方法”“返回结果”这类复述代码的注释。
5. 注释必须随着代码行为更新，发现过期注释时应一并修正。
6. 注释中不得包含 API Key、真实请求头、私密对话、真实角色资源、真实本地路径或服务商账号信息。

### 5.2 应该注释的内容

以下情况应优先写注释：

1. 兼容边界：Character Card V1/V2、SillyTavern 世界书、Regex 脚本、协议字段映射。
2. Prompt 构建：宏展开、消息排序、角色/世界书注入、预算裁剪、协议后处理。
3. LLM 请求：不同 Provider 协议差异、流式解析、停止生成、错误脱敏。
4. 数据一致性：会话和消息事务、分支复制、群聊成员和摘要、导入失败回滚。
5. Android 生命周期：Activity Result、文件选择器、复制到剪贴板、系统分享。
6. 并发与异步：流式生成、摘要生成、批量导入导出、不可重入操作。
7. UI 状态：复杂 UiState 树、一次性 ViewEvent、生成中禁用操作、危险操作确认。
8. 与直觉相反的实现：为了兼容、隐私或性能而做出的非明显取舍。

### 5.3 KDoc 规范

KDoc 应说明职责和边界，必要时说明调用约束。

```kotlin
/**
 * 将角色、会话历史、世界书和 Prompt 预设组合为一次聊天生成请求。
 *
 * 该类只负责构建请求草稿和检查信息，不直接发起网络请求，也不持久化聊天消息。
 */
class ChatPromptBuilder
```

函数 KDoc 只在函数跨模块暴露或约束复杂时编写：

```kotlin
/**
 * 重新生成世界书激活后的时序状态。
 *
 * 只能使用最终保留进 Prompt 的条目更新 sticky/cooldown，避免被预算裁剪的条目影响后续轮次。
 */
fun rebuildTimelineAfterBudget(...)
```

### 5.4 行内注释规范

行内注释应少用，只用于解释局部复杂逻辑。

推荐：

```kotlin
// 先写入用户消息，再启动流式生成，确保停止生成后仍能保留用户已提交内容。
```

不推荐：

```kotlin
// 设置 loading 为 true
state = state.copy(loading = true)
```

### 5.5 TODO 规范

TODO 必须说明后续动作和触发条件，不写空泛占位。

推荐：

```kotlin
// TODO: 增加 Claude tool use 支持后，将 Anthropic 消息后处理拆出独立 adapter。
```

不推荐：

```kotlin
// TODO: 优化
```

---

## 6. 命名速查

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

## 7. AI 编码 Checklist

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
10. 是否避免泄露 API Key、请求头、私密对话、真实路径和完整原始响应？
11. 涉及 Prompt、世界书、Regex 或 Provider 时是否补充对应单元测试？
12. 新增注释是否解释了必要约束，而不是复述代码？
13. 是否运行最小验证？

常用验证：

```powershell
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug
```

如果必须联网下载依赖，先说明原因。

---

## 8. 当前框架入口

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
