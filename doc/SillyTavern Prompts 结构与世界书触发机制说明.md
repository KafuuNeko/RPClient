# SillyTavern Prompts 结构与世界书触发机制说明

> 说明：本文基于前面对 SillyTavern Prompt Manager、Chat Completion 消息结构、World Info / 世界书触发机制的讨论整理而成。不同 SillyTavern 版本、API 后端、preset、扩展和用户配置会影响最终提交结构，因此本文以 OpenAI-compatible / Chat Completion 路径下的常见机制为主。

---

## 目录

1. [核心结论](#核心结论)
2. [SillyTavern 最终提交的消息结构](#sillytavern-最终提交的消息结构)
3. [Prompt 构建的整体流程](#prompt-构建的整体流程)
4. [Prompt Manager 的结构节点](#prompt-manager-的结构节点)
5. [各 Prompt 节点详细说明](#各-prompt-节点详细说明)
6. [Prompt 排序策略](#prompt-排序策略)
7. [Relative 与 In-Chat 注入机制](#relative-与-in-chat-注入机制)
8. [World Info / 世界书的基本概念](#world-info--世界书的基本概念)
9. [世界书来源与启用优先级](#世界书来源与启用优先级)
10. [世界书 Entry 的触发流程](#世界书-entry-的触发流程)
11. [Key、Tag、Filter 的区别](#keytagfilter-的区别)
12. [世界书扫描范围](#世界书扫描范围)
13. [Summary、Author's Note、Persona 中出现 Key 的情况](#summaryauthors-notepersona-中出现-key-的情况)
14. [世界书插入位置与排序](#世界书插入位置与排序)
15. [预算与裁剪策略](#预算与裁剪策略)
16. [Prompt Post-Processing](#prompt-post-processing)
17. [推荐配置模板](#推荐配置模板)
18. [排错清单](#排错清单)
19. [术语表](#术语表)

---

## 核心结论

SillyTavern 在 Chat Completion 模式下通常不是简单拼接一个纯文本 prompt，而是通过 Prompt Manager、角色卡、世界书、聊天历史、扩展注入内容等，构造一个面向 Chat Completion API 的 `messages[]` 结构。

世界书触发机制也不是“扫描最终提交给模型的所有 messages”。更准确地说：

```text
SillyTavern 会从已启用的世界书中收集 entry，
用 entry 的 key / keys 去扫描有限的文本缓冲区，
命中并通过过滤、预算、优先级等规则后，
再把 entry.content 插入 prompt。
```

最重要的几个判断：

```text
1. Prompt Manager 决定 prompt 节点是否启用、以什么顺序进入上下文。
2. 世界书 entry 主要靠 key 触发，不是靠 tag 触发。
3. 世界书默认主要扫描最近 N 条聊天消息，N 由 Scan Depth 控制。
4. 当前用户刚发送的消息通常会进入扫描范围。
5. Author's Note 通常可能参与世界书扫描。
6. Summary、Persona、用户 note 是否参与触发，取决于其实现和配置。
7. 世界书不会默认扫描最终 prompt 中的所有 system/user/assistant message。
8. Post-History Instructions 通常靠后，因此对输出有较强影响。
9. API 兼容性设置可能会改变最终 messages 的 role 和结构。
```

---

## SillyTavern 最终提交的消息结构

在 OpenAI-compatible / Chat Completion 模式下，最终请求通常包含类似这样的结构：

```json
{
  "model": "model-name",
  "messages": [
    {
      "role": "system",
      "content": "Main Prompt..."
    },
    {
      "role": "system",
      "content": "World Info before..."
    },
    {
      "role": "system",
      "content": "Character Description..."
    },
    {
      "role": "system",
      "content": "Character Personality..."
    },
    {
      "role": "system",
      "content": "Scenario..."
    },
    {
      "role": "system",
      "content": "Auxiliary Prompt..."
    },
    {
      "role": "system",
      "content": "World Info after..."
    },
    {
      "role": "user",
      "content": "Example or historical user message..."
    },
    {
      "role": "assistant",
      "content": "Example or historical assistant message..."
    },
    {
      "role": "user",
      "content": "Current user message..."
    },
    {
      "role": "system",
      "content": "Post-History Instructions..."
    }
  ],
  "max_tokens": 300,
  "temperature": 1
}
```

需要注意：

```text
Prompt Manager 中看到的结构 ≠ 最终 HTTP 请求中一定完全相同的结构。
```

原因是 SillyTavern 可能根据后端 API 要求进行 post-processing，例如：

```text
- 合并连续相同 role 的 messages
- 只保留一个 system message
- 强制 user / assistant 交替
- 把所有内容压成一个 user message
```

---

## Prompt 构建的整体流程

可以把 SillyTavern 的 prompt 构建理解为以下阶段：

```text
1. 读取当前角色、用户 persona、会话、preset、扩展设置
2. 收集 Prompt Manager 中启用的 prompt 节点
3. 按 prompt_order 和注入位置组织基础结构
4. 收集参与本轮扫描的世界书来源
5. 构造 World Info 扫描文本缓冲区
6. 根据 key / filter / budget / order 激活世界书 entry
7. 把激活的世界书 entry 插入指定位置
8. 加入 example messages、chat history、当前用户消息
9. 应用上下文预算裁剪
10. 根据 API 后端做 post-processing
11. 发送给模型
```

概念伪代码：

```ts
const promptNodes = collectEnabledPromptNodes(promptManager);

let context = buildBasePromptStructure(promptNodes, {
  characterCard,
  persona,
  scenario,
  examples,
  chatHistory,
  currentUserMessage,
});

const activeWorldBooks = collectActiveWorldBooks({
  globalLorebooks,
  characterLorebook,
  personaLorebook,
  chatLorebook,
});

const scanBuffer = buildWorldInfoScanBuffer({
  recentChatMessages,
  scanDepth,
  authorsNote,
  recursedEntryContent,
  includeNames,
});

const activatedEntries = activateWorldInfoEntries(activeWorldBooks, scanBuffer, {
  optionalFilters,
  generationType,
  characterFilters,
  probability,
  recursion,
  budget,
});

context = insertWorldInfoEntries(context, activatedEntries);
context = fitIntoTokenBudget(context);
context = postProcessForApiCompatibility(context);

sendToModel(context);
```

---

## Prompt Manager 的结构节点

常见默认 Prompt Manager 节点如下：

| 节点 | 常见 role | 是否默认启用 | 主要作用 |
|---|---:|---:|---|
| Main Prompt | system | 是 | 定义总体任务、角色扮演模式、回复目标 |
| World Info before | system marker | 是 | 插入角色定义之前的世界书内容 |
| Persona Description | system marker | 视 preset | 插入用户 persona 描述 |
| Character Description | system marker | 是 | 插入角色卡 description |
| Character Personality | system marker | 是 | 插入角色 personality |
| Scenario | system marker | 是 | 插入当前场景设定 |
| Enhance Definitions | system | 通常否 | 额外强化角色定义 |
| Auxiliary Prompt | system | 是/视 preset | 辅助行为约束，旧名常与 nsfw prompt 相关 |
| World Info after | system marker | 是 | 插入角色定义之后的世界书内容 |
| Chat Examples | marker | 是 | 插入 example dialogue |
| Chat History | marker | 是 | 插入实际聊天历史和当前用户消息 |
| Post-History Instructions | system | 是 | 历史之后的最终指令，旧名常与 jailbreak prompt 相关 |

其中 `marker` 类节点本身通常不是最终发给模型的实际文本，而是一个插槽，用来把某类内容插入到对应位置。

---

## 各 Prompt 节点详细说明

### 1. Main Prompt

**位置：** 通常在最前面。  
**role：** 通常是 `system`。  
**作用：** 定义模型当前应该执行的总任务。

典型内容包括：

```text
- 这是一个角色扮演聊天
- 模型应该扮演 {{char}}
- 模型应该回复 {{char}} 的下一条消息
- 模型不应该替 {{user}} 说话
- 模型应维持叙事风格和角色一致性
```

Main Prompt 的特点：

```text
优点：稳定提供全局行为框架。
缺点：离最终输出较远，容易被后面的历史、Author's Note、Post-History Instructions 覆盖或稀释。
```

适合放：

```text
- 长期通用行为规则
- 回复视角规则
- 基础 RP 模式说明
- 不随场景变化的格式要求
```

不适合放：

```text
- 当前场景临时状态
- 本轮必须遵守的短期指令
- 大量世界观资料
```

---

### 2. World Info before

**位置：** 角色定义之前。  
**role：** 通常作为 system 插槽。  
**作用：** 插入被触发的世界书内容，尤其适合世界观基础信息。

适合放在 before 的内容：

```text
- 世界基础规则
- 大陆、时代、势力背景
- 魔法/科技/社会制度
- 会影响角色定义理解的背景知识
```

策略：

```text
长期背景 → before
当前强约束 → 不建议 before，建议靠后或 Author's Note / Depth 注入
```

---

### 3. Persona Description

**位置：** 通常在角色定义附近。  
**role：** 通常是 `system` 或 marker 展开为 system 内容。  
**作用：** 描述用户 persona，即 `{{user}}` 在设定中的身份。

适合内容：

```text
- 用户名字、身份、种族、职业
- 用户与角色的长期关系
- 用户的外貌、背景、能力
- 用户在世界观中的固定地位
```

注意：

```text
Persona Description 被插入最终 prompt，
不等于其中出现的世界书 key 一定会触发世界书。
```

如果某些 persona 内容必须稳定影响输出，可以考虑：

```text
- 放入 Persona Lorebook
- 放入 Constant 世界书 entry
- 放入 Chat Lorebook 当前状态
- 放入 Author's Note 作为当前提醒
```

---

### 4. Character Description

**位置：** 角色定义区。  
**role：** 通常是 `system`。  
**作用：** 插入角色卡 description。

适合内容：

```text
- 角色身份
- 角色背景
- 角色外貌
- 角色能力
- 角色关系
- 角色核心矛盾
```

特点：

```text
Character Description 是角色长期设定的主干。
它应该写稳定事实，不适合写大量当前剧情状态。
```

建议：

```text
角色不会变化的设定 → Character Description
会随剧情变化的状态 → Chat Lorebook / Author's Note / Summary
复杂世界百科 → World Info
```

---

### 5. Character Personality

**位置：** 角色定义区。  
**role：** 通常是 `system`。  
**作用：** 描述角色性格、说话方式、行为模式。

适合内容：

```text
- 性格特征
- 情绪倾向
- 价值观
- 对 {{user}} 的态度
- 语言风格
- 决策偏好
```

示例：

```text
{{char}} is cautious, formal, and observant. She rarely reveals emotion directly, but she becomes protective when {{user}} is threatened.
```

注意：

```text
Personality 不适合塞世界观细节。
过长的 personality 会挤占聊天历史预算。
```

---

### 6. Scenario

**位置：** 角色定义区之后或附近。  
**role：** 通常是 `system`。  
**作用：** 描述初始场景或当前基础场景。

适合内容：

```text
- 开场地点
- 当前局势
- 角色与用户正在做什么
- 初始冲突
- 故事时间点
```

Scenario 和 Author's Note 的区别：

```text
Scenario：偏长期/开场/基础场景。
Author's Note：偏短期/当前/强提醒。
```

如果剧情已经推进很远，Scenario 可能变得陈旧。此时应使用：

```text
- Summary 记录过去
- Chat Lorebook 记录当前状态
- Author's Note 放当前关键提醒
```

---

### 7. Enhance Definitions

**位置：** 通常在角色定义附近。  
**role：** 通常是 `system`。  
**作用：** 强化角色定义、补充解释角色卡的遵循方式。

常见用途：

```text
- 强调角色一致性
- 强调不要脱离角色
- 强调不要替用户行动
- 强调使用角色卡中的信息
```

建议：

```text
除非模型明显忽视角色卡，否则不一定需要开启。
开启后应保持简短，避免与 Main Prompt 重复。
```

---

### 8. Auxiliary Prompt

**位置：** 通常在角色定义和 World Info after 附近。  
**role：** 通常是 `system`。  
**作用：** 提供辅助规则或风格约束。

常见用途：

```text
- 文风约束
- 内容边界
- 回复长度偏好
- 场景表现要求
- 特定模型需要的额外提示
```

注意：

```text
Auxiliary Prompt 如果过强，可能覆盖角色性格。
如果与 Main Prompt、Post-History Instructions 冲突，模型可能优先遵循更靠后的指令。
```

---

### 9. World Info after

**位置：** 角色定义之后。  
**role：** 通常作为 system 插槽。  
**作用：** 插入被触发的世界书内容，尤其适合与当前角色、场景或近期剧情相关的背景。

适合放在 after 的内容：

```text
- 当前相关 NPC
- 当前相关组织
- 与角色当前行动有关的地点
- 对角色设定的补充
```

和 before 的区别：

```text
Before：更像世界观底层背景。
After：更像角色定义后的补充信息。
```

---

### 10. Chat Examples

**位置：** 通常在 Chat History 之前。  
**role：** 展开后通常是 user / assistant 交替示例。  
**作用：** 提供角色说话风格、对话节奏和格式示例。

适合内容：

```text
- 角色典型语气
- 角色常用句式
- 角色与用户互动方式
- 叙事格式样例
```

注意：

```text
Examples 不是剧情事实的主要存储位置。
模型可能模仿 examples 的风格，但不一定把其中所有内容当成当前事实。
```

如果 example 太多：

```text
- 会占用上下文预算
- 会挤掉真实聊天历史
- 可能让模型重复示例风格或桥段
```

---

### 11. Chat History

**位置：** 通常在 examples 之后、Post-History Instructions 之前。  
**role：** 通常是 `user` / `assistant` 交替。  
**作用：** 提供最近实际对话上下文。

特点：

```text
- 越新的消息通常越靠近末尾
- 越靠近末尾对当前回复影响越强
- 超出上下文预算的旧历史会被裁剪
```

World Info 扫描也主要依赖最近聊天消息：

```text
Scan Depth = N
→ 通常扫描最近 N 条聊天消息中的 key
```

---

### 12. Post-History Instructions

**位置：** 通常在 Chat History 之后，接近最终 messages 末尾。  
**role：** 通常是 `system`，但可能被 post-processing 改写。  
**作用：** 给模型最后的行为指令。

适合内容：

```text
- 本轮生成的最终约束
- 回复格式要求
- 不要替用户行动
- 保持角色扮演
- 避免总结式回复
- 强化当前任务
```

特点：

```text
因为它通常在历史之后，距离生成位置很近，
所以实际约束力往往强于 Main Prompt。
```

风险：

```text
如果 Post-History Instructions 过长或过强，
可能压过角色卡、世界观和自然对话状态。
```

---

## Prompt 排序策略

默认排序可以概括为：

```text
全局任务
→ 世界/角色/用户设定
→ 示例对话
→ 实际聊天历史
→ 最终后置指令
```

常见顺序：

```text
1. Main Prompt
2. World Info before
3. Persona Description
4. Character Description
5. Character Personality
6. Scenario
7. Enhance Definitions
8. Auxiliary Prompt
9. World Info after
10. Chat Examples
11. Chat History
12. Post-History Instructions
```

策略解释：

```text
Main Prompt：定义整体任务。
World Info before：提供基础世界背景。
Persona / Character / Scenario：定义用户、角色、场景。
Auxiliary Prompt：补充行为规则。
World Info after：补充当前相关背景。
Chat Examples：给风格示例。
Chat History：给真实上下文。
Post-History Instructions：给最终约束。
```

通用经验：

```text
越长期、越基础的内容 → 越靠前。
越当前、越强约束的内容 → 越靠后。
越希望模型马上遵守的内容 → 靠近末尾。
```

---

## Relative 与 In-Chat 注入机制

Prompt Manager 中的 prompt 节点通常有两类位置策略：

### Relative

Relative 表示按照 Prompt Manager 列表顺序插入。

适合：

```text
- Main Prompt
- Character Description
- Scenario
- Auxiliary Prompt
- World Info before / after
- Chat Examples
- Chat History
- Post-History Instructions
```

特点：

```text
结构清晰，适合稳定 prompt 架构。
```

### In-Chat + Depth

In-Chat 表示插入到 Chat History 内部，而不是按照列表顺序。

Depth 概念：

```text
Depth 0 = 插在最后一条 chat message 之后
Depth 1 = 插在最后一条 chat message 之前
Depth 2 = 插在倒数第二条 chat message 之前
Depth 3 = 插在倒数第三条 chat message 之前
```

适合：

```text
- Author's Note
- 当前场景强提醒
- 临时状态
- 当前任务目标
- 当前地点 / 时间 / 危机
```

策略：

```text
当前强相关信息 → Depth 0–2
一般当前状态 → Depth 2–4
不应过度影响本轮输出的信息 → 更深或放入普通 World Info
```

---

## World Info / 世界书的基本概念

World Info，也常称世界书、Lorebook，本质是一个动态上下文检索系统。

它的基本单位是 entry：

```text
Entry = 一条可被触发并插入 prompt 的设定卡片
```

一个 entry 通常包含：

```text
- Title / Comment：标题或备注，通常用于管理，不一定插入 prompt
- Key / Keys：触发关键词
- Optional Filter：额外过滤条件
- Content：真正插入 prompt 的内容
- Insertion Position：插入位置
- Insertion Order：插入顺序
- Strategy：触发策略，例如 keyword、constant、vectorized
- Budget / Probability / Cooldown / Delay / Sticky 等控制项
```

核心原则：

```text
只有 Content 才是要给模型看的主要内容。
Key 是触发用的，不等于一定会出现在 prompt 中。
Tag 多用于管理，不是主要触发机制。
```

---

## 世界书来源与启用优先级

SillyTavern 中常见世界书来源包括：

```text
1. Global Lore
2. Character Lore
3. Persona Lorebook
4. Chat Lorebook
```

### 1. Global Lore

全局世界书。

适合：

```text
- 共用世界观
- 多角色共享地点、组织、历史
- 大型设定百科
```

风险：

```text
全局启用过多会导致误触发、预算占用和上下文污染。
```

建议：

```text
只启用当前会话真正需要的全局世界书。
不要把所有 lorebook 全部全局启用。
```

### 2. Character Lore

绑定到角色的世界书。

适合：

```text
- 角色专属背景
- 角色专属关系网
- 角色知道或关心的组织、地点、人物
- 角色能力和限制
```

建议：

```text
单角色 RP 中优先使用 Character Lore。
```

### 3. Persona Lorebook

绑定到用户 persona 的世界书。

适合：

```text
- 用户身份
- 用户种族/职业/能力
- 用户与世界的关系
- 用户长期携带的物品或状态
```

建议：

```text
用户设定很重要时启用。
如果用户只是普通对话对象，可以不启用或保持很短。
```

### 4. Chat Lorebook

绑定到当前会话的世界书。

适合：

```text
- 当前剧情状态
- 当前章节目标
- 已发生但不应遗忘的事件
- 当前地点
- 当前任务
- 临时 NPC 状态
```

建议：

```text
长线剧情强烈建议使用 Chat Lorebook。
它比把所有状态塞进角色卡更合理。
```

推荐优先级：

```text
当前会话状态 → Chat Lorebook
用户长期身份 → Persona Lorebook
角色长期设定 → Character Lore
世界通用设定 → Global Lore
```

---

## 世界书 Entry 的触发流程

世界书 entry 激活并不是简单的 key 命中。完整流程可以理解为：

```text
1. 收集参与本轮扫描的世界书
2. 收集这些世界书中启用的 entries
3. 排除不符合当前生成类型的 entries
4. 排除不符合角色过滤条件的 entries
5. 构造扫描文本缓冲区
6. 对每个 entry 扫描 key
7. 如果 key 命中，继续检查 Optional Filter
8. 检查概率、冷却、延迟、sticky 等状态
9. 检查递归触发
10. 根据 order、priority、budget 决定是否插入
11. 按 insertion position 插入 prompt
```

概念伪代码：

```ts
const activeBooks = collectActiveWorldBooks();
const candidateEntries = [];

for (const book of activeBooks) {
  for (const entry of book.entries) {
    if (!entry.enabled) continue;
    if (!generationTypeAllowed(entry)) continue;
    if (!characterFilterAllowed(entry)) continue;
    candidateEntries.push(entry);
  }
}

const scanBuffer = buildScanBuffer({
  recentMessages,
  scanDepth,
  authorsNote,
  includeNames,
});

const activated = [];

for (const entry of candidateEntries) {
  if (entry.constant) {
    activated.push(entry);
    continue;
  }

  if (!matchesAnyKey(entry.keys, scanBuffer)) continue;
  if (!optionalFiltersPass(entry, scanBuffer)) continue;
  if (!probabilityPass(entry)) continue;
  if (!cooldownDelayStickyPass(entry)) continue;

  activated.push(entry);
}

const recursed = activateRecursedEntries(activated, candidateEntries);
const finalEntries = fitWorldInfoBudget([...activated, ...recursed]);
insertEntries(finalEntries);
```

---

## Key、Tag、Filter 的区别

这是最容易混淆的部分。

### Key / Keys

Key 是世界书 entry 的主要触发字段。

例如：

```text
Key: 黑塔, Black Tower
Content: 黑塔是北境最高的法师机构，负责监管禁术和星象观测。
```

当扫描文本中出现 `黑塔` 或 `Black Tower` 时，该 entry 可能被激活。

Key 的设计原则：

```text
- 具体
- 不泛化
- 用户或角色真的可能说出口
- 覆盖必要同义词
- 尽量避免常见词
```

坏例子：

```text
王
城
魔法
学校
她
朋友
```

好例子：

```text
银冠王朝
艾尔温王城
星辉学院
禁咒委员会
米蕾雅
灰鸦旅团
```

### Tag / 标签

Tag 通常用于管理、分类、筛选。

例如：

```text
Tag: 北境
Tag: NPC
Tag: 地点
Tag: 主线
```

Tag 的用途更接近：

```text
- 帮用户整理 entry
- 搜索和筛选 entry
- 表示条目类别
```

关键点：

```text
默认世界书触发主要不是靠 tag。
不要把 tag 当成 key。
```

### Optional Filter

Optional Filter 是 key 命中后的进一步条件判断。

它可以减少误触发。

示例：

```text
Entry: 王
Primary Key: 王
Optional Filter: 银冠, 王朝, 王都
Logic: AND ANY
```

含义：

```text
只有扫描文本里出现“王”，并且同时出现“银冠 / 王朝 / 王都”之一时，才触发。
```

另一个例子：

```text
Entry: Rose 作为人名
Key: Rose
Optional Filter: flower, garden, bouquet
Logic: NOT ANY
```

含义：

```text
出现 Rose 但没有 flower / garden / bouquet 时，才更可能认为 Rose 是人名，而不是玫瑰花。
```

---

## 世界书扫描范围

世界书不是默认扫描最终 prompt 的全部 messages。

更准确地说，它通常扫描一个有限的 scan buffer。

主要来源：

```text
1. 最近 N 条聊天消息
2. 当前用户刚发送的消息
3. Author's Note
4. 已激活 entry 的 content，前提是开启递归
5. 消息发送者名字，前提是开启 Include Names
```

### 最近 N 条聊天消息

由 Scan Depth 控制。

```text
Scan Depth = 1
→ 扫描最后 1 条聊天消息

Scan Depth = 2
→ 扫描最后 2 条聊天消息

Scan Depth = 4
→ 扫描最后 4 条聊天消息

Scan Depth = 0
→ 不扫描普通聊天历史，但仍可能评估 Author's Note 和递归内容
```

例子：

```text
历史：
1. User: 我们昨天离开了王城。
2. Assistant: 是的，现在北境越来越危险。
3. User: 我想去黑塔找禁咒委员会。

Scan Depth = 1
→ 只扫描第 3 条。
→ 可能触发：黑塔、禁咒委员会。
→ 不会因为第 1 条触发：王城。

Scan Depth = 3
→ 扫描第 1、2、3 条。
→ 可能触发：王城、北境、黑塔、禁咒委员会。
```

### 当前用户消息

当前用户刚发出的消息通常就是最新的聊天消息，因此一般会进入扫描范围。

例如：

```text
User: 我们去星辉学院。
```

如果 `星辉学院` 是某条 entry 的 key，且 Scan Depth 覆盖当前消息，该 entry 通常可能触发。

### 不默认扫描的内容

不要默认认为以下内容一定参与世界书 key 扫描：

```text
- Main Prompt
- Character Description
- Character Personality
- Scenario
- Persona Description
- Chat Examples
- Post-History Instructions
- 最终提交给模型的所有 system/user/assistant messages
```

这些内容可能被插入最终 prompt，但不等于它们一定是 World Info 的扫描源。

---

## Summary、Author's Note、Persona 中出现 Key 的情况

### 1. Author's Note 中出现 key

Author's Note 比较特殊，通常可能参与世界书触发判断。

例如：

```text
Author's Note:
当前地点是黑塔地下档案室，禁咒委员会正在追踪 {{user}}。
```

如果世界书里有：

```text
Key: 黑塔
Key: 禁咒委员会
```

那么相关 entries 可能被触发。

优点：

```text
适合当前场景强提醒。
```

风险：

```text
Author's Note 中长期存在的 key 会稳定触发对应 entry，造成上下文污染。
```

建议：

```text
Author's Note 只放当前最相关的地点、目标、危机、状态。
不要把大量世界观索引塞进 Author's Note。
```

### 2. Summary / 总结中出现 key

Summary 是否触发世界书，取决于它的实现方式和配置。

两种情况：

```text
情况 A：Summary 只是作为普通 system prompt 插入最终上下文。
→ 不应假设它会触发世界书。

情况 B：某个扩展或设置把 Summary 纳入 World Info 扫描源。
→ Summary 中的 key 可能触发世界书。
```

风险：

```text
Summary 往往包含过去发生过的地点、人物、组织。
如果它参与扫描，可能导致旧 key 反复触发。
```

坏例子：

```text
他们曾经经过黑塔、王城、星辉学院，并听说灰鸦旅团。
```

这可能使多个已经不相关的 entry 继续触发。

更好的写法：

```text
Past: 他们曾经接触过多个北境势力，包括王城与学院。
Current focus: 当前相关的是黑塔和禁咒委员会。
```

### 3. Persona / 用户 note 中出现 key

Persona Description 会被插入 prompt，但不应自动等同于世界书扫描源。

如果 persona 中写：

```text
{{user}} 是星辉学院的学生，曾受黑塔庇护。
```

这可能出现在最终 prompt 中，但不一定触发 `星辉学院` 或 `黑塔` 的世界书 entry。

如果这些信息每轮都很重要，更稳妥的做法是：

```text
- 放入 Persona Lorebook
- 做成 Constant entry
- 放入 Chat Lorebook 当前状态
- 放入 Author's Note 当前提醒
```

### 4. 已触发 entry 的 content 中出现 key

如果开启递归扫描，已触发 entry 的内容可能继续触发其他 entry。

例如：

```text
Entry A
Key: 黑塔
Content: 黑塔由禁咒委员会管理。

Entry B
Key: 禁咒委员会
Content: 禁咒委员会是监管高危魔法的机构。
```

用户只说：

```text
我们去黑塔。
```

触发流程可能是：

```text
黑塔 → 触发 Entry A
Entry A 的 content 中出现 禁咒委员会
递归扫描 → 触发 Entry B
```

风险：

```text
递归可能链式激活大量 entries，导致预算爆炸和信息噪声。
```

建议：

```text
- Max Recursion Steps 设为 1–2
- 只让上层概念递归
- 底层百科 entry 禁止继续递归
```

---

## 世界书插入位置与排序

World Info entry 被激活后，需要决定插入 prompt 的位置和顺序。

### 插入位置

常见插入位置：

| 位置 | 适合内容 |
|---|---|
| Before Char Defs | 世界基础背景、规则、历史 |
| After Char Defs | 与角色相关的背景补充 |
| Before Example Messages | 影响示例理解的设定 |
| After Example Messages | 示例之后、历史之前的补充 |
| Author's Note top/bottom | 当前场景强提醒 |
| In-Chat Depth | 当前剧情状态、临时目标、短期约束 |
| Outlet | 需要手动控制插入位置的高级配置 |

经验规则：

```text
长期背景 → 靠前
角色补充 → 角色定义后
当前状态 → 靠近聊天历史末尾
强约束 → Author's Note 或 Depth 0–2
```

### Insertion Order

Order 决定多个 entry 插入时的相对顺序。

常用分层：

```text
Order 0–100
→ 世界基础、历史背景

Order 100–300
→ 地点、组织、种族、职业

Order 300–600
→ 当前剧情相关 NPC、物品、任务

Order 600–900
→ 当前场景强约束、临时状态、作者意图
```

经验：

```text
越希望模型遵守，order 越高。
越接近当前回复，影响越强。
```

---

## 预算与裁剪策略

SillyTavern 受模型上下文窗口限制。整体可以理解为：

```text
模型上下文窗口
- 预留 response tokens
= 可用于 prompt/context 的 token budget
```

这个 budget 需要容纳：

```text
- Main Prompt
- 角色卡
- Persona
- Scenario
- World Info
- Chat Examples
- Summary
- Author's Note
- Chat History
- Post-History Instructions
- 扩展注入内容
```

### World Info Budget

World Info 通常有单独预算或比例限制。

如果世界书预算耗尽：

```text
即使命中 key，entry 也可能不会插入。
```

常见优先级逻辑：

```text
- Constant entries 通常优先插入
- 直接 key 命中的 entries 优先于递归命中的 entries
- Order / Priority 更高的 entry 更容易靠近末尾或更优先
- 超出预算的 entry 会被丢弃
```

### 推荐预算

```text
4k–8k context：World Info 预算 5–10%
16k–32k context：World Info 预算 10–15%
64k+ context：World Info 预算 10–20%，但仍应控制噪声
```

### Entry 长度建议

```text
核心规则：30–100 tokens
普通 NPC / 地点：50–150 tokens
复杂组织 / 规则：100–250 tokens
大型百科：拆分成多个小 entry
```

原则：

```text
一个 entry 只解决一个问题。
Content 要能独立理解。
不要把无关信息塞进同一个 entry。
```

---

## Prompt Post-Processing

在发送给模型之前，SillyTavern 可能根据 API 后端兼容性处理 messages。

常见模式：

```text
None
→ 尽量保留原始 messages 结构。

Merge consecutive messages from the same role
→ 合并连续相同 role 的 messages。

Semi-strict
→ 更严格地合并 role，通常只允许一个 system message。

Strict
→ 强制满足更严格的 user / assistant 结构要求。

Single user message
→ 把所有内容压成一个 user message。
```

影响：

```text
Prompt Manager 中看到的 system/user/assistant 分布，
可能和最终 API 请求中的 messages 不完全一致。
```

因此分析问题时要区分：

```text
逻辑 prompt 结构
≠ post-processing 后的实际提交结构
```

---

## 推荐配置模板

### 单角色 RP

```text
启用：
- Character Lore
- 当前世界观相关 Global Lore
- 必要 Chat Lorebook

Prompt 顺序：
- Main Prompt
- World Info before
- Character Description
- Character Personality
- Scenario
- Auxiliary Prompt
- World Info after
- Chat Examples
- Chat History
- Post-History Instructions

World Info：
- 核心角色设定放角色卡
- 地点/NPC/组织放 Character Lore 或 Global Lore
- 当前剧情状态放 Chat Lorebook
```

### 长线剧情

```text
启用：
- Character Lore
- Chat Lorebook
- 必要 Global Lore
- 必要 Persona Lorebook

策略：
- 当前章节目标：Chat Lorebook Constant 或高 Order
- 当前地点：Author's Note / Depth 0–2
- 历史事实：Summary
- 世界百科：Keyword World Info
- 旧剧情：不要依赖 Author's Note 长期保留
```

### 大型世界观

```text
启用：
- 当前地区/章节相关 Global Lore
- 当前角色相关 Character Lore

避免：
- 全部世界书全局启用
- 泛 key
- 大量 Constant entries
- 无限递归

建议：
- 大条目拆分
- 用具体 key
- 用 Optional Filter 降低误触发
- 只让当前章节相关资料参与扫描
```

### 高稳定性配置

```text
Scan Depth: 2–4
World Info Budget: 10%左右
Constant entries: 1–5 条
Recursion: 关闭或最多 1–2 层
Author's Note: 只放当前场景强相关信息
Post-History Instructions: 简短明确
```

---

## 排错清单

### 问题：世界书没有触发

检查：

```text
1. 对应世界书是否启用？
2. entry 是否启用？
3. key 是否真的出现在 Scan Depth 范围内？
4. 当前用户消息是否被纳入扫描？
5. Optional Filter 是否没通过？
6. generation type 是否被限制？
7. 角色过滤是否排除了当前角色？
8. probability 是否未命中？
9. delay / cooldown 是否阻止触发？
10. World Info budget 是否已满？
11. key 是否大小写、整词、regex 设置不符合？
```

### 问题：世界书乱触发

检查：

```text
1. key 是否太泛？
2. 是否把 tag 当 key 用了？
3. Summary 或 Author's Note 是否长期包含旧 key？
4. Scan Depth 是否太大？
5. Include Names 是否导致角色名触发？
6. 递归是否链式激活？
7. Constant entry 是否太多？
8. Global Lore 是否启用过多？
9. Optional Filter 是否缺失？
10. NOT filter 是否应该添加？
```

### 问题：模型忽视世界书

检查：

```text
1. entry content 是否太长、太散？
2. entry 是否插入得太靠前？
3. 是否被后面的历史或 Post-History Instructions 覆盖？
4. 当前聊天历史是否与世界书矛盾？
5. 世界书内容是否没有明确写成事实？
6. 是否需要提高 order 或改为 Author's Note / Depth 注入？
7. 是否需要把核心规则设为 Constant？
```

### 问题：上下文太短，历史被挤掉

检查：

```text
1. Main Prompt 是否过长？
2. 角色卡是否过长？
3. examples 是否太多？
4. World Info budget 是否过高？
5. Constant entry 是否过多？
6. Summary 是否冗长？
7. Post-History Instructions 是否重复？
8. 是否启用了太多扩展注入？
```

---

## 术语表

### Prompt Manager

SillyTavern 中管理 prompt 节点、顺序、启用状态和注入位置的界面。

### Prompt Node

Prompt Manager 中的一项，例如 Main Prompt、Character Description、Chat History、Post-History Instructions。

### Marker

占位节点，本身不一定是实际文本，用于插入角色卡、世界书、聊天历史等动态内容。

### World Info / Lorebook / 世界书

根据关键词或其他策略动态插入背景设定的系统。

### Entry

世界书中的单条设定卡。

### Key

entry 的触发关键词。

### Tag

entry 的分类或管理标签，通常不是主要触发字段。

### Optional Filter

key 命中后的进一步条件判断，用来减少误触发。

### Scan Depth

世界书扫描最近多少条聊天消息。

### Constant Entry

不依赖 key，始终尝试插入的世界书 entry。

### Recursive Scanning

递归扫描。已触发 entry 的 content 里出现其他 key 时，继续触发其他 entry。

### Author's Note

作者注释，通常靠近聊天历史末尾，用于当前场景强提醒。

### Summary

聊天总结或记忆摘要，用于压缩过去剧情。是否参与世界书扫描取决于具体实现和配置。

### Post-History Instructions

聊天历史之后的最终指令，通常靠近 prompt 末尾，对当前输出影响较强。

### Prompt Post-Processing

发送给模型前，为适配 API 格式而对 messages 进行合并、改写或压缩的处理。

---

## 最终建议

构建稳定 SillyTavern prompt 和世界书系统时，应遵循以下原则：

```text
1. 角色长期设定放角色卡。
2. 世界观百科放 World Info。
3. 当前剧情状态放 Chat Lorebook。
4. 当前强提醒放 Author's Note 或 In-Chat Depth。
5. 过去剧情压缩进 Summary。
6. 世界书 key 要具体，不要泛化。
7. 世界书 tag 用于管理，不要当触发词。
8. 不要假设最终 prompt 中出现 key 就一定触发世界书。
9. 不要全局启用所有世界书。
10. 不要让递归和 Constant entry 无限制膨胀。
11. 越当前、越重要的内容，越靠近末尾。
12. 越基础、越长期的内容，越靠前。
```

一句话总结：

```text
SillyTavern 的 prompt 是一个分层上下文构建系统；
世界书是一个基于 key 的有限扫描与动态注入系统。
正确的做法不是把所有信息都塞进去，
而是让当前回复真正需要的信息，在正确的位置、以正确的优先级出现。
```
