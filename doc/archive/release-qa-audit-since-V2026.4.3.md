# V2026.4.3 之后的发布前 QA 审计

**修复复测结论（2026-11-17）**　本次审计列出的四项缺陷已在当前工作区修复并通过回归。混淆 Release 使用原始样本通过文件导入复测，428 项 JVM 单测及 238 项不同的设备用例通过。候选仍为开发版本。详见[修复方案与复测](#修复方案与复测)。

**首次审计结论（提交 `851f96d4`）：暂缓发布。** 该提交的混淆 Release 包在世界书管理页导入一个符合大小限制的 ZIP 时发生进程崩溃。另有两项可复现的功能缺陷，以及一项使三条设备回归用例无法完成的首帧问题。

审计基准为 `V2026.4.3`（`b99a22f5`），候选为 `develop` 的 `851f96d4`，共 34 个提交、321 个变更文件。候选版本为 `2026.4.4-Dev`（`20260404`）。开始检查时工作区干净。复核覆盖 Room 与跨存储迁移、备份恢复、人设和归档、世界书导入导出、单聊与群聊生成、宏与图片请求、页面加载、资源及仓库交付规范。

## 首次审计的发布阻断及功能缺陷

### P1 · 在限额内导入世界书 ZIP 可使 Release 进程崩溃

- **复现方式**　在世界书管理页导入一个含四个 JSON 的 ZIP。每个 JSON 的 `entries` 含 600,000 个不同数字键，每项的值为 `{}`。实测 ZIP 为 5,732,758 字节，每个 JSON 为 7,088,921 字节，合计 28,355,684 字节，均低于当前的 64 MiB 压缩包、8 MiB 单文件、32 MiB 累计限制。归档中只有四个文件。
- **实际结果**　Android API 36、192 MiB 应用堆上，经过系统文件选择器导入，R8 Release 进程抛出 `OutOfMemoryError` 并退出。用当前构建的 mapping 还原后，调用链为 `LorebookArchiveReader.readEntries:140 → LorebookCodec.parseLorebook:40 → LorebookCodec.toLorebookEntry:128`。另一个约 912 KiB 的单文件 ZIP 在有限堆的 JVM 和 Android ART 探针中也复现了内存耗尽；该小样本在实际 Release 界面成功导入。
- **原因及验收**　当前检查限制归档文件数和字节数。解析器把 JSON 建树、转回字符串，再建树并创建每条记录的实体；前面已解析的世界书也一直驻留内存。应在对象大量分配前约束每本和整批的条目数或内存消耗，减少重复建树。用上述样本从 Release 界面复测，应能完成受控处理，应用持续可用。[归档读取](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/character/LorebookArchiveReader.kt#L123) · [条目实例化](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/character/LorebookCodec.kt#L40)

### P2 · 应用成功导出的单本世界书可能无法重新导入

单本 JSON 导出没有 8 MiB 限制，导入却经过与 ZIP 条目相同的限长读取。用生产编解码器导出一部 132 条、每条正文含 65,536 个 ASCII 字符的世界书，得到 8,743,847 字节。编解码器可完整读回 132 条，当前文件导入入口抛出 `EntryLimitException`。基准版本的单本入口没有这个 8 MiB 截断。修复时需保留大文件的受控读取，同时验证已导出的历史单本文件能够恢复。[单本导出](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/LorebookRepository.kt#L316) · [单本导入](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/character/LorebookArchiveReader.kt#L59)

### P2 · 切换图片画质会改变高级请求补丁的语义

对 Responses 基础请求 `{"reasoning":{"summary":"auto"}}` 应用补丁 `{"reasoning":{"summary":null}}`，合并结果为 `{"reasoning":{}}`。仅将图片画质设为 High，再次走同一个请求合并器，结果变为 `{"reasoning":{"summary":"auto"}}`。新画质写回函数使用的 Gson 序列化器省略了对象中的显式 `null`，丢失了用户删除字段的指令。既有表单格式化路径也使用相同的序列化策略。验收应覆盖打开已有配置、切换画质和保存，再比较实际请求体；画质以外的补丁语义应保持一致。[画质写回](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/llm/adapter/ImageInputPreferences.kt#L77) · [补丁合并](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/llm/adapter/RequestBodyPatch.kt#L169) · [表单格式化](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/utils/GsonUtils.kt#L36)

### P2 · 首帧拦截使三条搜索导航设备用例挂起

`ContentSearchFlowTest` 中的单聊历史定位、群聊历史定位、首页搜索跳转旧消息三项，在当前提交分开运行均超过 48 秒并由审计驱动终止。首次整套运行也停在单聊定位，线程栈显示测试停在 `Instrumentation.startActivitySync`，当时 `ChatActivity.mIsFirstFrameReady` 为 false。纯搜索列表分页用例通过。

同一测试 APK 在父提交 `77ab96ac` 上四项全过，耗时 15.21 秒。在当前源码的临时副本中，仅移除单聊和群聊注册 `deferFirstDrawUntil` 的两处调用，四项也全部通过，耗时 15.42 秒。实际 Release 的普通单聊入口能显示升级前消息。需修复首帧等待与测试启动流程的相互依赖，并同时回归历史定位和带背景页面的首次显示。[首帧监听](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/core/ActivityFirstDraw.kt#L21) · [单聊入口](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatActivity.kt#L83) · [群聊入口](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatActivity.kt#L79)

## 规范核查

- **页面分层**　新建的 `UserPersonaPicker` 直接导入 Room Entity，以 `UserPersona.NONE` 判断并发送选择。项目要求 Compose 只依赖 UI 或领域投影；可由 `PersonaSelection` 暴露这一选择的语义。[选择器](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/ui/widgets/UserPersonaPicker.kt#L68) · [Room 依赖规范](../guides/room-data-layer.md)
- **公开接口文档**　例如新增的 `PromptPresetRepository.create` 有功能说明，但缺少项目要求的参数和返回值说明。当前编译器还对预设迁移的两处异构 `arrayOf` 报告隐式交集类型警告。[预设创建](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/PromptPresetRepository.kt#L55) · [迁移绑定参数](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/app/src/main/java/me/kafuuneko/rpclient/libs/upgrade/PromptPresetMigration.kt#L37)
- **文档可达性**　中英文 README 的 Responses 接入说明，以及 V2026.4.3 更新说明的备份恢复指南，仍指向移动前的位置。备份指南关系到用户能否在升级前找到恢复步骤。[中文 README](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/README_ZH.md#L80) · [英文 README](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/README.md#L80) · [更新说明](../updates/V2026.4.3.md#L64)
- **设备元数据**　已跟踪的 Android Studio 部署目标记录了开发真机的 ADB 序列号。共享配置应移除本机标识。[部署目标配置](https://github.com/KafuuNeko/RPClient/blob/851f96d4c506c3f6e8cf39c27c4a01178900b329/.idea/deploymentTargetSelector.xml#L10)

## 验证记录

| 检查 | 当前提交的结果 |
|---|---|
| Android 构建 | 离线 `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:assembleRelease` 成功，Release 启用 R8 和资源压缩 |
| JVM 单测 | 425 项通过，零失败、零跳过 |
| Android 设备测试 | 237 项中 234 项通过，3 项搜索导航超时。按用例去重；父提交和临时副本的通过数未计入 |
| 显式设备场景 | 三项默认跳过的用例另行执行通过，包含 10 MiB 搜索基准、混合读写基准、索引构建中强杀进程再恢复。图片与对话集成测试 10 项全部通过 |
| 版本覆盖升级 | 从标签与候选源码分别构建 Release 并用同一开发密钥签名。旧包导入合成角色并建立会话，覆盖安装后首页、单聊页均读到了旧角色、会话标题和原消息 |
| Kotlin 格式 | 使用项目锁定的 Android Studio，执行 `formatCheck -PformatBase=V2026.4.3`。278 个文件全部通过；`git diff --check V2026.4.3..HEAD` 通过 |
| 本地化 | 12 个译文目录均含默认资源的 1,455 个可翻译字符串键。此次新增或修改的 120 个英文字符串，已核对各译文的格式占位符 |
| Release Lint | 标签和候选均为零错误。警告从 230 条增至 326 条，净增 96 条，其中未使用资源从 120 条增至 196 条，复数候选从 41 条增至 46 条 |

以上验证使用隔离的 API 36 arm64 模拟器、合成数据及本地模拟模型服务。尚未实测最低支持版本 API 26、真实模型端点和正式分发签名。候选仍使用开发版版本名；仓库发布工作流还会检查正式标签与版本名一致，并检查标签提交属于 `master`。[版本配置](../../app/build.gradle.kts#L39) · [发布流程](../../.github/workflows/release.yml)

首次审计建议从混淆 Release 的系统文件选择器复测两种世界书样本，比较画质切换前后的实际请求体，再在候选原始代码上跑通全部 237 项设备用例。


## 修复方案与复测

- 世界书文件先在应用私有缓存按实际 UTF-8 字节计量，再流式检查结构并逐条解码。独立 JSON 使用原有整批 32 MiB 上限，ZIP 仍为单成员 8 MiB、累计 32 MiB。文件选择器单次导入每本最多 16,384 条、整批最多 32,768 条。单个字段或条目另有结构深度、节点和字符预算，避免少量大扩展绕过条目数限制。对根清单识别也执行深度检查。单本超限可跳过，ZIP 累计超限会拒绝整包。Gson 支持将流式遍历与单个值的对象映射结合使用。[Gson 流式读取指南](https://github.com/google/gson/blob/main/UserGuide.md#streaming)
- 单本及 ZIP 导出在同一数据库读取事务内分页编码，完整暂存并通过相同的导入检查后才打开目标文档。单本超过 ZIP 成员限额时仍可独立导出。完整备份继续使用其原有的预算策略。
- 模型表单、画质和 OpenRouter 路由快捷设置共用保留显式空值的 JSON 序列化器。回归用例从数据库实体投影为表单，修改设置、保存，再比对实际请求合并器产出的 JSON。对象字段上的 `null` 会删除基础请求中的对应字段，数组里的 `null` 保留。[RFC 7396](https://www.rfc-editor.org/rfc/rfc7396)
- 单聊、群聊给已缓存背景预留 200 毫秒的首帧过渡时间。背景提前就绪时直接绘制；超过时限由 Android 主线程放行，继续用当前主题显示加载页。加载状态和正常页面仍订阅同一个页面背景。200 毫秒是本项目的过渡取值。[Android 启动画面的首帧指导](https://developer.android.com/develop/ui/views/launch/splash-screen#suspend-drawing)
- 人设选择器改用界面投影提供的 ID，公开预设接口补齐 KDoc，迁移 SQL 显式声明异构绑定参数。修复中英文 README 与历史更新说明中的文档路径，移除共享的本机部署目标并加入忽略规则。
- 针对相对标签新增的 Lint 告警，删除已核对引用的 81 个未使用字符串及译文，为五项数量文案采用 Android 复数资源，并区分 Gemini 的两个版本号。升级代码保留同步提交与返回值检查，因为写入失败时必须保留迁移检查点。

### 修复后的实测结果

| 检查 | 结果 |
|---|---|
| 原始崩溃 ZIP | API 36、192 MiB 应用堆、开启 R8 的 Release，通过系统文件选择器导入原 5,732,758 字节样本。界面报告成功 0 本、失败 4 本。随后继续操作，应用进程 PID 保持一致。 |
| 历史独立 JSON | 使用修复前 `851f96d4` 的编解码器重新生成 8,743,847 字节的导出文件，在同一 Release 进程从系统文件选择器导入。世界书列表显示 `Probe` 和 132 条。仓库设备回归另行核对了 132 条正文，并验证 ZIP 超限时原目标内容仍可读取。 |
| 请求补丁 | 使用保存后的表单配置调用生产请求合并器。高画质、恢复自动画质及 OpenRouter 路由设置均保留嵌套字段删除指令、相邻字段和数组空值。 |
| 构建与静态检查 | 离线 Debug、设备测试 APK、R8 Release 构建成功。JVM 428 项全部通过。26 个改动的 Kotlin 文件均经指定 Android Studio 执行 `formatApply` 和 `formatCheck`，`git diff --check HEAD` 通过。 |
| Release Lint | 零错误、209 条警告。修复前为 326 条，基准标签为 230 条。 |
| 本地化 | 五组数量文案在默认资源及全部 12 个译文目录有对应的复数资源，各数量分支占位符与英文源一致。 |
| Android 设备 | 在干净的 API 36 模拟器应用数据上，全套执行 238 项，237 项直接通过。默认需要外部强杀的恢复用例随后按准备、强杀、新进程验证执行通过。去重后 238 项全部通过，含单聊与群聊历史定位、首页跳转旧消息、图片会话集成及 10 MiB 搜索基准。 |

正式分发签名、真实模型端点和最低支持版本 API 26 尚未实测。两种文件样本使用开发签名的混淆 Release 验证，设备自动化使用 Debug 包与合成数据。
