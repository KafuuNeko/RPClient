# RPClient 基础功能与 SillyTavern 差异复查报告

> 复查日期：2026-06-11  
> 对比基线：当前仓库代码与 SillyTavern 官方文档  
> 范围：单人/群聊 Prompt、总结、世界书、角色卡、Provider 适配及主要用户体验

## 1. 结论摘要

RPClient 已具备可工作的 AI 角色扮演主链路，包括角色卡、单人和群组会话、流式生成、世界书、总结、Continue/Impersonate、Prompt 后处理及多 Provider 支持。现有单元测试也覆盖了部分核心构建器。

但当前实现还不能视为与 SillyTavern 高兼容。问题不只在于功能数量较少，以下缺陷会直接改变用户数据、Prompt 内容或模型输出：

1. **编辑导入的世界书会静默丢失书级和条目级兼容字段。**
2. **群聊清空总结会把全部已有消息标记为“已总结”，导致下一轮 Prompt 丢失历史。**
3. **Prompt 和总结预算使用 `字符数 / 3` 估算并设置强制下限，中文及小上下文模型可能实际超限。**
4. **默认 Gemini 和 Claude 模型已失效或退役，新用户照默认配置会直接请求失败。**
5. **世界书 `triggers` 被当成关键词，和 SillyTavern 的“生成类型过滤”语义相反。**
6. **总结会读取本应隐藏的 `<think>` 内容，并且预算没有计入模板和已有总结。**
7. **Prompt 固定顺序、示例对话角色结构和原生 Claude/Gemini 的中途 system 处理与 SillyTavern 不一致。**

综合判断：

- 基础聊天可用性：**中等偏上**
- SillyTavern 卡片/世界书兼容性：**中等偏低**
- 长对话记忆稳定性：**中等偏低**
- Prompt 可解释性与高级可控性：**偏低**
- Provider 开箱即用性：**存在阻断问题**

## 2. 审查方法

本次复查采用以下方式：

- 静态追踪单人和群聊从消息输入、Prompt 构建、Provider 转换到消息保存的完整路径。
- 核对总结选择、生成、快照边界、编辑、删除和自动总结逻辑。
- 核对世界书 JSON 导入、数据库实体、编辑表单、触发器和 Prompt 注入。
- 对照 SillyTavern 官方的 Summarize、World Info、Prompt Manager、Reasoning、Group Chats 和 Macros 文档。
- 执行 `.\gradlew.bat testDebugUnitTest`；现有 JVM 单元测试通过。

说明：单元测试通过只能证明当前测试描述的行为未回归，不能证明这些行为与 SillyTavern 兼容。现有测试没有覆盖本报告列出的多项数据保真和跨 Provider 问题。

## 3. 已确认的严重问题

### P1-1 编辑世界书会丢失导入数据

**证据**

- `Lorebook` 保存了 `description`、`scanDepth`、`tokenBudget`、`recursiveScanning` 和 `extensionsJson`：
  `app/src/main/java/me/kafuuneko/rpclient/libs/room/entity/Lorebook.kt`
- `WorldBookEditForm` 只保留 `id`、`name` 和条目列表，`toLorebook()` 仅重建 `id/name`：
  `app/src/main/java/me/kafuuneko/rpclient/feature/worldbookedit/model/WorldBookEditForm.kt:6`
- 编辑页保存时用完整实体更新数据库：
  `app/src/main/java/me/kafuuneko/rpclient/feature/worldbookedit/WorldBookEditViewModel.kt:105`

因此，用户只要打开已导入世界书并重命名、保存或进入条目编辑，书级高级字段就会被默认值覆盖。

条目编辑也存在相同问题：

- `WorldBookEntryEditForm` 没有 `matchCreatorNotes` 和 `rawJson`。
- `matchWholeWords: Boolean?`、`caseSensitive: Boolean?` 被表单压平成非空 Boolean。
- 保存时重建 `LorebookEntry`，缺失字段回到默认值。

**影响**

- 从 SillyTavern 导入的数据无法往返保真。
- 原本启用的 Creator's Notes 匹配会被关闭。
- `null` 所表示的“沿用默认设置”会变成显式 `false`。
- 用户界面没有提示字段被删除，属于静默数据损坏。

**建议**

P1 优先修复。编辑表单必须完整 round-trip 所有实体字段；未暴露在 UI 的字段至少要从原实体复制，不能用默认值重建。

### P1-2 群聊清空总结会隐藏全部历史

**证据**

`GroupChatRepository.updateCurrentSummary()` 无论内容是否为空，都把 `coveredMessageId` 设为最新消息：

`app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/GroupChatRepository.kt:289`

后续未总结消息按“最新总结覆盖消息之后”读取：

`app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/GroupChatRepository.kt:262`

单人聊天已经对空总结做了正确处理，将边界重置为 `0L`：

`app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepository.kt:663`

**影响**

用户在群聊中删除总结文本后，下一轮 Prompt 既没有总结，也没有被边界遮蔽的旧消息。模型会表现为突然失忆。

**建议**

空总结应创建 `coveredMessageId = 0L` 的快照，或删除当前总结并回退到上一有效快照；行为应与单人聊天一致。

### P1-3 Token 预算不是硬约束

**证据**

- 单人和群聊都用 `(text.length / 3)` 估算 Token：
  `ChatPromptBuilder.kt:356`、`GroupChatPromptBuilder.kt:442`
- 即使 `contextTokens - maxTokens` 很小或为负，也强制给 Prompt 至少 1024 Token、历史至少 512 Token：
  `ChatPromptBuilder.kt:35`、`ChatPromptBuilder.kt:49`
- 历史和世界书选择逻辑允许第一条超预算内容进入，因为只有列表非空时才检查超限：
  `ChatPromptBuilder.kt:222`、`ChatPromptBuilder.kt:235`
- 固定段预算在宏展开前计算，但最终内容在宏展开后才发送。

**影响**

- 中文、日文、代码、特殊符号的估算误差明显大于普通英文。
- 用户把上下文设得小于输出预算时，应用仍构建一个更大的 Prompt。
- `{{history}}`、`{{outlet::...}}` 等宏展开后可能显著增加实际长度。
- API 可能返回 context overflow，或由中转服务在不可控位置截断。
- 自动总结无法稳定避免长上下文错误。

**建议**

引入按模型/Provider 选择的真实 tokenizer，并在宏展开、Prompt 后处理完成后做最终计数。预算不足时应明确裁剪顺序或阻止请求，不能使用会突破上下文的强制下限。

### P1-4 默认 Provider 模型已经过期

**证据**

- Gemini 默认模型为 `gemini-1.5-flash`：
  `app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/LLMRepository.kt:223`
- Claude 默认模型为 `claude-3-5-sonnet-latest`：
  `app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/LLMRepository.kt:233`
- Anthropic 官方记录显示 Claude Sonnet 3.5 已于 2025-10-28 退役。
- Google 当前模型弃用表已不再列出 Gemini 1.5，有效推荐模型已进入 Gemini 2.5/3.x 系列。

**影响**

新用户启用内置模板后很可能立即收到 model not found 或 retired model 错误，容易误以为 API Key 或网络配置有问题。

**建议**

不要把易过期模型名固化为长期默认值。可提供“拉取模型列表/手动选择”，并维护带日期和迁移提示的默认模板。

## 4. Prompt 构建问题

### P2-1 固定 Prompt 顺序和 SillyTavern 默认结构不一致

当前单人顺序为：

1. World Info Before Character
2. Main Prompt
3. World Info After Character
4. Persona Description
5. Character Description
6. Personality
7. Scenario
8. Summary
9. Auxiliary Prompt

见 `ChatPromptBuilder.kt:80`。

SillyTavern Prompt Manager 的默认概念顺序是 Main Prompt 在前，World Info Before/After 分别围绕角色定义区，并允许用户拖放、禁用、修改角色和触发类型。

**影响**

- “Before Character” 实际出现在 Main Prompt 之前。
- “After Character” 实际出现在角色描述、性格和场景之前。
- 依赖相对位置和近因效应的卡片可能出现不同输出。

**建议**

先修正默认顺序，再把 Prompt section 建模为可排序配置。至少应提供 Prompt 预览，显示最终 role、顺序、估算/真实 Token 数和来源。

### P2-2 对每个固定段单独硬截断会破坏角色设定

`ChatPromptBuilder` 将 Required 段限制为估算 1600 Token，Optional 段限制为 900 Token，并直接按字符截断：

`ChatPromptBuilder.kt:304`、`ChatPromptBuilder.kt:350`

**影响**

- 长角色描述、场景、主 Prompt 或 Post-History Instructions 会被静默切断。
- 截断可能发生在 JSON、XML、Markdown 或一句话中间。
- 用户只看到模型“不遵守设定”，看不到设定已被客户端删掉。

SillyTavern 的角色描述可以很长，主要通过整体上下文预算和 Prompt 管理器控制，而不是对每个固定节点使用固定上限。

**建议**

不要按节点固定字符数截断 Required 内容。应采用整体优先级裁剪，并在 UI 中明确提示哪些段被裁剪。

### P2-3 示例对话没有转换为 few-shot 角色消息

当前只按 `<START>` 分块，然后把标记和整个示例块都作为 `system` 消息：

`ChatPromptBuilder.kt:189`

**影响**

模型看不到真实的 user/assistant 轮次结构，示例对话对措辞、节奏和角色模仿的约束力会弱于标准 few-shot 消息。对严格区分 role 的模型影响更明显。

**建议**

解析 `{{user}}:`、`{{char}}:` 或卡片中的示例格式，生成交替的 user/assistant 消息；无法解析时再回退为文本块。

### P2-4 Claude/Gemini 的中途 system 消息会降级为 user

原生 Anthropic 和 Gemini adapter 只提取开头连续的 system 消息。后续 system 消息会变成 user role，并加上 `[System]` 文本前缀：

- `LLMHttpUtils.kt:136`
- `AnthropicMessagesLLMClient.kt:96`
- `GeminiLLMClient.kt:116`

这会影响 Author's Note、at-depth 世界书和 Post-History Instructions 的指令权重。用户可以启用 `SemiStrict` 把所有 system 前移，但这又会丢失深度和近因位置语义。

**建议**

Prompt 后处理应按 Provider/模型保存，而不是全局设置；UI 应说明每种模式会如何改变位置和 role。对不支持中途 system 的协议，应提供经过验证的 provider-specific 模板。

### P2-5 Summary 注入缺少包装和位置控制

单人聊天把 `context.summary` 作为一条未标记的 Required system 消息固定放在角色设定区：

`ChatPromptBuilder.kt:92`

SillyTavern 支持 Injection Template、注入位置和角色配置。

**影响**

模型可能无法区分“已发生剧情总结”和“角色/世界设定”，尤其当总结中包含命令式句子时，容易产生设定污染。

**建议**

默认至少包装为清晰的 memory 区块，并允许配置模板、role 和位置。

## 5. 总结功能问题

### P2-6 总结预算没有计算完整请求

当前预算只计算：

`provider.contextTokens - summaryResponseTokens`

见 `SummaryPromptBuilder.kt:84` 和 `GroupChatSummaryPromptBuilder.kt:47`。

没有扣除：

- 总结 Prompt 模板本身
- 已有总结
- 用户名、角色名等宏展开
- 消息格式和 role 开销

同时仍使用 `字符数 / 3`，并允许第一条消息超预算。

SillyTavern 文档描述的总结预算会考虑当前 Prompt、已有总结和回复余量。

**影响**

长总结越积越大时，自动总结最容易在最需要它的时候失败。

### P2-7 隐藏思维会进入长期总结

普通 Prompt 默认会通过 `stripThinkBlocks()` 移除历史中的 `<think>...</think>`：

`ChatPromptBuilder.kt:338`、`GroupChatPromptBuilder.kt:287`

但单人和群聊总结直接使用原始消息内容：

- `SummaryPromptBuilder.kt:33`
- `GroupChatSummaryPromptBuilder.kt:20`

**影响**

- 即使关闭“将思维加入上下文”，思维仍会被总结器读取。
- 推理草稿可能被误写成已发生事实或角色内心。
- 总结 Token 消耗增大。
- 一次污染会被长期保存在 summary 中。

SillyTavern 将 reasoning 作为消息的独立可折叠数据处理，而不是直接混入正文。

**建议**

消息模型应拆分 `content` 与 `reasoning`。在完成迁移前，所有总结、导出、复制和二次处理路径必须复用统一的 think 过滤策略。

### P2-8 总结缺少恢复、暂停和触发控制

当前已支持总结快照和因消息编辑/删除而失效，这是正确方向。但与 SillyTavern 相比仍缺少：

- Restore Previous
- Pause 自动总结
- Injection Template/Position
- 按字数触发
- Blocking/Non-blocking 等更新策略
- 明确的最大消息数、预算和当前边界可视化

这主要是能力差异，不全是 bug，但会影响用户纠正错误总结的体验。

### P2-9 总结生成期间存在状态竞争风险

总结请求选择了一批消息并记录边界，然后异步调用模型。生成期间如果消息被编辑、删除或继续增加，返回结果仍可能按旧边界保存。单人保存路径对“更新的 summary 是否仍是最新”有校验，但没有证明被覆盖消息仍与请求时一致。

**建议**

保存时校验消息 ID、更新时间或内容哈希；冲突时放弃结果并提示重新总结。

## 6. 世界书问题

### P2-10 `triggers` 语义实现错误

当前把 `extensions.triggers` 与主关键词合并：

`WorldBookActivator.kt:117`

但 SillyTavern 中 Triggers 表示该 Prompt/条目适用的生成类型，例如 normal、continue、impersonate、swipe、regenerate、quiet，不是额外关键词。

**影响**

- 导入的 `"normal"`、`"continue"` 等值可能在聊天文本出现时误触发。
- 本应只在特定生成模式生效的条目无法正确过滤。

**建议**

建立生成类型枚举，并在激活前按当前 generation mode 过滤；不要参与关键词匹配。

### P2-11 默认 Whole Words 行为不兼容

当前只有 `matchWholeWords == true` 才整词匹配，`null` 和 `false` 都使用子串匹配：

`WorldBookActivator.kt:167`

SillyTavern 文档中 Match Whole Words 默认启用。当前实现可能让 `king` 命中 `liking`，造成世界书过度触发。

编辑器还会把导入的 `null` 显式保存成 `false`，进一步固化差异。

### P2-12 书级 Scan Depth 和 Token Budget 没有进入运行时

`Lorebook` 保存 `scanDepth` 和 `tokenBudget`，但激活器只读取条目级 `scanDepth`，否则使用硬编码默认值 2：

`WorldBookActivator.kt:140`

Prompt Builder 仅使用全局 `worldInfoBudgetPercent`，不读取书级 `tokenBudget`。

**影响**

导入数据看似成功，实际生成行为不同；这是“保存了但不生效”的高迷惑性兼容问题。

### P2-13 Inclusion Group 只实现了子集

当前直接按完整 `group` 字符串分组：

`WorldBookActivator.kt:180`

SillyTavern 支持逗号分隔的多个 group，并支持 Use Group Scoring。实体虽保存 `useGroupScoring`，运行时没有使用。

**影响**

复杂世界书的互斥和优先选择结果不同，可能同时注入互相冲突的设定，或选中错误条目。

### P2-14 世界书预算会错误淘汰 Constant 条目

激活阶段按 `constant` 优先排序，但预算阶段重新仅按 `order` 排序：

`ChatPromptBuilder.kt:239`、`GroupChatPromptBuilder.kt:305`

SillyTavern 的预算顺序是 Constant 优先，再按较大的 Order。当前高 Order 普通条目可以挤掉 Constant 条目。

### P2-15 扫描文本和正则能力不兼容

- 扫描历史时只拼接消息正文，不包含说话者名称；SillyTavern 默认 Include Names。
- 正则只识别 `/pattern/`，不支持 `/pattern/flags`。
- 宏系统仅支持项目内定义的简单替换，缺少 SillyTavern 当前宏引擎的嵌套、变量等能力。

这些差异会让同一世界书在两个客户端触发结果不同。

### P2-16 Author's Note 位置被固定

世界书 AN Top、用户 Note 和 AN Bottom 都固定在 depth 1：

`ChatPromptBuilder.kt:129`

缺少 SillyTavern 的 Author's Note depth、frequency、position 和禁用语义。即使没有启用独立 Author's Note，AN 位置世界书仍会注入。

### P2-17 世界书状态在请求成功前持久化

Prompt 构建完成后、真正请求模型前就保存 timed effects：

- `ChatViewModel.kt:921`
- `GroupChatViewModel.kt:694`

**影响**

请求失败、取消或 Provider 拒绝时，也可能消耗 probability/sticky/cooldown 状态。用户重试相同消息时得到不同世界书结果。

**建议**

将状态作为 generation transaction 的候选结果，成功保存回复后再提交；取消和失败时回滚。

## 7. Provider 与生成体验问题

### P2-18 单人流式异常会丢失已收到文本

用户主动停止时，单人聊天会保存已收到内容；但网络或解析异常时，只要该消息是新建占位，就直接删除：

`ChatViewModel.kt:744`

群聊使用 `persistOrDeleteStreamingMessage()`，会保留非空部分内容，二者行为不一致。

**影响**

网络波动发生在长回复末尾时，用户会失去已经看到的大段文本。

**建议**

异常时也保存非空 partial response，并在消息元数据中标记 incomplete。

### P2-19 Provider 参数缺少范围与模型能力校验

`LLMProviderEditForm.toProviderOrNull()` 只检查能否解析数字，不检查：

- `contextTokens > 0`
- `maxTokens > 0`
- `maxTokens < contextTokens`
- temperature/top_p 的协议范围
- 模型是否支持对应参数

`AnthropicMessagesLLMClient` 总是发送 `temperature` 和 `top_p`。Anthropic 当前文档说明 Claude Opus 4.7 及以后在非默认值时会对这些参数返回 400。

**建议**

为不同协议提供 capability profile，只发送用户显式设置且模型支持的参数；Provider 测试应包含一次最小生成请求和可读的错误解释。

### P3-1 原生 reasoning 支持不完整

当前只对 OpenAI-compatible 响应中的 `reasoning_content` 等字段做了文本拼接。原生 Claude/Gemini 的 thinking block、thought signature 和多轮保留没有统一消息模型。

对普通非思维模型影响有限，但使用新 reasoning 模型时可能出现思维丢失、上下文不连续或不符合 Provider 要求。

### P3-2 SillyTavern Regex 扩展（已于 2026-06-13 完成）

项目已新增独立 Regex 领域层、统一执行入口和符合 MVI 规范的管理页面，现已支持：

- SillyTavern `regex_scripts` 格式、未知字段往返保留及 JSON 导入导出；
- JavaScript 风格 `/pattern/flags` 的 `g/i/m/s/u/y`、`{{match}}`、编号/命名捕获组、Trim Out 和 Find Regex 宏模式；
- User Input、AI Response、Slash Command、World Info、Reasoning、聊天显示和出站 Prompt placement；
- Source、Markdown Display、Prompt 三种执行模式，显示/Prompt 临时变换不回写数据库；
- Run on Edit、Min/Max Depth、稳定排序、启停、全局/预设/角色卡作用域；
- 角色卡内嵌脚本默认不执行，只有用户显式授权后进入单聊或群聊执行管线；
- 脚本创建、复制、删除、拖动排序、即时校验、测试，以及 Prompt Inspector 命中阶段和持久化状态展示；
- 无效脚本隔离，单条错误不会中断后续脚本或生成流程。

兼容层使用 Kotlin Regex 模拟 JavaScript RegExp；已覆盖 SillyTavern 脚本常用语义，但极少数依赖 JavaScript 引擎特有正则语法的脚本仍可能被判定为无效，并会在测试页或 Prompt Inspector 中显示错误。

## 8. 与 SillyTavern 的主要能力差异

以下多数属于产品范围差异，不应直接称为缺陷，但会影响高级用户迁移：

| 领域 | RPClient 当前状态 | SillyTavern 对照能力 | 影响 |
| --- | --- | --- | --- |
| Prompt 管理 | 固定代码顺序，少量全局模板 | Prompt Manager 可排序、开关、改 role、设触发类型 | 难以调试和复刻预设 |
| Token 观察 | 估算，无最终 Prompt itemization | Tokenizer、Prompt itemization/预览 | 用户不知道内容为何被裁剪 |
| 总结 | 自动/手动、快照、编辑 | 恢复、暂停、注入模板/位置、更多触发策略 | 错误总结不易恢复 |
| 世界书 | 支持关键词、递归、概率、部分 group/timed effects | 多来源、完整 group scoring、过滤器、向量匹配、更多激活设置 | 复杂世界书不兼容 |
| Persona | 会话用户名/描述 | Persona 管理、切换、绑定及 persona lore | 多身份 RP 管理较弱 |
| 长期记忆 | 总结 | 总结、Chat Vectorization、Data Bank/RAG | 长对话检索能力较弱 |
| 消息分支 | regenerate 替换 | swipe 候选、分支与回退 | 试回复和比较成本更高 |
| 群聊 | 基础选择策略、强制发言、自动模式 | mute、成员排序、自回复控制、场景覆盖等 | 群聊编排能力较少 |
| Regex 脚本 | 已支持全局/角色/预设脚本、作用范围、深度、临时模式和授权执行 | 全局/角色/预设脚本、作用范围、深度和临时执行模式 | 常用脚本语义已对齐；JavaScript 引擎特有语法仍可能不兼容 |
| 扩展自动化 | 无对应完整体系 | STscript、Quick Replies、扩展和函数调用 | 高级工作流迁移困难 |
| 推理内容 | `<think>` 混入正文 | reasoning 独立展示和配置 | 摘要、导出和上下文易污染 |

## 9. 已实现且方向正确的部分

为避免只列问题，以下设计值得保留：

- 单人总结使用快照和覆盖边界，而不是直接覆盖全部历史。
- 编辑或删除被总结消息后，会失效相关总结，避免继续引用已改变剧情。
- 普通 Prompt 默认过滤 `<think>`，减少推理草稿回灌。
- 世界书已实现条目级 scan depth、secondary logic、递归、概率、sticky/cooldown/delay、depth/role/outlet 等基础结构。
- 单人和群聊都优先保留最近历史。
- 支持 Prompt 后处理模式，为不同 API 协议兼容留下扩展点。
- 群聊异常中断会保留非空 partial response。
- 角色卡和世界书保留部分原始 extensions，为继续改善兼容性提供了数据基础。

问题主要在于这些机制之间尚未形成统一、可验证、可往返的数据和 Prompt 管线。

## 10. 建议修复顺序

### 第一阶段：阻止数据损坏和直接失忆

1. 修复世界书/条目编辑 round-trip，补回缺失字段。
2. 修复群聊空总结边界。
3. 更新默认 Gemini、Claude 和 OpenRouter 模型模板。
4. 为以上问题补充数据库与表单转换测试。

### 第二阶段：保证 Prompt 不超限且可解释

1. 引入真实 tokenizer 抽象。
2. 在宏展开和后处理后执行最终预算。
3. 移除会突破上下文的固定最小预算。
4. 增加 Prompt Inspector：顺序、role、来源、Token、裁剪原因。
5. 将固定段静默截断改为整体优先级裁剪和用户提示。

### 第三阶段：修复世界书兼容语义

1. 将 triggers 改为 generation type filter。
2. 正确实现默认 whole-word、Include Names 和世界书关键词 regex flags；不将其误判为已实现 Regex 扩展。
3. 接入 lorebook-level scan depth/token budget。
4. 实现逗号多 group、group scoring 和 Constant 预算优先。
5. 成功生成后再提交 timed effects 状态。

**2026-06-13 审计补充**

- 修复 sticky/cooldown 结束边界晚一轮的问题，结束轮次现在与 SillyTavern 的半开区间语义一致。
- 空回复或被 Source Regex 清空的回复不再推进世界书 timed effects；流式占位消息会删除，重新生成时保留原消息。

### 第四阶段：实现完整 Regex 脚本体系（已完成）

1. 已在 `libs/regex/` 建立独立领域层，定义 RegexScript、作用域、placement、宏替换模式和临时执行模式；执行引擎保持纯函数并按脚本顺序稳定运行。
2. 已兼容 SillyTavern 脚本格式及 JavaScript 风格 `/pattern/flags`，实现 `g/i/m/s/u/y`、`{{match}}`、编号/命名捕获组、Trim Out 和 Find Regex 宏的 Raw/Escaped/Disabled 模式。
3. 已接入 User Input、AI Response、Slash Command、World Info、Reasoning、聊天显示和出站 Prompt，并实现 Run on Edit、Min/Max Depth、仅显示、仅 Prompt 及不落盘临时执行语义。
4. 已支持全局、角色卡和预设作用域，保留稳定排序、启停与授权状态；角色卡内嵌 `extensions.regex_scripts` 默认只保留，用户授权后才执行。
5. 已按 MVI 规范新增脚本列表、编辑器和测试模式，使用 UiState/UiIntent/ViewEvent 单向数据流；支持创建、复制、删除、拖动排序、即时校验及 JSON 导入导出。
6. 单聊、群聊、编辑消息、重新生成和 Prompt Inspector 已使用同一 Regex 执行入口，并显示命中的脚本、阶段及文本是否持久化。
7. 已增加格式往返、脚本顺序、flags、捕获组、宏、深度、placement、临时模式、Prompt 管线和异常正则隔离测试，确保单条无效脚本不会阻断生成。

### 第五阶段：完善总结和 Provider

1. 总结预算计入模板、已有总结和格式开销。
2. 总结路径统一过滤 reasoning。
3. 增加恢复、暂停、模板和位置设置。
4. Provider 参数按能力发送，并保存为 per-provider 配置。
5. 单人异常中断保留 partial response。

### 第六阶段：提高与 SillyTavern 的高级兼容

1. 可排序 Prompt Manager。
2. 示例对话解析为 user/assistant few-shot。
3. 独立 reasoning 数据模型。
4. Persona、swipe/branch、RAG 和扩展自动化能力。

## 11. 建议新增的回归测试

至少新增以下测试，防止修复后再次退化：

1. 导入含全部 Lorebook 字段的 JSON，重命名后导出，除名称外语义完全一致。
2. 编辑条目后 `match_creator_notes`、nullable flags、raw extensions 不丢失。
3. 群聊清空总结后，全部普通消息重新进入未总结集合。
4. 中文长文本在真实 tokenizer 下不超过 `context - response`。
5. 宏展开后再次预算，`{{history}}` 不可绕过限制。
6. Constant 世界书条目在预算不足时优先于普通条目。
7. triggers 只过滤生成类型，不参与关键词命中。
8. 多 inclusion group 和 group scoring 与 SillyTavern 示例一致。
9. `includeThinkInContext=false` 时，总结请求不包含 `<think>`。
10. 单人流式请求收到部分 delta 后异常，部分文本仍被保存。
11. Claude/Gemini 的中途 system 经各后处理模式转换后生成预期 payload。
12. Provider 表单拒绝负数、零上下文和输出预算大于上下文。
13. 角色卡 Regex 脚本导入导出字段、顺序和作用域保持一致，未经授权的内嵌脚本不执行。
14. Regex 脚本的 placement、深度、临时模式、宏、捕获组和 Trim Out 在单聊与群聊中得到相同结果。

## 12. 参考资料

- [SillyTavern Summarize](https://docs.sillytavern.app/extensions/summarize/)
- [SillyTavern World Info](https://docs.sillytavern.app/usage/core-concepts/worldinfo/)
- [SillyTavern Prompt Manager](https://docs.sillytavern.app/usage/prompts/prompt-manager/)
- [SillyTavern Prompts](https://docs.sillytavern.app/usage/prompts/)
- [SillyTavern Reasoning](https://docs.sillytavern.app/usage/prompts/reasoning/)
- [SillyTavern Regex](https://docs.sillytavern.app/extensions/regex/)
- [SillyTavern Group Chats](https://docs.sillytavern.app/usage/core-concepts/groupchats/)
- [SillyTavern Macros](https://docs.sillytavern.app/usage/core-concepts/macros/)
- [Anthropic Model Deprecations](https://platform.claude.com/docs/en/about-claude/model-deprecations)
- [Gemini API Deprecations](https://ai.google.dev/gemini-api/docs/deprecations)

## 13. 最终判断

RPClient 的架构已经越过“简单拼接角色卡文本”的阶段，具备继续演进为成熟 RP 客户端的基础。但当前最需要解决的不是继续堆叠新功能，而是先建立三条可靠边界：

1. **导入的数据在编辑和导出后不能静默变化。**
2. **发送给模型的最终 Prompt 必须可查看、可计数、可解释。**
3. **总结、世界书状态和生成结果必须以一次成功生成作为一致的事务边界。**

完成这些修复后，再补 Prompt Manager、Persona、swipe/branch 和 RAG，用户体验与 SillyTavern 的差距会明显缩小；否则功能越多，隐式状态和兼容差异越难排查。
