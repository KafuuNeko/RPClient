# SillyTavern 总结机制说明：Summary Prompt、History 展开与记忆注入

> 本文档用于说明 SillyTavern 中“总结 / 摘要 / 长期记忆”相关机制的工作方式，重点解释：总结时使用什么提示词、`{{history}}` 如何提交、总结请求是否包含世界书 / 作者注释 / 角色卡、总结结果如何注入后续 prompt，以及如何避免摘要污染。

---

## 1. 总结机制的核心结论

SillyTavern 的“总结”通常不是正常聊天生成流程的一部分，而是一个独立的任务流程。

正常聊天生成大致是：

```text
Main Prompt
+ Character Description
+ Character Personality
+ Scenario
+ Persona
+ World Info
+ Chat Examples
+ Chat History
+ Post-History Instructions
→ 生成 {{char}} 的下一条回复
```

总结生成大致是：

```text
Summary Prompt
+ Existing Summary
+ Formatted Chat History
→ 生成 / 更新剧情摘要
```

因此：

```text
总结请求 ≠ 正常聊天请求
Summary Prompt ≠ Main Prompt
Main API ≠ Main Prompt
```

如果 Summarize 扩展中显示 “Summarize with Main API”，通常意思是使用当前主 API / 当前连接模型执行总结任务，而不是使用 Main Prompt。

---

## 2. 总结时使用什么提示词？

总结时使用的是 **Summarize 扩展或相关记忆扩展中的专用 Summary Prompt**。

它的目标不是让模型继续角色扮演，而是让模型执行文本处理任务：

```text
阅读聊天记录
→ 提取重要剧情事实
→ 压缩成长期记忆摘要
```

一个典型 Summary Prompt 会包含：

```text
任务说明
总结规则
已有摘要
需要总结的聊天记录
输出格式要求
长度限制
```

示例：

```text
请将以下聊天记录整理为一份简洁的剧情记忆摘要。

规则：
- 不要继续角色扮演。
- 不要替 {{char}} 或 {{user}} 生成新回复。
- 只总结聊天中已经实际发生的内容。
- 保留重要事实、角色关系变化、承诺、伤势、地点、目标、未解决冲突和当前场景状态。
- 排除推测、泛世界观设定、重复对白、寒暄和无关细节。
- 尽量按时间顺序整理。
- 使用简洁项目符号。
- 控制在 {{words}} 字以内。

已有摘要：
{{summary}}

需要总结的聊天记录：
{{history}}
```

---

## 3. `{{history}}` 是如何提交的？

### 3.1 核心结论

`{{history}}` 通常会被渲染成一整段格式化文本，直接嵌入 Summary Prompt。

它通常不是把原始聊天历史逐条保留为 Chat Completion 的独立 `messages[]` 条目。

也就是说，它更像：

```text
需要总结的聊天记录：
{{user}}: 我们必须进去。
{{char}}: 太危险了，里面有巡逻。
{{user}}: 我知道，但线索就在里面。
{{char}}: ……好，我跟你一起去。
```

而不是：

```json
{
  "messages": [
    { "role": "user", "content": "我必须进去。" },
    { "role": "assistant", "content": "太危险了，里面有巡逻。" },
    { "role": "user", "content": "我知道，但线索就在里面。" },
    { "role": "assistant", "content": "……好，我跟你一起去。" }
  ]
}
```

### 3.2 为什么要把 history 合并成文本？

因为总结任务的语义是：

```text
下面是一段要处理的材料，请总结它。
```

不是：

```text
下面是一段对话，请继续回复。
```

如果把每条历史继续作为独立 user / assistant message 传给模型，模型可能更容易误判为继续对话，而不是总结材料。

因此，通常会把历史放进一个明确区域：

```text
需要总结的聊天记录：
...
```

让模型知道这些文本是“输入材料”，不是当前要继续的对话上下文。

---

## 4. 总结请求通常有几个 message？

总结请求通常只有一个主要 payload：

```text
Summary Prompt
+ Existing Summary
+ Formatted History
```

在 Chat Completion API 中，这个 payload 可能被包装为：

### 形式 A：单个 user message

```json
{
  "messages": [
    {
      "role": "user",
      "content": "请将以下聊天记录整理为摘要...\n\n已有摘要：...\n\n需要总结的聊天记录：..."
    }
  ]
}
```

### 形式 B：system + user 两个 message

```json
{
  "messages": [
    {
      "role": "system",
      "content": "你是一个剧情摘要工具。只输出摘要，不继续对话。"
    },
    {
      "role": "user",
      "content": "请总结以下聊天记录：\n\n已有摘要：...\n\n聊天记录：..."
    }
  ]
}
```

不同版本、不同扩展、不同 API 后端可能包装方式不同，但核心结构通常一致：

```text
总结请求通常不是完整的角色扮演 prompt 栈。
```

---

## 5. 总结过程中是否提交世界书条目？

### 5.1 默认建议：不要提交

总结过程中通常不应该提交大量 World Info / 世界书条目。

原因是：

```text
世界书 = 背景知识 / 可能性 / 设定
聊天历史 = 已经实际发生的剧情事实
```

总结的目标是记录“已经发生的事情”，而不是把所有背景设定写入记忆。

如果总结时把世界书也交给模型，可能导致摘要污染。

例如世界书写着：

```text
黑塔地下有一条通往王城的密道。
```

但聊天中角色并没有发现这条密道。

如果总结时带入世界书，模型可能错误总结成：

```text
他们发现黑塔地下有一条通往王城的密道。
```

这会把“背景设定”误写成“剧情事实”。

### 5.2 No WI / AN 的意义

在 Summarize 相关设置中，可能会看到类似：

```text
No WI / AN
Skip WI / AN
```

这里的 WI 通常指：

```text
World Info
```

AN 通常指：

```text
Author's Note
```

这个选项的目标是：

```text
总结时不要把世界书和作者注释混进被总结文本。
```

### 5.3 Raw Prompt Builder 与 Classic Prompt Builder

不同总结构建器行为可能不同：

```text
Raw prompt builder:
  通常只按 Summary Prompt 模板构造请求，避免自动混入 WI / AN。

Classic prompt builder:
  可能存在是否排除 WI / AN 的选项。
```

实际行为取决于 SillyTavern 版本、扩展版本、设置选项和你使用的 prompt builder。

---

## 6. 总结过程中是否提交 Author's Note？

通常也不建议提交。

Author's Note 常用于：

```text
当前写作方向
当前场景提醒
临时叙事要求
不要跳过剧情
保持某种氛围
```

这些内容不一定是已经发生的事实。

例如 Author's Note 写：

```text
当前章节应保持紧张压抑，不要让角色立刻发现真相。
```

这是一条写作指令，不应该被总结成剧情记忆。

错误摘要可能变成：

```text
角色没有发现真相，气氛紧张压抑。
```

其中“气氛紧张”可能可以记录，但“不要立刻发现真相”是指令，不是剧情事实。

因此，summary prompt 中最好明确要求：

```text
不要把作者注释、写作指令或未发生的设定写入摘要。
```

---

## 7. 总结过程中是否提交角色卡？

通常不需要提交完整角色卡。

原因是：

```text
角色卡 = 角色长期设定
聊天记录 = 当前剧情事实
总结 = 对剧情事实的压缩记忆
```

总结时如果带入角色卡，模型可能把角色设定中“可能存在但未在剧情中体现”的内容写进摘要。

例如角色卡写：

```text
{{char}} 曾经背叛过王国。
```

但聊天中从未提及此事。

错误摘要可能变成：

```text
{{char}} 的王国背叛史影响了当前事件。
```

这就是把角色设定污染进剧情摘要。

更好的做法是：

```text
总结只基于聊天中出现或明确确认的信息。
```

---

## 8. Existing Summary / `{{summary}}` 的作用

`{{summary}}` 通常表示已有摘要。

在增量总结时，模型会收到：

```text
已有摘要：
{{summary}}

需要总结的新聊天记录：
{{history}}
```

然后输出一个更新后的摘要。

常见任务是：

```text
把已有摘要和新聊天记录合并，生成一份更新后的完整摘要。
```

而不是只总结新片段。

推荐在 prompt 中明确：

```text
请在保留已有摘要中仍然重要信息的基础上，整合新聊天记录，输出更新后的摘要。
```

否则模型可能只总结新 history，遗忘旧 summary。

---

## 9. Summary 结果存在哪里？

总结生成后，结果通常会保存到当前 chat 的摘要状态 / metadata 中。

后续正常聊天时，它会作为某种 memory / summary context 被注入 prompt。

完整流程是：

```text
聊天历史变长
→ Summarize 扩展触发总结
→ 使用 Summary Prompt 生成摘要
→ 摘要保存到当前 chat
→ 后续正常回复时，摘要被注入上下文
→ 模型通过摘要记住早期剧情
```

注意：

```text
生成摘要时的请求
和
后续正常聊天时注入摘要
是两个不同阶段。
```

---

## 10. Summary 注入后是否会触发世界书？

不一定。

这取决于 summary 是否被纳入 World Info 扫描源。

需要区分：

```text
Summary 出现在最终 prompt 中
≠ Summary 一定参与 World Info key 扫描
```

World Info 通常不是扫描最终提交给模型的所有 messages，而是扫描特定文本来源，例如：

```text
最近 N 条聊天历史
Author's Note
递归激活的 World Info entry 内容
其他被特定设置纳入扫描的来源
```

因此：

```text
summary 里有 key
不等于一定触发世界书
```

只有当当前配置或扩展明确把 summary / memory 纳入 WI scan 时，summary 里的 key 才可能触发对应世界书条目。

---

## 11. Summary 应该注入到 prompt 的什么位置？

常见策略是把 summary 放在：

```text
角色定义之后
真实聊天历史之前
```

也就是：

```text
Main Prompt
Character / Persona / Scenario
Summary / Memory
Recent Chat History
Post-History Instructions
```

原因：

```text
Summary 是较早剧情的压缩记忆
Recent Chat History 是最近发生的内容
Post-History Instructions 是最终生成约束
```

一般不建议把 summary 放得比当前场景 Author's Note 或 Post-History Instructions 更靠后。

因为 summary 是背景记忆，不应该压过当前场景指令。

---

## 12. 好的剧情摘要应该包含什么？

建议保留：

```text
已发生的重要事件
角色关系变化
承诺、约定、契约
伤势、状态、资源变化
当前位置
当前目标
未解决冲突
已获得线索
已揭露秘密
重要物品归属
阵营关系变化
```

示例：

```text
- {{user}} 和 {{char}} 抵达黑塔外墙，并确认入口被禁咒委员会封锁。
- {{char}} 起初反对潜入，但在 {{user}} 表示线索就在塔内后同意同行。
- 二人目前位于黑塔地下档案室外，正在躲避巡逻。
- 未解决目标：找到失踪档案，并查明禁咒委员会为何封锁黑塔。
```

---

## 13. 摘要中应该避免什么？

应避免：

```text
寒暄
重复对白
无关细节
泛世界观百科
没有在聊天中发生的背景设定
模型推测
作者注释里的写作指令
未来计划被写成既成事实
角色卡设定被误写成剧情事件
过度文学化描写
```

错误示例：

```text
- 黑塔地下有通往王城的密道。
```

如果聊天中没有发现密道，这条不该写入 summary。

更好的写法：

```text
- {{user}} 怀疑黑塔地下可能存在隐秘通道，但目前尚未确认。
```

前提是聊天中确实出现过这个怀疑。

---

## 14. 推荐 Summary Prompt 模板：中文 RP 版

```text
请将以下聊天记录整理为一份简洁的剧情记忆摘要。

任务：
你是剧情记忆整理器，不是角色扮演参与者。你的目标是压缩聊天记录，保留后续对话必须记住的事实。

规则：
- 不要继续角色扮演。
- 不要替 {{char}} 或 {{user}} 生成新回复。
- 只总结聊天中已经实际发生、明确说出或明确确认的内容。
- 不要把世界书、角色卡、作者注释、写作指令或背景设定中未在聊天里发生的内容写入摘要。
- 保留重要事件、角色关系变化、承诺、伤势、地点、目标、未解决冲突、当前场景状态和关键线索。
- 如果某事只是猜测、计划或怀疑，必须标明“怀疑 / 计划 / 尚未确认”，不要写成事实。
- 排除寒暄、重复对白、无关细节和泛世界观百科。
- 尽量按时间顺序整理。
- 使用简洁项目符号。
- 控制在 {{words}} 字以内。

已有摘要：
{{summary}}

需要总结的聊天记录：
{{history}}

请输出更新后的摘要：
```

---

## 15. 推荐 Summary Prompt 模板：英文版

```text
Summarize the following chat history into a concise narrative memory summary.

Task:
You are a memory summarizer, not a roleplay participant. Your goal is to compress the chat into facts that are important for future continuity.

Rules:
- Do not continue the roleplay.
- Do not write a new message for {{char}} or {{user}}.
- Only summarize events that actually happened, were explicitly stated, or were clearly confirmed in the chat.
- Do not include world lore, character-card information, author's notes, writing instructions, or background facts unless they actually occurred or were explicitly confirmed in the chat.
- Preserve important events, relationship changes, promises, injuries, locations, goals, unresolved conflicts, current scene state, and key clues.
- If something is only a suspicion, plan, or hypothesis, mark it as unconfirmed instead of writing it as fact.
- Exclude greetings, repeated dialogue, minor phrasing, irrelevant details, and generic worldbuilding.
- Keep the summary chronological when possible.
- Use compact bullet points.
- Keep the result under {{words}} words.

Existing summary:
{{summary}}

Chat history to summarize:
{{history}}

Output the updated summary:
```

---

## 16. 增量总结策略

长线 RP 中不建议每次都重总结全部历史，因为成本高且容易漂移。

更常见策略是：

```text
已有 summary
+ 新增聊天片段
→ 更新 summary
```

推荐规则：

```text
1. 保留仍然影响当前剧情的旧信息。
2. 删除已经过时、无关或被新剧情覆盖的信息。
3. 将重复信息合并。
4. 明确当前场景状态。
5. 不要把未确认内容升级为事实。
```

示例：

```text
旧摘要：
- 二人准备潜入黑塔。

新聊天：
- 二人成功进入黑塔地下档案室。
- {{char}} 受轻伤。

更新后摘要：
- {{user}} 和 {{char}} 已潜入黑塔地下档案室。
- {{char}} 在躲避巡逻时手臂轻伤，但仍能行动。
- 当前目标是找到失踪档案并避开禁咒委员会巡逻。
```

---

## 17. Summary 与 World Info 的分工

不要把 Summary 当成世界书。

两者分工不同：

| 类型 | 作用 | 内容特点 |
|---|---|---|
| Summary | 记录已经发生的剧情 | 动态、时间线、关系变化、当前状态 |
| World Info | 存储背景设定和可触发资料 | 静态或半静态、地点、组织、规则、NPC、术语 |
| Author's Note | 当前场景 / 写作提醒 | 短期、强提醒、靠近生成点 |
| Character Card | 角色基础设定 | 长期、稳定、角色核心定义 |
| Persona | 用户身份设定 | 长期或当前选中身份 |

推荐：

```text
已经发生的剧情 → Summary
世界观百科 → World Info
当前场景提醒 → Author's Note
角色固定设定 → Character Card / Character Lore
用户固定身份 → Persona / Persona Lorebook
```

---

## 18. 常见错误与修正

### 错误 1：把世界书内容写进 summary

错误：

```text
- 禁咒委员会掌控黑塔所有密道。
```

如果聊天中没有确认，不应写入。

修正：

```text
- {{user}} 和 {{char}} 正在调查禁咒委员会与黑塔封锁之间的关系。
```

### 错误 2：把未来计划写成已发生

错误：

```text
- 二人进入档案室并找到了失踪档案。
```

如果聊天中只是决定去找档案，应写：

```text
- 二人计划进入档案室寻找失踪档案，但尚未找到。
```

### 错误 3：摘要过度文学化

错误：

```text
- 在黑暗如墨的塔影下，两颗心被命运推向不可知的深渊。
```

修正：

```text
- {{user}} 和 {{char}} 抵达黑塔外墙，准备潜入。
```

### 错误 4：摘要保留太多对白

错误：

```text
- {{user}} 说“我们必须进去”，{{char}} 说“太危险了”，{{user}} 说“我知道”。
```

修正：

```text
- {{user}} 坚持进入黑塔寻找线索，{{char}} 起初反对但最终同意同行。
```

---

## 19. 推荐配置原则

### 总结输入

```text
只包含需要总结的聊天历史
可包含已有 summary
尽量排除 World Info
尽量排除 Author's Note
不要提交完整角色卡
不要提交正常聊天的 Main/Aux/PHI
```

### 总结 prompt

```text
明确要求只总结已发生事实
明确区分事实 / 猜测 / 计划
明确排除世界书、角色卡、作者注释中的未发生内容
要求简洁、可检索、可持续维护
```

### 总结输出

```text
项目符号优先
按时间顺序
保留当前状态
删除无关细节
不要文学化
不要把推测写成事实
```

### 后续注入

```text
Summary 放在近期聊天历史之前
不要比 Author's Note / Post-History Instructions 更靠后
不要让 Summary 压过当前场景提醒
是否允许 Summary 触发 World Info 要谨慎决定
```

---

## 20. 最简流程图

```text
[聊天历史增长]
        ↓
[触发 Summarize]
        ↓
[构造 Summary Prompt]
        ↓
[展开 {{summary}} 和 {{history}}]
        ↓
[通常作为一个文本块提交给模型]
        ↓
[模型输出更新摘要]
        ↓
[摘要保存到当前 chat]
        ↓
[后续正常聊天时注入 prompt]
        ↓
[帮助模型记住早期剧情]
```

---

## 21. 一句话总结

SillyTavern 的总结机制通常是：

```text
用专门的 Summary Prompt，把已有摘要和格式化后的聊天历史作为文本材料提交给模型，生成一份压缩记忆；它通常不走正常聊天的完整 prompt 栈，也不应默认带入世界书、角色卡或作者注释。
```

最重要的原则是：

```text
Summary 只记录已经发生的剧情事实；World Info 记录背景设定；Author's Note 记录当前写作/场景提醒。三者不要混用。
```
