# V2026.2.3 之后的发布前 QA 审计

## 再次发布前 QA（2026-09-19）

**结论：原 P1 与统计页 P2 已关闭；未发现新的 P0/P1，新增一项已复现的 P2 功能缺陷，当前不能标记为全部通过。** 导入单条消息携带五张及以上图片后，保留附件、仅编辑正文也无法保存。该问题与维护者明确不再要求处理的低优先级规范遗留项分开记录，尚未视为已接受。

### 本轮范围与基准

- 当前提交：`f18950787cf0792c5afa2392956fad238e4461ed`；开始审计时工作区干净。
- 对比基准：`V2026.2.3`，累计 67 个提交、372 个文件；在初审基础上重点复核 `4fea66f` 的附件链路修改与 `f189507` 的统计页拆分，并重新执行全量现有测试。
- 版本仍为 `versionName=2026.2.3`、`versionCode=20260203`，符合维护者暂不迭代版本的要求。本轮没有修改生产代码、版本或数据库结构。
- 离线 Gradle 构建；Pixel_10a 模拟器，Android 17 / API 37、arm64、16 KB 页。Release 开启 R8 与资源压缩，用本地测试密钥签名后安装。
- 仅使用合成数据、本地模拟服务端和审计专用应用数据；没有调用真实付费模型端点。

### 新发现：P2 — 导入超过四张图片的消息无法保存编辑

**状态：已通过三个真实 Room/归档探针和实际 R8 Release 界面确认，未修复。**

归档解析允许每条消息最多 256 张图片，界面也能展示折叠后的多图。但普通消息的编辑入口将“本地新增消息最多四张”的限制应用于所有保留附件，形成不一致：即使用户没有添加图片，只修改正文或调整原图顺序，提交仍被拒绝。用户、角色、旁白均可受影响；最近统一附件保存链路后，角色与旁白也进入这条数量校验。

| 位置 | 证据 |
| --- | --- |
| `libs/chat/ChatArchiveImageCodec.kt:18`、`:39` | 归档图片上限为 256，允许导入超过四张图片的消息 |
| `libs/room/repository/ChatRepository.kt:130` | 编辑保存对全部附件直接校验不超过四张 |
| `libs/room/repository/MessageImageRepository.kt:41` | 共享附件替换事务再次执行相同限制 |
| `feature/chat/ChatViewModel.kt:1294` | 非摘要消息统一进入图文编辑仓库，因此覆盖角色和旁白 |

以上路径相对 `app/src/main/java/me/kafuuneko/rpclient/`。群聊共享仓库也有相同数量约束，但本轮实际归档探针与五图界面复现针对单聊，没有把群聊归档导入作为已测流程。

复现方式：

- 用真实 `ChatArchiveRepository.saveImport` 导入一条含五个有效 PNG 附件的消息，分别采用 User、Character、Narrator 来源；导入与读取均成功，附件数量为五。
- 原样传回五个已有 UUID，仅把正文从 `before` 改为 `after`。
- 三种来源均抛出 `IllegalArgumentException: A message can contain at most four images`，正文未保存。
- 实际 R8 Release 的五图角色消息中修改正文并点击保存，仍停留在编辑状态；取消后原正文和附件保留，独立数据库核验通过，未发生进程崩溃。

影响是导入内容无法在保留全部图片的情况下正常编辑。当前若想保存，必须先把附件删到四张以内，这不应成为仅改正文的前提。建议将“新增图片数量限制”与“合法既有附件的保留、重排、删减”区分处理，同时保留 UUID 所有权和会话归属校验。此处只提出修复方向，本轮未实施。

### 本轮执行结果

| 验证 | 结果 |
| --- | --- |
| Debug 构建 | 通过 |
| R8 Release 与资源压缩构建 | 通过 |
| JVM 单元测试 | 279 项，零失败、零错误、零跳过 |
| 完整 Android 设备测试 | 100 项，零失败、零错误、零跳过 |
| 补充边界探针 | 8 项：5 项通过、3 项失败；失败均为上述五图编辑问题 |
| schema 1、2、3 直接迁移到 4 | 三项补充探针均通过，验证旧业务表数据保留 |
| V2026.2.3 Release 覆盖安装到当前 Release | 通过；19 张旧业务表原有行列、偏好哨兵、头像文件及外键完整性保留 |
| Lint | 零错误、196 项既有警告 |
| 本地化结构检查 | 1,182 个可翻译键、12 个翻译目录，未发现缺键或编号占位符不匹配 |
| `V2026.2.3..HEAD` 空白检查 | 仍有初审列出的六处，按维护者决定保留 |
| 本轮文档改动空白检查 | 通过 |

补充测试使用临时 sourceSet 注入，未把预期暴露缺陷的失败探针混入仓库测试集；完整 100 项与补充 8 项结果分别保存，未把补充失败隐藏在全量通过结论中。

### 实际 Release 验证

- 旧包建立包含角色、会话、群聊、世界书、故事、模型等数据的 schema 3 样本，记录快照后直接覆盖安装当前包。核验通过后才添加本轮界面用的图片和统计数据，避免把新夹具混入升级保留判断。
- 分支包含用户图片、五张角色图片和一张旁白图片，共七张。通过实际界面成功创建分支；独立核验确认七个附件 UUID 均独立、三种来源保持不变、共享文件可读、外键正常。
- 单聊旁白图片显示与已有附件保存正常。群聊角色、旁白图片显示正常，角色已有附件可保存。角色及旁白编辑区没有新增图片按钮；底部用户输入区仍保留图片入口。
- 统计页今日显示一条请求、总量 1.25K；七日显示七条、总量 8.75K，输入输出为 80%/20%。切换柱状图和趋势图、选择日期、查看分组排行及展开请求详情均可操作。
- 统计详情正确分别展示输入 `Reported` 与输出 `Proxy estimate` 来源，以及测试分词器名称。清空确认取消后记录保留；确认删除合成统计数据后总量归零、空态显示、清空入口隐藏。
- 保存并检查关键界面截图；本轮设备 `AndroidRuntime:E` 日志未发现崩溃记录。没有将单一设备上的冒烟测试扩展宣称为所有分辨率或系统版本均通过。

### 已接受的遗留与覆盖边界

维护者本轮明确要求不再处理其余已知低优先级问题，因此克隆名称固定英文 `Copy` 和六处空白格式问题保留，作为已接受的规范遗留记录，不再单独要求整改。新发现的五图编辑问题属于功能兼容性缺陷，不属于此前已接受的范围。

本轮未验证 Android 8 / API 26 真机、其他厂商系统、低内存或进程强杀恢复、全部历史 APK、生产签名分发与真实模型供应商端点。模型协议是否支持 Assistant/System 图片仍由现有协议检查和服务端能力决定；存储与导入成功不等于真实服务端能够接受这些图片。

发布前需要修复或明确接受上述新增 P2，并针对最终代码执行对应回归。本轮已覆盖范围内没有其他已确认的严重问题；不作“绝对没有 bug”或“所有旧版场景均兼容”的承诺。

### 本轮证据

- 全量构建与测试日志：`/tmp/rpclient-qa2-build.log`、`/tmp/rpclient-qa2-device.log`。
- 补充探针源码与日志：`/tmp/rpclient-qa2-probes/`、`/tmp/rpclient-qa2-probes.init.gradle`、`/tmp/rpclient-qa2-probes.log`。
- 独立 XML、Lint、本地化核验、升级与分支核验及截图：`/tmp/rpclient-qa2-evidence/`；其中 `unit/`、`device-100/`、`probes-8/` 分别保存各轮结果。
- Release 验证工具源码：`/tmp/rpclient-qa2-harness/`。
- 当前测试签名 Release APK：`/tmp/rpclient-qa2-release.apk`，SHA-256：`0ca5e97280850aa18ddf8e287754084670a59832eb26af7297436c13991bfd7a`。
- 临时证据可能被系统清理；主要结论、失败原因与复现步骤已完整记录于本报告。测试结束后清理审计专用应用与界面转储文件，不修改模拟器分辨率或密度。

以下为前轮整改与初审历史记录；涉及状态、测试数字或发布判断时，以上方本轮复审结论为准。

## P2 用量统计页整改跟进（2026-09-18）

**用量统计页大函数与内部注释问题已处理，改动基于 `4fea66f`。** `TokenUsageLayout.kt` 按列表组织、趋势与选点提示、总览指标、模型排行、请求摘要与详情拆分私有 Compose 组件。仅调整展示层组织，保留原有列表 item 边界和键、展开状态、过滤条件、图表 Intent、尺寸、颜色及动画配置。

- `NormalView` 由 146 行缩短至 29 行，`HeroSummaryCard` 由 125 行缩短至 25 行，`RecentRequestCard` 由 189 行缩短至 36 行；同文件模型排行卡片也已拆分。
- 该文件所有函数均不超过 80 行；超过 16 行的函数均有内部注释，说明空态、可选指标、计数来源或状态归属等约束。新增组件补充中文 KDoc。
- `:app:assembleDebug`、`:app:lintDebug` 与 `git diff --check` 通过；Lint 零错误、196 项既有警告，未报告该布局文件的问题。构建日志：`/tmp/rpclient-tokenusage-refactor.log`。
- 本次属于展示层职责拆分，未新增测试，未重跑设备测试或 Release 构建；本地化与六处历史空白问题仍未处理。

## P1 修复跟进（2026-09-18）

**本轮已修复并验证 P1，代码位于基于 `3db3454` 的当前工作区。** 单聊和群聊的角色消息在底层与用户消息共用图文事务；角色来源及群聊发言者快照保持不变。根据后续产品要求，仅用户消息编辑界面开放添加图片入口；角色及导入旁白保留已有附件的展示、移除、排序和保存，以及图片导入与分支复制能力。

- 分支复制移除了仅限用户消息的限制。既有角色或导入旁白附件均可复制；保留消息存在、会话归属和非摘要消息约束。
- 每个分支附件继续创建独立 UUID，仅共享 hash 对应文件；新增测试覆盖用户、角色、旁白三种归档来源，以及先删除原会话或先删除分支的两种顺序。
- 附件编辑仍校验既有 UUID 属于当前消息、单消息图片数量、非空图文及文件事务；系统消息没有新增图片编辑入口。
- 分支遇到文件或数据库异常时恢复页面并显示已有本地化失败提示，取消操作继续传播。
- 单元测试 279 项通过，最终完整设备测试 99 项通过，零失败、零跳过；Debug、R8 Release 构建通过；Lint 零错误、196 项既有警告。
- 新增真实 MVI 回归验证角色纯图消息、取消编辑保留图片、角色图片进入单聊和群聊后续 Prompt。三协议编码测试扩展到 User 与 Assistant、流式与非流式及文字、纯图、混合内容组合。
- 收起角色新增入口前，实际 R8 Release 界面通过系统图片选择器向单聊角色消息新增图片并保存，随后成功进入分支会话；群聊角色消息也成功新增图片。独立校验工具确认单聊两张图、群聊一张图、角色信息未变、分支 UUID 独立且文件可读。这些截图记录的是底层能力验证阶段，当时的角色新增入口已按后续要求收起。
- 再次执行 V2026.2.3 测试签名旧包覆盖安装，19 张旧业务表快照、偏好、头像及外键检查通过。未修改数据库 schema 或编译版本号。

首次新增流程测试有两项未通过：单聊断言错误地对重新序列化后的 JSON 搜索未转义斜杠，已改为解析图片 URL；群聊发生一次等待超时，增加等待阶段诊断后，六项定向流程测试与随后完整 99 项测试均通过，未再次复现。没有把初次失败运行记录为通过。

本轮证据：`/tmp/rpclient-p1-build.log`、`/tmp/rpclient-p1-final-tests.log`、`/tmp/rpclient-p1-evidence/`。最后一处包含单元与设备测试 XML、Release 操作截图和数据校验结果。测试结束后已清理临时主应用、校验工具与测试图库图片；未调整模拟器现有分辨率或密度设置。

后续入口调整仅修改单聊、群聊的添加按钮显示条件及单聊标题占位，底层、导入和分支逻辑保持不变。调整后 `:app:assembleDebug` 与 `git diff --check` 通过，构建日志为 `/tmp/rpclient-character-image-entry-build.log`；本次未重新执行设备测试或 Release 构建，上述完整测试与 Release 界面证据属于入口收起前的验证。MVI 测试仍直接触发预留能力并使用模拟服务端，不代表当前界面提供角色新增入口，也不代表真实供应商接受助手图片。

后续再次简化旁白附件处理：移除 `allowSystemMessage` 和普通消息角色白名单，单聊、群聊旁白与其他普通消息共用附件编辑事务；界面仍仅向用户消息提供添加入口。保留父消息、会话、附件所有权和摘要边界校验，协议层 System 图片校验不变。新增单聊及群聊旁白附件保存、保留引用和删除回收回归测试。本次 Debug 构建和模拟器上的 38 项定向设备测试全部通过，覆盖 `MessageImageLifecycleTest`、`ChatArchiveRepositoryTest`、`ImageConversationFlowTest`；`git diff --check` 通过，日志为 `/tmp/rpclient-narrator-qa.log`。本次未重新运行全量测试或 Release 构建。

**P1 修复阶段仅关闭 P1；P2 已由上方后续整改关闭，P3 与整体发布覆盖范围仍未重新放行。** 下文保留 `c745b0b` 初审记录，源代码行号、95 项原设备测试及临时五项探针结果均为修复前的历史证据。

## 初次审计结论（c745b0b）

**初审提交不建议发布。发现一项已通过补充测试和实际 Release 包共同复现的崩溃：包含导入角色图片的单聊创建分支时，附件归属校验抛出未捕获异常。** 此外，新增代码仍存在注释与函数规模不符合编码规范、克隆名称未本地化及少量空白格式问题。

现有测试全部通过，并不代表此次增量没有问题。现有 279 项 JVM 单元测试、95 项设备测试均通过；针对审计边界补充的 5 项设备测试中，4 项通过、1 项失败，失败项正是上述分支回归。

旧版升级方面，数据库 schema 1、2、3 直升 schema 4 的补充数据保留测试均通过；也完成了从 V2026.2.3 APK 覆盖安装到当前启用 R8 的 Release APK 的实际验证，未发现所构造历史业务数据、偏好或头像丢失。该结果有明确的样本和设备范围，不等同于所有旧版本、所有设备和所有外部服务均已验证。

| 审计目标 | 结论 |
| --- | --- |
| 符合 coding-guidelines.md，可读且可维护 | 部分满足；仍有需要处理的明确规范问题 |
| 功能正确、没有引入回归 | 不通过；导入图片与分支功能组合存在回归 |
| V2026.2.3 及更早版本升级兼容 | 已测迁移链及旧 APK 覆盖安装通过；未完成所有历史 APK 与设备组合测试 |
| 没有引入严重 bug | 不通过；已复现 Release 主线程崩溃 |
| 编译版本尚未迭代 | 符合本次审计约定，不作为问题，不修改版本号 |

初次审计仅执行验证、未修改生产代码；后续 P1 实施和验证情况见本文开头的修复跟进。

## 范围与环境

| 项目 | 内容 |
| --- | --- |
| 审计日期 | 2026-09-18，Asia/Shanghai |
| 基准 | `V2026.2.3`，`177137ada2e2b67d618ed8ba66be72610492625a` |
| 审计终点 | `c745b0b3dfc088fd9361c2d7b3466f1d6eaf8665` |
| Git 范围 | `V2026.2.3..HEAD`，64 个提交，含合并提交 |
| 变更规模 | 370 个文件，新增 35,374 行、删除 9,194 行；新增或修改的生产 Kotlin 文件 265 个 |
| 构建版本 | `versionName=2026.2.3`，`versionCode=20260203` |
| Android 配置 | minSdk 26、targetSdk 36；与基准一致 |
| 构建方式 | 本地 Gradle Wrapper，离线构建；Debug 与开启 R8、资源压缩的 Release |
| 设备 | 已启动的 Pixel_10a 模拟器，Android 17 / API 37，arm64，16 KB 内存页 |
| 测试数据与服务 | 合成数据、临时本地 HTTP 服务；未使用真实对话、账号或外部付费模型 |
| 工作区保护 | 保留审计前已有的 `.idea/deploymentTargetSelector.xml` 修改 |

审计依据为 [编码规范](../coding-guidelines.md) 及其分层、MVI、Intent、ViewModel/Compose、注释、Room、Koin/Kotpref、领域规范。方法包括增量清单与提交核对、通用静态扫描、重点链路代码审查、现有测试、补充回归探针，以及实际旧包升级和 Release 操作验证。没有将全部 370 个文件逐行人工复核或全功能穷举作为已经完成的工作。

重点审查了数据库与偏好迁移、R8 反射入口、图片文件事务与引用回收、选图及编辑取消竞态、三种 LLM 协议、Prompt 与摘要预算、分页状态、单聊归档、分支、故事批量查询、统计与本地化。

## 发现的问题

### P1：导入角色图片后创建分支导致应用崩溃

**初审状态：已确认，发布阻断。当前状态：已修复并验证，见本文开头的修复跟进。**

触发条件是单聊中存在导入的角色图片消息，且分支的截取范围包含该消息。分支按钮可以位于图片消息本身，也可以位于后面的消息；并不要求被点击的那条消息带图片。

| 位置 | 作用 |
| --- | --- |
| `app/src/main/java/me/kafuuneko/rpclient/libs/chat/ChatArchiveRepository.kt:288` | 导入明确支持角色消息携带图片，并直接恢复有序附件关系 |
| `app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/ChatRepository.kt:319` | 创建分支后复制所有被截取消息的图片 |
| `app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/MessageImageRepository.kt:86` | 复制附件复用了面向用户发送或编辑的 `validateOwner` |
| `app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/MessageImageRepository.kt:102` | 要求目标消息来源必须是 `User`，拒绝合法导入的 `Char` |
| `app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt:627` | 分支调用没有捕获该异常；仅处理返回值为零的失败情况 |

复现过程：

- 导入一份包含真实 PNG 的角色消息归档，导入成功，消息来源为 `Char`，图片正常存在。
- 对该消息或之后的消息执行“从此处分支”。
- 附件复制抛出 `IllegalArgumentException`，分支未创建。
- 界面调用链没有处理异常，Release 进程发生 `FATAL EXCEPTION: main`，聊天页退出。

设备补充测试 `branchImportedCharacterImagePreservesAttachment` 通过真实 `ChatArchiveRepository.saveImport` 建立数据后失败；相邻对照 `branchImportedUserImagePreservesAttachment` 通过，确认不是所有分支或所有图片都失败。

Release 界面验证使用独立测试工具写入与导入结果一致的角色附件关系，再点击正式界面中的分支按钮。2026-09-18 10:21:43 捕获到应用进程崩溃；用本次 Release 的 `mapping.txt` 还原后，关键堆栈为：

```text
java.lang.IllegalArgumentException: An image can only belong to a user message of the same session
    at MessageImageRepository.validateOwner(MessageImageRepository.kt:102)
    at MessageImageRepository.copyInTransaction$app(MessageImageRepository.kt:86)
    at ChatRepository$createBranchSession$2.invokeSuspend(ChatRepository.kt:319)
    at FileRepository$mutate$2$1$4$result$1.invokeSuspend(FileRepository.kt:321)
```

崩溃后核对了数据库：原会话、603 条普通消息和原图片关系仍在，外键检查通过，没有残留分支会话。当前证据表明事务回滚有效，**未观察到原历史数据丢失**。旁白消息也会被同一 `User` 限制拒绝，这是代码路径推导，尚未单独执行旁白复现。

建议修复：

- 将“用户新增或编辑附件”的来源限制与“复制已有合法附件”的约束分开；复制时验证源、目标消息及会话归属，保留角色，不把 `Char` 改写成 `User`。
- 保持独立附件 UUID、共享文件 hash、事务回滚与引用回收规则。
- 在分支操作边界处理可恢复失败，恢复页面状态并显示本地化错误；协程取消仍应继续传播。
- 增加导入 `Character`、`Narrator`、`User` 图片后分支的回归测试，同时验证删除原会话或分支后另一侧图片仍可读取。
- 修复后重新执行实际 R8 Release 的同一路径，而不仅是 Repository 测试。

### P2：新增统计页的大函数缺少规范要求的内部注释

**状态：已处理，详见上方 P2 整改跟进。以下保留初审发现及当时行号。**

`app/src/main/java/me/kafuuneko/rpclient/feature/tokenusage/ui/TokenUsageLayout.kt` 中的典型位置：

| 函数 | 初审行号 | 初审时含声明的行数 |
| --- | --- | --- |
| `NormalView` | 132–277 | 146 |
| `HeroSummaryCard` | 301–425 | 125 |
| `RecentRequestCard` | 596–784 | 189 |

初审时这些方法没有方法体内的分段注释。编码规范明确要求超过 16 行的方法必须写必要的内部注释，普通函数建议不超过 80 行。页面入口的类文档式说明不能替代这些方法内部的维护说明。

建议按独立视觉职责拆分统计摘要、输入输出占比、请求状态与明细组件，解释数据为空、统计来源、展开状态等非直观约束。无需为了补注释而逐行复述 Compose 调用，也无需改变功能行为。聊天、群聊和故事 ViewModel 仍然较大，后续宜围绕稳定职责继续提取；本次没有把单纯文件行数直接判定为功能缺陷。

### P3：模型配置克隆名称存在硬编码英文

**状态：已确认，本地化问题，尚未处理。**

`app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/LLMRepository.kt:129` 使用 `name = "${source.name} Copy"`。这是会展示给用户的配置名称，非英文环境也固定显示 `Copy`，违反用户可见文案资源化要求。

建议由具备本地化能力的上层通过带占位符的资源生成默认克隆名称，再交给 Repository 保存；同步补齐所有语言，不向数据层引入 Activity 或 Context。

### P3：增量存在空白格式问题

`git diff --check V2026.2.3..HEAD` 未通过，报告以下六处：

- `feature/tokenusage/TokenUsageViewModel.kt:262`：文件末尾新增空行。
- `feature/tokenusage/model/TokenUsageItems.kt:115`：文件末尾新增空行。
- `feature/tokenusage/presentation/TokenUsageUiState.kt:63`：文件末尾新增空行。
- `feature/worldbookentryedit/presentation/WorldBookEntryEditUiIntent.kt:80`：行末空白。
- `libs/room/model/LLMTokenUsageModels.kt:68`：文件末尾新增空行。
- `libs/room/repository/LLMTokenUsageRepository.kt:52`：文件末尾新增空行。

以上路径均相对 `app/src/main/java/me/kafuuneko/rpclient/`。它们不造成已知运行时故障，但应在规范收尾时清理。

## 已执行的验证

### 构建与现有自动化测试

```bash
./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease

./gradlew --offline --no-daemon --console=plain :app:connectedDebugAndroidTest
```

| 项目 | 实际结果 |
| --- | --- |
| Debug 编译与 APK 组装 | 通过 |
| Release 编译、R8 与资源压缩 | 通过；产物为 unsigned APK，安装验证时另行使用本地测试签名 |
| JVM 单元测试 | 42 个测试套件，279 项通过，零失败、零错误、零跳过 |
| 现有 Android 设备测试 | 95 项通过 |
| 额外边界探针 | 5 项执行，4 项通过，1 项真实回归失败 |
| Android Lint | 零错误、196 项警告 |
| 增量空白检查 | 失败，六处，见问题列表 |
| 本地化键及带编号占位符核对 | 英文 1,182 个可翻译字符串，12 个翻译目录；未发现缺键或带编号占位符不匹配 |
| 新增或修改 Kotlin 通配符导入扫描 | 未发现 |

现有设备测试覆盖的关键类别包括：

- Room 历史迁移及新增字段默认值，统计表不保存请求正文、响应正文或 API Key。
- 分页窗口、全量消息状态判断、摘要边界与生成结果提交。
- 文件采样、图片生命周期、共享 hash 引用、删除和取消处理。
- 单聊纯图片发送、失败重试复用同一用户消息、带图历史与摘要。
- 群聊图片轮流发言、编辑附件与小历史窗口；流式请求停止后保留已发送用户图片。
- OpenAI-compatible、Gemini、Anthropic 多模态请求编码与本地服务集成。
- 聊天归档、图片压缩与 hash 校验、损坏输入及引用处理。
- Regex 并发、历史升级逻辑、故事及世界书数据层。

这些类别表示测试覆盖到相关路径，不代表所有参数组合或外部厂商行为均已穷举。

### 补充探针

补充源码位于本机临时目录，通过 Gradle init script 加入 androidTest 源集，没有写入生产代码或修改构建配置。

| 探针 | 结果 |
| --- | --- |
| schema 1 直升 4，核对历史业务表原有字段及外键 | 通过 |
| schema 2 直升 4，核对历史业务表原有字段及外键 | 通过 |
| schema 3 直升 4，核对历史业务表原有字段及外键 | 通过 |
| 导入角色图片后分支并保留图片 | 失败，P1 的直接证据 |
| 导入用户图片后分支并保留图片 | 通过，对照组 |

各迁移探针按旧 schema 向业务表写入带关联的合成数据，执行 Room 完整 schema 校验，逐列比较升级前后的旧字段，检查行数和 `PRAGMA foreign_key_check`。schema 1 的旧请求日志表属于历史既定删除对象，没有把它作为应保留业务表；默认预算等历史有意转换由仓库现有迁移测试覆盖。

本机复现命令：

```bash
./gradlew --offline --no-daemon --console=plain \
  -I /tmp/rpclient-qa-probes.init.gradle \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.kafuuneko.rpclient.libs.chat.ReleaseAuditArchiveProbeTest,me.kafuuneko.rpclient.libs.room.ReleaseAuditMigrationProbeTest
```

该命令依赖本次临时探针文件。其他机器应将对应回归用例正式加入测试集后执行；不能把临时文件路径当作已经提交的仓库测试。

### 旧 APK 到当前 Release 的覆盖安装

- 从基准 Tag 导出独立临时源码并离线构建旧版 APK，不切换或覆盖当前工作区。
- 旧 Release 与当前 R8 Release 都使用同一把本地测试密钥签名，应用 ID 保持 `me.kafuuneko.rpclient`。
- 先安装旧包并建立 schema 3 数据：角色、真实头像文件、601 条单聊普通消息、群聊、摘要、世界书、Regex、故事卷章、关联关系、模型配置与用户偏好。
- 对全部 19 张旧业务表记录行数与原有字段快照，再用 `adb install -r` 覆盖安装当前包；版本号保持现值。
- 当前应用启动后 schema 为 4；所有快照中的旧字段和行数一致，头像文件存在，偏好哨兵值保留，外键检查为空。
- 首页和历史聊天正常展示；完成横屏布局冒烟检查。横屏截图不包含已展开的软键盘，因此不作为横屏输入法验证证据。
- 使用本地 OpenAI-compatible 模拟端点完成一次非流式发送，收到并显示回复，消息数从 601 增为 603。
- 用量页显示服务端返回的输入 123、输出 7、总量 130 和一次成功请求，与响应一致。
- 最后执行图片分支复现，捕获真实 Release 崩溃，并确认原数据回滚保留。

这验证了相同签名条件下的 APK 升级、数据迁移和混淆后运行路径。没有使用正式发布密钥，没有验证应用商店的签名、分包或分发流程，也没有将同版本号本地覆盖安装当作正式发布版本迭代。

验证结束后已卸载本次临时安装的主应用与独立校验工具，停止本地模拟服务并移除端口转发；模拟器保持启动，旋转设置恢复原值。

## 旧版兼容性评估

### 数据库

| 历史版本 | schema | 本次证据 |
| --- | --- | --- |
| V2026.1.1、V2026.1.2 | 1 | 现有历史迁移测试与补充 1→4 数据保留测试 |
| V2026.1.3 | 2 | 现有历史迁移测试与补充 2→4 数据保留测试 |
| V2026.2.1、V2026.2.2、V2026.2.3 | 3 | 现有 3→4 测试、补充逐表测试；V2026.2.3 另有真实 APK 覆盖安装 |

当前数据库版本为 4，保留 1→2→3→4 自动迁移链，没有新增破坏性重建回退。新增内容主要是用量记录、消息图片关系，以及 Provider 的服务端用量、本地估算器和图片能力设置。

3→4 对 Gemini、Anthropic 保持服务端用量启用；旧 OpenAI-compatible 配置默认关闭该开关，避免升级后向兼容端点额外发送不支持的统计字段。本次对三种协议的迁移默认值均有现有测试覆盖。新增 DeepSeek 预设默认启用服务端用量，并不等于自动修改所有既有 DeepSeek 配置。

1→2 删除旧请求日志、旧世界书默认预算转换，以及 2→3 的 OpenRouter 默认请求扩展处理属于基准之前已有的升级规则。不能把这些历史既定转换误记成此次新增的数据损失。

### 偏好、文件与混淆

- Kotpref 的 consumer ProGuard 规则保留模型类名；当前 R8 mapping 中 `AppModel` 类名未改变，实际旧包覆盖后偏好仍可读取。
- CoreViewModel 的观察函数、注解、协程签名和不同 UiIntent 类型有对应 R8 规则；正式混淆包中的页面导航、发送和统计入口实际执行成功。
- 旧头像继续通过文件 UUID/hash 读取，图片新表不要求改写旧消息；实际升级样本的头像文件正常。
- 图片文件修改、引用复制与回收集中在文件事务中；已测取消与删除路径通过。本次分支失败也验证了数据库回滚。
- 新增“最多参与 Prompt 的历史消息数”默认 500，设为 0 表示不限。它改变长对话的上下文选取范围，不删除旧消息，应在更新说明中告知用户。

### 导入导出

旧文字聊天归档和角色、世界书、Regex、故事相关兼容路径仍有测试覆盖。新图片归档使用 `extra.rpclient` 的版本化扩展，支持省空间的压缩和重复资源引用。SillyTavern 可以忽略这一扩展；不能据此承诺 SillyTavern 会显示 RPClient 导出的内嵌图片。

导入允许角色图片，而分支假定图片只属于用户消息，两者的契约冲突是此次发现的明确兼容性缺口。它主要影响升级后使用新增图片导入能力的用户，并非旧文字会话升级即崩溃。

## 编码规范与 Lint 评估

新增媒体模块将图片处理、交互协调与数据层分离，文件写入和引用回收集中处理；本次扫描没有发现 Feature 直接导入 DAO 或 AppDatabase 的新增违规。这里的分层结论限于扫描与重点链路阅读，不能替代所有业务语义审查。

未发现通配符导入；翻译资源键及带编号格式占位符齐全。但这不代表翻译语义、所有字号下的排版或所有硬编码文案都通过，克隆名称就是资源扫描无法发现的具体遗漏。

Lint 警告分布：

| 类别 | 数量 | 处理意见 |
| --- | --- | --- |
| UnusedResources | 95 | 逐项核实后清理，不凭警告批量删除动态使用资源 |
| PluralsCandidate | 38 | 完善数量文案的复数资源 |
| TypographyEllipsis / TypographyFractions | 30 | 文案排版整理 |
| MissingQuantity | 9 | 补查各语言复数类别与实际展示 |
| StringFormatCount | 6 | 复查百分号语义；当前集中于 `world_info_context_percent_helper` |
| 依赖或构建工具版本建议 | 11 | 不作为自动升级全部依赖的理由 |
| 其他 | 7 | LocaleFolder、RedundantLabel、ModifierParameter、AcceptsUserCertificates、InsecureBaseConfiguration、ObsoleteSdkInt、IconLocation 各一项 |

`world_info_context_percent_helper` 的英文说明含字面量 `0–100% of`，Lint 将其中部分文本识别为格式串，而页面按无参数资源读取；本次没有据此复现格式化崩溃。应消除歧义后再清理警告，不能把警告直接描述成确定的崩溃。

网络配置显式信任系统和用户安装的 CA，并允许 HTTP 自定义端点；其中用户 CA 支持属于本次变更。相关 Lint 警告反映实际产品行为，不是测试工具误报。这里记录其行为边界，没有把按设计支持自定义服务直接判定成新漏洞，也没有据此证明所有网络使用场景安全。

## 证据与复核入口

以下为本次机器上的临时证据，可能随系统清理而消失；关键结论、测试数字和崩溃根因已写入本文。交接后应把正式回归测试纳入仓库，并按团队流程保留构建日志。

| 证据 | 本机位置 |
| --- | --- |
| 当前构建、单测与 Lint 日志 | `/tmp/rpclient-qa-build.log` |
| 现有 95 项设备测试日志 | `/tmp/rpclient-qa-device-tests.log` |
| 旧版构建日志 | `/tmp/rpclient-qa-baseline-build.log` |
| 补充五项测试日志 | `/tmp/rpclient-qa-probe-tests.log` |
| 补充测试源码与注入配置 | `/tmp/rpclient-qa-probes/`、`/tmp/rpclient-qa-probes.init.gradle` |
| 单元测试 XML 快照 | `/tmp/rpclient-qa-evidence/unit-tests/` |
| 补充测试 XML 快照 | `/tmp/rpclient-qa-evidence/probe-5-device-tests/` |
| Lint 与本地化检查快照 | `/tmp/rpclient-qa-evidence/lint-debug.xml`、`localization.json` |
| Release 原始与还原堆栈 | `/tmp/rpclient-qa-evidence/release-branch-crash.log`、`release-branch-retraced.txt` |
| Release 发送请求元数据 | `/tmp/rpclient-qa-evidence/release-mock-requests.jsonl` |
| Release 用量与分支前截图 | `/tmp/rpclient-qa-evidence/release-token-usage.png`、`release-before-branch.png` |
| 旧包升级与数据校验工具 | `/tmp/rpclient-qa-harness/` |

后执行的补充测试会覆盖 Gradle 默认的 connected test 报告目录，因此当前该目录显示的是五项补充测试，不能将它冒充为 95 项测试的 XML。95 项通过的独立证据是前一次完整执行日志。

## 尚未覆盖与发布门槛

尚未实际验证 Android 8 / API 26 真机、其他厂商系统、低内存及进程强杀恢复、所有输入法与字体缩放、超大真实用户库、全部历史 APK、真实模型供应商端点和线上签名分发。图片与协议的现有集成测试使用可控本地服务；不能推导所有模型都支持同样的图片能力和 token 统计。

重新放行前至少需要完成：

- 修复 P1，并以角色、旁白、用户图片的分支回归测试和实际混淆包操作验证关闭问题。
- 处理上述确定的编码规范问题；若保留非阻断债务，应明确记录，不能声称完全符合规范。
- 重跑与修复相关的测试、完整单元与设备测试，以及 Release 构建；确认没有新的失败。
- 按支持范围补充最低系统版本和代表性真实端点的关键流程验证。
- 审计通过后由维护者手动迭代正式版本号并执行发布流程。

本文的“暂不放行”由已复现的崩溃直接支持，并非因为版本号尚未更新。
