# Issue #36：回复多版本与历史分支调研

调研日期：2026-11-18。代码基线：`f1da95f1c33ff97bb77eb06e47b80e4b0fc91075`，调研时本地 HEAD 与 GitHub 默认分支 HEAD 一致。

## 结论

[Issue #36](https://github.com/KafuuNeko/RPClient/issues/36) 更可能是在请求“保留多次生成的候选回复，可以回看、复制，并选回喜欢的版本继续聊天”。这是基于描述和代码的需求推断，还没有原作者的操作步骤或截图确认。

当前实现已经具有普通历史消息分页和单聊历史分支，但没有回复候选版本。单聊重新生成成功或接受流式部分正文后，会直接覆盖旧正文；群聊的部分入口还会先删除目标消息及后续历史。因此，这个需求具有明确的功能缺口，单纯增加历史加载数量无法解决被覆盖的回复丢失。

建议先实现单聊的“回复版本保存、回看与选择”，再利用现有独立会话分支实现“从历史版本继续”。无需先把线性会话改造成完整消息树。群聊入口的破坏性行为应单独列为后续范围，不能默认两种聊天已经具有相同语义。

## 原始诉求与不确定性

Issue 当前为 Open，没有评论，也没有说明版本号、所在页面、是否点击过重新生成。

原作者提出的目标可整理为：

- 旧的生成消息应当持续可见。
- 可以回到之前的内容，提取文本用于后续生成。
- 不满意后续回复时，能够从喜欢的消息继续。

同一作者在 [Issue #27](https://github.com/KafuuNeko/RPClient/issues/27) 中提到自己来自 Character.AI，并讨论过新建聊天和回退聊天的操作体验。这增强了“候选回复选择与历史续聊”的解释，但不能代替对 #36 触发步骤的确认。

需要区分三个场景：

| 场景 | 当前代码行为 | 判断 |
| --- | --- | --- |
| 普通聊天越聊越长，想向上查看更早消息 | 已有向前分页；查询排除 Summary，但不排除被摘要覆盖的普通消息 | 若实际无法翻页，应作为独立故障复现 |
| 对同一条回复多次重新生成，想找回前一个候选 | 旧正文被更新覆盖，没有候选表和选择状态 | 明确缺少回复版本功能 |
| 已经继续聊天，想回到某个历史回复或历史候选续聊 | 单聊已有从当前可见消息建立独立会话分支；不能选择已经被覆盖的候选 | 已有部分基础能力，仍需版本接入 |

“机器人可能记得旧消息”是作者的感受，不能据此认定旧版本仍在数据库里。下一次请求采用的是当时的消息、摘要及预算结果；摘要残留、模型表现和旧正文可恢复是不同的问题。

## 代码依据

| 能力或问题 | 代码位置 | 已确认行为 |
| --- | --- | --- |
| 单聊历史分页 | [ChatViewModel.onLoadOlderMessages](../app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt#L478)、[ChatMessageDao](../app/src/main/java/me/kafuuneko/rpclient/libs/room/dao/ChatMessageDao.kt#L37) | 使用创建时间与消息 ID 游标读取更早普通消息，不以摘要边界过滤展示历史 |
| 顶部滚动触发加载 | [ChatNormalPage](../app/src/main/java/me/kafuuneko/rpclient/feature/chat/ui/ChatNormalPage.kt#L192) | 首次定位后接近窗口顶部会发送 LoadOlderMessages |
| 摘要持久化 | [ChatRepository.saveSummary](../app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepository.kt#L1167) | 写入或更新 Summary 快照，不删除被覆盖的普通消息 |
| 单聊重新生成限制 | [ChatViewModel.regenerateLastAssistantMessage](../app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt#L1791) | 只允许会话末尾是角色回复时重新生成，只有开场白时拒绝；使用 GenerationOutput.Update |
| 单聊实际覆盖点 | [ChatRepository.commitGenerationResult](../app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepository.kt#L1017) | 直接 updateMessageContent，失效覆盖该消息的摘要，并提交世界书时序与活跃时间 |
| 单聊流式收尾 | [ChatViewModel.generateStreaming](../app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt#L2088) | finally 中提交已收到的非空正文；重生成也可能以部分正文覆盖旧回复；空结果保留旧正文 |
| 单聊分支 | [ChatViewModel.onBranchFromMessage](../app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt#L960)、[ChatRepository.createBranchSession](../app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepository.kt#L318) | 复制截至目标消息的前缀、图片引用和有效摘要，创建独立会话，并将世界书时序重置为 `{}`；UI 不向首条消息提供分支按钮 |
| 群聊消息菜单重新生成 | [GroupChatViewModel.onRegenerateMessage](../app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt#L1816) | 生成前调用 deleteMessagesFrom，删除目标及后续消息，再由原发言角色生成 |
| 群聊删除落点 | [GroupChatRepository.deleteMessagesFrom](../app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/GroupChatRepository.kt#L711) | 删除覆盖摘要、关联图片和目标起的消息；后续请求失败也没有恢复这些消息的逻辑 |
| 群聊设置页重新生成 | [GroupChatViewModel.onRegenerateLast](../app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt#L1853)、[GroupChatRepository.replaceLatestReply](../app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/GroupChatRepository.kt#L702) | 完整结果生成成功且原回复仍是未变更的末条时才覆盖；失败或取消保留原回复，但成功后仍无旧版本 |
| 数据模型 | [ChatMessage](../app/src/main/java/me/kafuuneko/rpclient/libs/room/entity/ChatMessage.kt)、[GroupChatMessage](../app/src/main/java/me/kafuuneko/rpclient/libs/room/entity/GroupChatMessage.kt) | 每条消息只有一个 content，没有回复版本集合或当前版本标识 |
| JSONL 兼容 | [ChatArchiveMessage](../app/src/main/java/me/kafuuneko/rpclient/libs/chat/ChatArchiveModels.kt)、[ChatArchiveCodec.decodeMessage](../app/src/main/java/me/kafuuneko/rpclient/libs/chat/ChatArchiveCodec.kt#L423) | 仅表达和读取当前 mes；没有文本 swipes、swipe_id、swipe_info 的解析和导出 |

以上是静态代码确认；尚未在 Android 设备上复现原作者操作，不能据此宣称普通历史翻页没有任何运行时故障。

## 可借鉴的交互

SillyTavern 的官方文档将替代回复切换与从消息建立分支分别提供，用户设置中也明确支持桌面和移动端的替代回复箭头。这与本项目现有的分支基础能力比较契合。[聊天操作](https://docs.sillytavern.app/usage/chatting/)、[用户设置](https://docs.sillytavern.app/usage/user-settings/)、[分支与检查点](https://docs.sillytavern.app/usage/core-concepts/chatfilemanagement/)

其源码使用 `swipes` 保存候选正文、`swipe_id` 标识当前候选、`swipe_info` 保存对应元数据。RPClient 若增加候选版本，导入导出应明确映射这些字段，避免迁移聊天时只剩当前显示的一版。[SillyTavern 官方实现](https://github.com/SillyTavern/SillyTavern/blob/release/public/script.js#L6786)

建议交互：

- 角色消息存在多个版本时显示 `‹ 2 / 4 ›`，并提供版本列表；版本正文允许查看、复制。
- 对末条角色消息选择“使用此版本”后，后续发送和续写以所选版本作为上下文。
- 重新生成将结果追加为新版本，先前候选继续保留。
- 对已有后续消息的历史回复，先独立预览版本；“从此版本继续”创建新会话，保留原会话及其后续消息。
- 仅浏览历史候选不改变原会话的有效上下文。避免页面正在显示的候选与实际继续生成所用的版本含义不清。

例如：同一问题生成候选 A、B，选 A 后继续得到 C。此后查看 B 时仍保留 A→C；从 B 继续会新建截至 B 的分支，C 不进入这个分支的 Prompt。

## 建议的数据与提交方式

### 消息与候选分开建模

建议新增单聊回复候选实体，由 ChatRepository 管理，不把全部候选混入普通消息列表。

- 普通消息继续承担会话位置与图片关联，保留稳定的消息 ID 和创建时间。
- 候选保存自身 ID、所属消息 ID、排序、Source 正则处理后的正文、生成时间，以及完整或部分结果的状态。
- 当前版本应有明确的标识；旧数据迁移为一个已有版本，不能恢复升级前已经覆盖或删除的内容。
- 若保留 ChatMessage.content 供现有查询和 Prompt 使用，它只是当前候选的投影。保存候选、选择当前候选和更新投影必须在同一事务内完成。
- 编辑、重新应用 Source Regex、导入、删除、分支复制都应经过同一套版本规则，避免候选正文与当前投影分叉。建议编辑另存为新版本，保留原候选。
- 版本 ID 和版本顺序不应改变消息本身的会话顺序、分页游标或消息数量。

历史续聊首先复用现有会话前缀复制，不要求每条消息增加 parentId 或先建设完整 DAG。若之后要在同一会话内自动恢复每个候选对应的整段后续对话，再评估树形历史；这超出本次最小范围。

### 成功、失败与取消

- 请求前保留当前候选，不提前覆盖或删除它。
- 新版本接收与当前版本切换采用原子提交；目标版本已经被编辑、删除或会话已变化时，拒绝迟到结果覆盖现状。
- 空响应和首个正文前取消不产生空候选，不推进世界书状态。
- 单聊目前保留流式部分正文。接入版本后可继续将其保存为“部分结果”候选，同时保留原版；是否自动选中部分结果需要统一产品规则。
- 群聊设置页目前只接受完整重生成结果。扩展到群聊时应明确是否保留这种差异，不能由不同入口偶然决定。

### 摘要、世界书与 Prompt

这部分是实际风险较高的集成点，不能只给消息气泡增加左右箭头。

- Prompt、摘要和世界书扫描只读取有效版本；其余候选作为本地历史，不自动全部发送给模型。
- 切换有效版本时，覆盖该消息的旧摘要必须失效。当前以消息 ID 表达摘要覆盖范围，同一个消息 ID 换正文同样需要失效处理。
- 新建历史分支时，应仅复制在分支边界仍有效的摘要；若选择的候选与摘要所依据的正文不同，应丢弃该摘要或回退到更早快照，不能直接复用现有分支函数的最终结果而不校验版本。
- 世界书 Sticky/Cooldown 属于会话运行时状态。纯浏览不能推进时序；末条版本切换应恢复相应状态，建议记录该位置的生成前快照与各候选生成后的快照。
- 对迁移前消息，没有快照时无法保证精确还原世界书状态。历史续聊首期可沿用现有分支重置为 `{}` 的语义，但必须把它视为重新开始时序，而不是精确时间回退。
- Display Regex 在展示候选时运行，Prompt Regex 在构建请求时运行；已有候选不应因浏览或切换而再次执行 Source Regex。

## 涉及模块与交付顺序

| 范围 | 主要改动 |
| --- | --- |
| Room | 候选 Entity、DAO、当前候选标识、索引、级联清理与数据库迁移；当前数据库 version 为 6，实施时以实际最新版本递增 |
| Repository | 候选追加、切换、编辑、目标一致性检查、摘要失效、世界书状态与会话元数据原子提交 |
| 单聊生成 | 调整 GenerationOutput.Update 及流式收尾，让重新生成追加版本 |
| 单聊界面 | 消息版本摘要状态、按需加载的版本列表、预览、选择和从版本建立分支的 Intent |
| Prompt 与摘要 | 保持当前有效正文查询；补充版本变更和分支边界的摘要一致性规则 |
| 归档和备份 | ChatArchiveMessage、ChatArchiveCodec、ChatArchiveRepository、PortableBackupV3 的往返保真与主键重映射 |
| 群聊后续范围 | 统一两种重新生成入口；保留发言者、批次、附件及候选的归属，决定历史续聊方式 |
| 本地化 | 按项目规范补齐默认英文与所有已支持语言的新增文案 |

建议按以下阶段推进：

- 首期交付完整单聊闭环：自动保存候选、回看复制、末条选择、从历史候选建立独立分支，并一起完成迁移及导入导出、完整备份的版本保留。
- 随后处理群聊：先明确消息菜单“重生成中间消息”对后续历史的处理，再接入同样的候选与安全提交规则。当前先删历史的实现是独立可确认的丢失风险。
- 同一会话内展示整棵分支树、自动恢复各候选的后续对话、一次并发生成多个候选，都需要额外需求确认，不从该 Issue 自动推导。

规模判断：单聊完整闭环属于中等规模功能，会跨数据、生成、界面和归档层；若包含群聊与完整树形历史，范围明显增大。尚未实现原型或测量工时，不给出未经验证的日期承诺。

## 建议验收与验证

- 同一回复生成 A、B、C 后，重启应用仍能查看和复制全部版本。
- 选择 B 后发送消息，Prompt Inspector 与实际请求只使用 B，不混入 A、C。
- 已有后续对话时，从历史版本建立分支；原会话完整保留，新会话没有原路径的后续消息。
- 成功、空结果、网络失败、首个正文前停止、收到部分正文后停止，都不会使旧版本不可恢复。
- 切换版本和新建分支后，不复用包含另一版本正文的摘要；世界书时序遵守已声明的恢复或重置规则。
- 旧数据库升级不丢原消息、摘要、图片和排序；旧消息作为唯一已有版本出现。
- 单聊 JSONL、SillyTavern swipes 与完整备份能够往返保留候选和当前选择；旧格式缺省字段仍能导入。
- 候选数量不干扰普通历史分页、全历史深度、Prompt 预算和图片生命周期。
- 针对版本事务回滚、迟到结果、摘要边界和归档兼容补充有业务价值的测试；单纯按钮或字段映射不需要仪式性单测。

现有测试可作为后续回归基础：

- [ChatRepositoryGenerationCommitTest](../app/src/androidTest/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepositoryGenerationCommitTest.kt)：接受部分正文时，正文、摘要失效与会话状态一起提交。
- [ChatRepositorySummaryTest](../app/src/androidTest/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepositorySummaryTest.kt)：重新生成排除旧正文时的历史窗口与摘要回退。
- [GroupChatRepositorySummaryTest](../app/src/androidTest/java/me/kafuuneko/rpclient/libs/room/repository/GroupChatRepositorySummaryTest.kt)：末条替换时保留原文直至成功，并拒绝迟到或失配的结果。
- [ChatArchiveCodecTest](../app/src/test/java/me/kafuuneko/rpclient/libs/chat/ChatArchiveCodecTest.kt) 与 [ChatArchiveRepositoryTest](../app/src/androidTest/java/me/kafuuneko/rpclient/libs/chat/ChatArchiveRepositoryTest.kt)：JSONL、摘要和图片分支兼容。

本次仅完成线上 Issue 与官方参考资料查阅、代码及现有测试阅读，并产出本调研文档；未修改业务代码，未运行构建、单元测试或设备测试，也未向 GitHub 发布评论。
