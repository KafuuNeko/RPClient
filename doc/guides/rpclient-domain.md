# RPClient 领域规范

本文档说明 RPClient 与角色卡、世界书、Prompt、Regex、LLM 请求和本地日志相关的领域边界。涉及这些能力时必须阅读。

---

## 1. 角色卡

1. 角色卡导入导出逻辑放在 `libs/character/` 或 Repository，ViewModel 只协调用户动作和状态。
2. Character Card V1/V2、PNG metadata 和 SillyTavern 扩展字段必须保留兼容性注释或测试。
3. 导入前保留原始文件由用户负责，应用内保存时只持久化本项目需要的字段和可恢复扩展。
4. 头像文件保存失败时不得提前提交不可恢复的角色记录。
5. 导出内容可能包含用户创作和私密设定，文件名和错误提示不要泄露不必要内容。

---

## 2. 世界书

1. 世界书实体、条目和会话/角色关联通过 Repository 访问，不在 Compose 直接读写。
2. 触发逻辑集中在 `WorldBookActivator`，不要在 feature 内临时复刻 key 扫描。
3. 修改 key、filter、概率、优先级、递归、sticky/cooldown、depth、budget 等行为时必须补充或更新单元测试。
4. 世界书激活只决定候选内容，最终进入请求仍受 Prompt 预算和协议后处理影响。
5. 会话级条目开关属于当前会话设置，不应误写为全局世界书状态。

---

## 3. Prompt 构建

1. Prompt 构建集中在 `libs/prompt/` 和 `libs/groupchat/`，ViewModel 不手写最终 messages。
2. 宏展开、角色定义、世界书注入、历史消息、摘要、Regex Prompt 模式和后处理顺序必须可追踪。
3. 预算裁剪要记录 omitted 信息，供 Prompt Inspector 展示。
4. 协议后处理不能破坏检查信息和实际请求的一致性；如必须不同，应明确注释。
5. 修改 `PromptRequestFinalizer`、`ChatPromptBuilder`、`GroupChatPromptBuilder`、`SummaryPromptBuilder` 时优先运行相关 prompt 单元测试。

---

## 4. LLM Provider 与请求

1. RPClient 不提供模型服务，只把请求发送到用户配置的 Provider。
2. API Key、请求头和 baseUrl 只在必要层级读取，不进入普通 UiState、Toast 或非脱敏日志。
3. OpenAI Compatible、Gemini、Anthropic 等协议适配放在 `libs/llm/`，不要在 feature 内分叉实现。
4. 流式响应要支持停止生成，并明确 partial 内容是否落库。
5. 错误提示应可理解但脱敏，不展示完整请求体、完整响应体或堆栈。
6. Request Log 是调试功能，新增字段时必须确认是否需要脱敏或开关控制。

---

## 5. Regex 脚本

1. Regex 脚本解析、存储和运行放在 `libs/regex/`，不要在聊天页面直接处理脚本 JSON。
2. Global、Preset、Character 作用域必须明确，角色卡内嵌脚本默认不应自动获得执行授权。
3. Source、Markdown、Prompt 三种执行模式的输入输出边界必须可测试。
4. 脚本排序、启用状态、复制、导入导出属于业务状态，持久化必须通过 Repository。
5. Regex 执行可能改变模型可见内容，涉及 Prompt 模式时必须检查 Prompt Inspector 输出。

---

## 6. 群聊与摘要

1. 群聊成员、发言者选择、自动模式、禁言、摘要和输出清理放在 `libs/groupchat/` 或 `GroupChatRepository`。
2. 群聊 Prompt 构建不能复用单聊 builder 后再临时拼接；共享逻辑应下沉到可测试的 builder/helper。
3. 摘要生成是长任务，启动前检查生成中状态，避免与普通回复并发写入同一会话。
4. 自动摘要暂停、恢复和手动摘要需要清楚记录到对应会话或群聊状态。

---

## 7. 导入、导出与隐私

1. 系统文件选择器和导出 launcher 通过 ViewEvent 交给 Activity。
2. ViewModel 接收选择结果后调用 Repository/Codec 完成解析、保存和错误处理。
3. 导入失败必须避免留下半成品数据库记录或孤立文件。
4. 导出必须由用户明确触发，导出内容可能包含私密设定和对话。
5. Issue、日志、Toast 和异常消息中不要公开 API Key、私密对话、未经授权的角色资源和完整请求内容。

---

## 8. 测试优先级

以下变更优先补单元测试：

1. Character Card、Lorebook、Regex 的导入导出兼容。
2. 世界书激活、递归、预算、sticky/cooldown。
3. Prompt 构建、宏展开、协议后处理、Prompt Inspector omitted 记录。
4. LLM 请求参数、默认 Provider 模板、错误解析。
5. 群聊发言者选择、输出清理、摘要 Prompt。
