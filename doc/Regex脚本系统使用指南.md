# RPClient Regex 脚本系统使用指南

本文档面向 Regex 脚本的使用者与维护者，说明它能解决什么问题、应当如何配置，以及怎样确认脚本在测试页和真实聊天中都按预期工作。

## 1. Regex 脚本是什么

Regex 脚本是一组按顺序执行的“查找并替换”规则。系统使用正则表达式查找文本，再用替换字符串生成新文本。

它可以在三个互不相同的阶段工作：

| 模式 | 修改对象 | 是否持久保存 | 典型用途 |
| --- | --- | --- | --- |
| Source | 消息原文 | 是 | 清理输入、规范模型回复、移除不需要的标签 |
| Markdown | 页面显示文本 | 否 | 隐藏内容、改善排版、只改变阅读效果 |
| Prompt | 发给模型的文本 | 否 | 调整历史消息、世界书内容或模型上下文 |

最重要的区别是：

- Source 会真正修改并保存消息，重新进入聊天后仍然有效。
- Markdown 只改变界面显示，数据库中的原文不变。
- Prompt 只改变发给模型的内容，聊天记录和界面原文不变。

## 2. 适合与不适合的场景

### 2.1 适合使用

- 把用户输入中的固定格式统一成目标格式。
- 删除模型回复中的固定前缀、后缀、标签或舞台说明。
- 使用捕获组重新排列日期、编号、姓名等结构化文本。
- 只在显示时隐藏或替换敏感片段，不修改原始消息。
- 只在请求模型时补充、删除或规范某些文本。
- 为全局、特定预设或特定角色配置不同规则。
- 处理 `/me` 一类以 `/` 开头的命令文本。
- 分别处理 AI 正文与 `<think>...</think>` 推理内容。
- 导入并兼容 SillyTavern Regex 脚本。

### 2.2 不适合使用

- 需要记忆状态、访问网络、读取数据库或执行任意代码的逻辑。
- 依赖自然语言语义判断的复杂改写。
- 必须提供安全保证的隐私脱敏。正则可能漏掉未覆盖的格式。
- 需要永久修改角色卡或世界书数据的操作。Prompt 模式只修改本次请求。
- 结构复杂、嵌套层级不确定的 XML、HTML 或 JSON 解析。

Regex 是文本变换工具，不是脚本语言。复杂业务逻辑应当在对应功能模块中实现。

## 3. 从哪里进入

1. 打开 RPClient 主页面。
2. 进入设置区域。
3. 点击“Regex Scripts”。
4. 先选择脚本范围，再创建或编辑脚本。

管理页支持新建、复制、删除、启用、禁用、排序、导入、导出和测试。

## 4. 配置时先决定三件事

一条脚本是否执行，主要由“范围、位置、模式”共同决定。

### 4.1 范围 Scope

| 范围 | 含义 | 实际聊天中的生效条件 |
| --- | --- | --- |
| Global | 全局脚本 | 始终参与执行 |
| Preset | 当前预设脚本 | 必须开启“允许脚本执行” |
| Character | 角色脚本 | 选择角色，并为该角色开启“允许脚本执行” |

角色范围的脚本保存在角色卡扩展字段中。在群聊中，系统会收集群内各角色已授权的脚本。

授权信息与脚本内容分开保存。导入角色卡并不等于自动授权执行，这可以避免外部角色卡中的脚本未经确认直接运行。

### 4.2 位置 Placement

| 位置 | 数值 | 处理内容 |
| --- | ---: | --- |
| Markdown Display | 0 | SillyTavern 旧版兼容位置；新脚本通常使用具体消息位置配合 Display only |
| User Input | 1 | 用户消息 |
| AI Response | 2 | AI 回复正文，不含 `<think>` 内部内容 |
| Slash Command | 3 | 以 `/` 开头的用户输入 |
| World Info | 5 | 被激活并准备写入 Prompt 的世界书内容 |
| Reasoning | 6 | AI 回复中 `<think>...</think>` 标签内部的推理内容 |

一条脚本可以选择多个位置。

Slash Command 只会在用户输入以 `/` 开头时执行。在真实聊天中，Slash Command Source 脚本先执行，之后文本还会继续经过 User Input Source 脚本。

### 4.3 模式 Mode

编辑页通过两个开关决定模式：

| 配置 | 实际模式 |
| --- | --- |
| Display only 关闭，Prompt only 关闭 | Source |
| Display only 开启，Prompt only 关闭 | Markdown |
| Display only 关闭，Prompt only 开启 | Prompt |

界面会避免同时开启 Display only 和 Prompt only。如果从外部 JSON 导入了两个字段都为 `true` 的异常脚本，该脚本不会在任何模式执行。

## 5. 脚本字段说明

| 字段 | 说明 |
| --- | --- |
| Script name | 脚本名称，用于识别执行记录和错误 |
| Find Regex | 查找表达式，可填写 `/pattern/flags` 或不带分隔符的普通表达式 |
| Replace string | 替换内容，可使用捕获组和宏 |
| Trim Out | 每行一个字符串，从捕获组引用的值中删除 |
| Placement | 脚本允许处理的位置，至少选择一个 |
| Enabled | 关闭后脚本不会执行 |
| Display only | 只修改页面显示 |
| Prompt only | 只修改发给模型的 Prompt |
| Run on edit | 编辑已有消息时也执行该脚本 |
| Find Regex macro mode | 是否在查找表达式中展开宏，以及如何展开 |
| Min depth | 允许执行的最小历史深度 |
| Max depth | 允许执行的最大历史深度 |

脚本按以下顺序运行：

1. Global。
2. Preset。
3. Character。
4. 同一范围内按照管理页中的脚本顺序。

后一条脚本接收前一条脚本的输出，因此调整顺序可能改变最终结果。

## 6. 正则与替换语法

### 6.1 支持的 flags

| Flag | 含义 |
| --- | --- |
| `g` | 替换全部匹配；没有 `g` 时只替换第一个匹配 |
| `i` | 忽略大小写 |
| `m` | 多行模式，使 `^` 和 `$` 按行工作 |
| `s` | 让 `.` 可以匹配换行符 |
| `u` | Unicode 兼容标记 |
| `y` | Sticky 模式，从当前位置连续匹配，遇到间断后停止 |

未知 flag 或重复 flag 会被报告为错误。

### 6.2 捕获组

```text
Find Regex: /(\d{4})-(\d{2})-(\d{2})/g
Replace string: $1/$2/$3
Input: 2026-06-13
Output: 2026/06/13
```

支持：

- `$1`、`$2` 等编号捕获组。
- `$<name>` 命名捕获组。
- `{{match}}` 完整匹配内容。

### 6.3 Trim Out

Trim Out 会从替换字符串所引用的捕获组值中删除指定字符串，而不是直接清理整段最终输出。

```text
Find Regex: /(foo)/
Replace string: $1
Trim Out: o
Input: foo
Output: f
```

### 6.4 支持的宏

| 宏 | 含义 |
| --- | --- |
| `{{user}}` | 当前用户名 |
| `{{char}}` | 当前角色名 |
| `{{persona}}` | 当前用户 Persona |
| `{{scenario}}` | 当前场景 |
| `{{group}}` | 当前群组名 |
| `{{charIfNotGroup}}` | 非群聊时的角色名 |
| `{{match}}` | 当前正则完整匹配，仅用于替换字符串 |

宏名不区分大小写。未知宏会保留原文，不会被删除。

管理页测试使用固定测试值：

- `{{user}}` 为 `User`。
- `{{char}}` 为 `Character`。
- 其他上下文宏通常为空。

真实聊天会使用当前会话中的实际值。

### 6.5 Find Regex 宏模式

| 模式 | 行为 | 建议 |
| --- | --- | --- |
| Disabled | 不展开查找表达式中的宏 | 查找内容不依赖会话信息时使用 |
| Raw | 把宏值直接当作正则语法 | 明确希望宏值参与正则表达式时使用 |
| Escaped | 转义宏值中的正则特殊字符，按普通文本匹配 | 匹配角色名、用户名时优先使用 |

例如角色名是 `A+B`：

- Raw 中的 `+` 会被解释为正则量词。
- Escaped 会匹配字面文本 `A+B`。

除非明确需要宏值成为正则语法，否则优先选择 Escaped。

## 7. 常用配置示例

### 7.1 统一用户输入

把所有 `foo` 统一替换为 `bar`：

```text
Find Regex: /foo/gi
Replace string: bar
Placement: User Input
Mode: Source
```

测试输入 `Foo foo`，预期输出为 `bar bar`。由于是 Source，真实聊天中保存的用户消息也会变成该结果。

### 7.2 清理 AI 回复中的固定前缀

```text
Find Regex: /^Assistant:\s*/i
Replace string:
Placement: AI Response
Mode: Source
```

这会永久移除 AI 正文开头的 `Assistant:`。

### 7.3 只隐藏推理内容中的词语

```text
Find Regex: /secret/gi
Replace string: ***
Placement: Reasoning
Mode: Markdown
```

测试时选择 Reasoning 和 Markdown，输入 `secret`，预期输出为 `***`。

真实聊天中的 `<think>secret</think>` 会在显示时变成 `<think>***</think>`，但保存的原始内容不变。

### 7.4 创建简单 Slash Command

```text
Find Regex: /^\/me\s+/
Replace string: *{{user}}：
Placement: Slash Command
Mode: Source
```

测试输入 `/me waves`，测试环境中的预期输出为 `*User：waves`。

### 7.5 只整理发给模型的世界书内容

```text
Find Regex: /\s{3,}/g
Replace string: 一个空格
Placement: World Info
Mode: Prompt
```

这里的“一个空格”应实际输入一个空格，而不是输入这四个汉字。该规则只处理已激活并准备写入 Prompt 的世界书内容，不修改世界书数据库。

### 7.6 按角色名匹配

```text
Find Regex: /{{char}}/g
Replace string: {{user}}的搭档
Find Regex macro mode: Escaped
Placement: AI Response
Mode: Source
```

测试页输入 `Character`，预期输出为 `User的搭档`。真实聊天会替换为实际用户名和角色名。

## 8. 如何使用管理页测试

1. 创建或编辑脚本。
2. 保存脚本。测试页运行的是当前范围内已保存的脚本，不是尚未保存的编辑草稿。
3. 确认当前选中的 Global、Preset 或 Character 范围正确。
4. 在测试区选择 Placement。
5. 选择 Source、Markdown 或 Prompt 模式。
6. 输入测试文本。
7. 点击“Run test”。
8. 检查输出、执行记录和错误。

测试时必须选择与脚本配置一致的位置和模式。例如 Display only 脚本只能在 Markdown 测试模式运行，在 Source 模式中没有变化是正常现象。

测试页会按当前范围中的脚本顺序执行全部已启用脚本。若只想验证一条规则，可以暂时禁用同范围内的其他脚本。

### 8.1 一个脚本报错会发生什么

单条脚本失败不会中断整个执行链。系统会：

1. 保留该脚本执行前的文本。
2. 记录脚本名称和错误。
3. 继续执行后续脚本。

因此输出看起来正常时，也要检查错误列表。

### 8.2 测试页与真实聊天的差异

测试页用于验证当前范围内脚本的文本变换，不要求 Preset 或 Character 已授权。

真实聊天还需要满足：

- Preset 或 Character 范围已开启“允许脚本执行”。
- 当前会话使用了对应预设或角色。
- Placement 与真实文本类型一致。
- 模式与实际执行阶段一致。

所以“测试页通过但聊天中没有变化”通常是范围、授权、位置或模式配置问题。

## 9. 真实聊天验收方法

建议为每种模式分别创建一条非常明显的临时脚本，再按下表验收。

| 验收项 | 操作 | 正常结果 |
| --- | --- | --- |
| Source | 发送能匹配的消息，退出并重新进入聊天 | 消息仍是替换后的内容 |
| Markdown | 查看能匹配的历史消息 | 页面显示被替换，但原始消息未被永久改写 |
| Prompt | 生成一次回复后打开 Prompt 检查器 | 最终消息或 Regex 执行记录中出现替换结果 |
| Disabled | 关闭脚本后重复相同输入 | 文本不发生变化 |
| Run on edit | 关闭该选项后编辑已有消息 | 编辑流程不执行该脚本 |
| Character authorization | 关闭角色脚本授权后聊天 | 测试页仍可测试，但真实聊天不执行 |

聊天页顶部提供 Prompt 检查器入口。至少生成一次回复后按钮才可用。Prompt 检查器会显示：

- Regex 执行记录。
- Regex 错误。
- 宏展开、后处理和预算裁剪后的最终消息顺序。

这是验证 Prompt only 脚本是否真正进入模型请求的首选方法。

## 10. 深度过滤

历史深度通常从最新消息开始计算：

- 深度 `0` 表示最新消息。
- 数值越大，表示消息越旧。

Min depth 和 Max depth 用于限制脚本处理的历史区间。例如：

```text
Min depth: 1
Max depth: 3
```

表示只处理深度 1 到 3 的消息。

深度限制只有在执行上下文提供了 depth 时才生效，主要用于 Prompt 历史和历史消息显示。即时用户输入与刚生成回复的 Source 处理不提供历史深度，不应依赖深度字段限制当前 Source 消息。

负数或空值具有兼容语义。普通用户建议留空，只有明确理解历史深度时再配置。

## 11. 常见问题排查

### 11.1 点击测试后没有变化

依次检查：

1. 编辑内容是否已经保存。
2. 脚本是否启用。
3. 测试 Placement 是否被脚本选中。
4. 测试模式是否与 Source、Display only、Prompt only 配置一致。
5. 正则是否真的匹配输入。
6. 是否忘记添加 `g`，导致只替换第一个匹配。
7. 前面的脚本是否已经改变了后续脚本要匹配的文本。

### 11.2 测试页正常，聊天中不执行

检查：

- Preset 或 Character 是否已授权。
- 当前会话是否使用对应角色。
- 用户输入是否真的以 `/` 开头。
- 脚本是否选错 AI Response、Reasoning 或 World Info。
- 是否把 Display only 或 Prompt only 当成了 Source。
- 编辑已有消息时是否开启 Run on edit。

### 11.3 正则报错

常见原因：

- 括号、方括号或转义符不完整。
- 使用了不支持的 flag。
- 重复填写 flag，例如 `/foo/gg`。
- 查找宏使用 Raw 后，宏值包含未转义的正则特殊字符。

优先把 Find Regex macro mode 改为 Escaped，再用最小输入复现。

### 11.4 应用变卡

Markdown 脚本可能在流式输出期间频繁运行。应避免：

- `(.*)+`、`(.+)+` 一类容易产生灾难性回溯的结构。
- 在长文本上使用没有边界的复杂 `.*`。
- 为简单文本替换编写过度复杂的嵌套分组。

先用较长文本在测试页验证耗时，再用于流式 AI Response 或 Reasoning。

## 12. 导入与导出

系统支持 SillyTavern 风格的 JSON。最小示例：

```json
[
  {
    "id": "replace-foo",
    "scriptName": "替换 foo",
    "findRegex": "/foo/gi",
    "replaceString": "bar",
    "trimStrings": [],
    "placement": [1],
    "disabled": false,
    "markdownOnly": false,
    "promptOnly": false,
    "runOnEdit": false,
    "substituteRegex": 0,
    "minDepth": null,
    "maxDepth": null
  }
]
```

兼容规则：

- 导入时会为缺失或重复的 ID 生成可用 ID。
- 未被 RPClient 识别的扩展字段会尽量保留，便于再次导出。
- 角色脚本会存入角色卡的 `extensions.regex_scripts`。
- 导入脚本后仍需自行确认 Preset 或 Character 的执行授权。

在批量修改或导入来源不明的脚本前，建议先导出当前脚本备份，并逐条检查 Source 脚本，因为 Source 会永久修改消息。

## 13. 开发者自动化测试

执行 Regex 引擎单元测试：

```bash
sh gradlew app:testDebugUnitTest \
  --tests me.kafuuneko.rpclient.libs.regex.RegexScriptEngineTest
```

执行全部 Debug 单元测试：

```bash
sh gradlew app:testDebugUnitTest
```

功能变更至少应覆盖：

- 有 `g` 与没有 `g` 的替换行为。
- 编号捕获组、命名捕获组和 `{{match}}`。
- Raw、Escaped、Disabled 三种查找宏模式。
- Source、Markdown、Prompt 模式过滤。
- Placement、深度、编辑状态和禁用状态过滤。
- 错误隔离与后续脚本继续执行。
- AI Response 与 Reasoning 的 `<think>` 分段。
- Prompt 检查器中的执行记录和错误记录。

## 14. 推荐的最小验证流程

每次新增或导入脚本后，按以下顺序验证：

1. 在管理页只启用待测脚本。
2. 用最小输入验证单次匹配。
3. 用包含多个匹配的输入验证 `g`。
4. 用不应匹配的输入验证不会误伤。
5. 用较长文本检查性能。
6. 保存并确认范围授权。
7. 在真实单聊或群聊中验证。
8. Prompt only 脚本通过 Prompt 检查器确认。
9. Source 脚本退出聊天后重新进入，确认持久化结果。

只要测试页结果、真实聊天结果和对应模式的持久化语义都符合预期，就可以认为脚本工作正常。
