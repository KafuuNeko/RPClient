# 项目结构与分层规范

本文档说明 RPClient 的目录结构、分层职责和通用命名规则。新增功能前先确认应放在哪一层。

---

## 1. 标准 Feature 结构

每个页面或功能建立独立 feature，不把业务堆进 `main`。当前已有 `chat`、`groupchat`、`characterlist`、`characteredit`、`worldbooklist`、`worldbookedit`、`llmprovider*`、`promptpreset`、`regexscript`、`requestlog` 等 feature。

```text
feature/<name>/
├── <Feature>Activity.kt
├── <Feature>ViewModel.kt
├── presentation/
│   ├── <Feature>UiState.kt
│   ├── <Feature>UiIntent.kt
│   └── <Feature>ViewEvent.kt      # 按需
├── model/                         # 当前 feature 专用 UI/业务模型，按需
└── ui/
    └── <Feature>Layout.kt
```

简单展示页可以没有 ViewModel：

```text
feature/about/
├── AboutActivity.kt
└── ui/
    └── AboutLayout.kt
```

---

## 2. 全局目录职责

```text
libs/core/       # MVI 与 Activity 基类，非必要不改
libs/room/       # AppDatabase、Entity、DAO、Repository
libs/character/  # Character Card 导入导出与映射
libs/groupchat/  # 群聊 Prompt、成员选择、摘要和输出清理
libs/llm/        # LLM Provider、请求模型、客户端适配
libs/prompt/     # Prompt 构建、宏、世界书激活、预算和检查
libs/regex/      # SillyTavern Regex 脚本解析、存储与运行
libs/utils/      # 通用工具
ui/theme/        # AppTheme、颜色、字体
ui/widgets/      # 通用组件
ui/message/      # Markdown 与消息内容渲染
```

---

## 3. 分层职责

| 层级 | 负责 | 不负责 |
|------|------|--------|
| Compose `ui/` | 按 `UiState` 渲染、发送 `UiIntent` | 业务判断、IO、Room、网络请求、文件读写 |
| Activity | 创建 ViewModel、收集状态、处理 ViewEvent | 业务计算、数据转换、直接读写偏好或数据库 |
| ViewModel | 处理 Intent、调用数据层、更新 State/Event | 持有 View/Activity、直接操作 Compose 状态、直接访问 DAO |
| Repository | Room、文件记录、导入导出事务、业务聚合 | 持有页面状态、构造复杂 UI |
| Codec/Builder/Runtime | 角色卡/Regex 编解码、Prompt 构建、脚本执行 | 直接更新页面状态或发 Toast |
| AppModel | 小体量全局偏好和默认 Prompt 配置 | 角色、会话、世界书、请求日志等主体数据 |

---

## 4. 命名规则

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
| 私有成员 | `m` 前缀，如 `mChatRepository` |

---

## 5. 代码风格

1. 4 空格缩进。
2. import 不使用通配符。
3. 注释使用中文，公共类和跨模块能力写 KDoc。
4. 不写复述代码的注释。
5. 普通函数建议不超过 80 行，嵌套不超过 4 层。
6. 优先 guard clause，少写层层嵌套。
7. 不做无关重构，不改动与需求无关的框架代码。
