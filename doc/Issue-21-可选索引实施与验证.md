# Issue #21：可选索引实施与验证

> 实施日期：2026-10-19～2026-10-20。基于 `develop` 的搜索实现继续调整，Room version 保持 **5**。

## 范围与评估结论

本轮同时落实[代码侵入程度评估](./Issue-21-搜索功能代码侵入程度评估.md)中的触发器升级、搜索 SQL 契约和验证建议，以及用户确认的 [FTS 可选启用与双搜索方案](./Issue-21-FTS可选启用与双搜索实现交接.md)。

- 默认使用普通原文扫描；只有用户确认并完整构建后才启用 FTS。
- 优先补齐开关、取消、清理、进程恢复和真实文件库验证，再评估是否需要分库。
- 本轮没有提取单聊／群聊历史窗口的共同状态。该项属于独立的页面重构，现有定位、历史窗口及末尾按钮保持回归验证。
- 独立索引库仍属于条件性方案。本轮继续共用主库，不承诺索引故障与业务写入完全隔离。

## 包组织

Repository 保留数据层对外入口，具体搜索算法和数据库辅助能力集中在搜索模块，避免将所有协作类堆入 `room.repository`。

```text
libs/
├── room/dao/
│   ├── ContentSearchDao.kt               # 派生索引读写与 FTS 分页
│   ├── SearchSourceDao.kt                # 原始业务数据的搜索读取
│   └── search/
│       ├── SearchProjectionSql.kt        # 编译期共享投影与标签规则
│       └── SearchFtsSql.kt               # 完整的 FTS 聚合分页 SQL
├── room/repository/
│   └── ContentSearchRepository.kt        # 统一业务入口、索引生命周期与后台增量
└── search/
    ├── ContentSearchModels.kt            # 请求、结果、分页及状态
    ├── SearchDatabaseCallback.kt         # 打开契约与业务触发器
    ├── SearchTextCodec.kt                # 可见文字投影与规范化
    ├── SearchNavigation.kt
    ├── engine/
    │   ├── ContentSearchEngine.kt        # 共同查询接口
    │   ├── SearchEngineBase.kt           # 共享分页流程
    │   ├── DatabaseScanSearchEngine.kt
    │   └── FtsSearchEngine.kt
    ├── query/
    │   ├── SearchQueryCoordinator.kt     # 内部策略路由、查询会话与请求失效
    │   └── SearchQuerySupport.kt         # 元数据、片段、导航复核
    └── storage/
        ├── SearchDataRevision.kt        # 普通扫描的连接内变更版本
        ├── SearchSourceContract.kt      # 业务变更观察与摘要隐藏条件
        ├── SearchSourceReader.kt        # 长正文读取
        └── SearchStorageMaintenance.kt  # 关闭后的文件回收
```

页面通过 `ContentSearchRepository` 使用搜索。查询依赖方向为 Repository → SearchQueryCoordinator → engine → SearchQuerySupport／storage；这些实现类保持 `internal`。没有为了目录整齐引入新框架、额外注入容器或逐类接口。

## 统一搜索入口

`ContentSearchRepository` 私有持有一个 `SearchQueryCoordinator`，Koin 只注册仓库。外部不再通过 `.queries` 调用另一层公开仓库，也不需要配对构造查询与索引管理对象。

| 能力 | 对外接口与约束 |
| --- | --- |
| 输入预检 | `validateQuery(raw)` 返回有效查询、空白或超长；页面只映射本地化提示 |
| 分页查询 | `search(SearchRequest(raw), cursor, onPartial)` 接收原始输入，自动选择策略；翻页保留首页查询与范围 |
| 部分结果 | 回调在 IO 上下文发布当前页累计结果；页面在主线程更新状态 |
| 目标复核 | `exists(hit, raw)` 复核归属，并检查正文或章节标题是否仍命中；省略查询只检查存在与归属 |
| 索引管理 | `indexState`、`enable()`、`disable()`、`retry()` 保留现有语义；开启确认仍属于页面 |
| 应用恢复 | `restore()` 恢复已确认索引的增量工作或未完成清理；`stop()` 只停止后台维护，保留持久阶段 |

查询规则只在仓库入口定义：原始文字最多 4096 个 UTF-16 码元，规范化后不得为空，且最多 128 个 Unicode 码点。码点上限复用正文分块重叠约束。`search()` 自行调用同一校验，不依赖页面是否预检；非法输入抛出 `IllegalArgumentException`，不发布部分结果。页面的草稿恢复体积预算独立于查询有效性，仍允许保存尚未提交的无效输入。

开启、关闭、重建和构建完成都通过仓库持有的同一个协调器使旧查询失效。模式切换会取消活跃查询；旧游标仍由策略、会话和数据版本拒绝复用。调用者可取消自己的查询任务；仓库不会因此停止应用作用域内的索引维护。页面继续保留请求代次守卫，以阻止已进入主线程队列的旧结果覆盖新状态。

本次只收敛业务调用边界，保留现有内部引擎与索引工作器分工。页面跳转、对话框和编辑器局部高亮仍由展示层负责。独立索引库及进一步拆分索引控制器留待后续需求评估。

## 产品行为与持久状态

| 场景 | 行为 |
| --- | --- |
| 新库、未确认的旧自动索引 | 默认关闭；旧派生内容先清理，不静默构建 |
| 点击开启 | 显示确认；确认后才进入可取消的构建弹窗 |
| 全量构建 | 分批登记三类历史正文，显示已确认处理数量；新增待办也纳入完成判定 |
| 构建完成 | 同事务检查播种完成且没有待办，持久化启用，再允许 FTS 查询 |
| 取消／关闭 | 作废旧查询，取消并等待工作器，停用业务维护触发器，清理并回收空间 |
| 构建失败／中断 | 进入清理，保持关闭；重启不自动继续昂贵全量构建 |
| 清理失败 | 保留 Cleaning，显示重试；重启可以继续清理 |
| 已启用后重启 | 恢复增量维护，不清空重建 |
| 投影格式不兼容 | 停用并清理，重新启用仍需明确确认 |

生命周期复用已有 `search_progress` 的保留行 `kind = -2`，`cursor` 表示 Disabled／Building／Enabled／Cleaning。关闭后只保留这条轻量管理记录，删除格式、全局搜索版本、播种检查点及所有源修订／待办。故事自身的业务修订号不受影响。

本轮没有改变 Entity 字段、Room 导出 schema 或 `AppDatabase` 的 version，也没有新增破坏性启动迁移。当前结构相同的 v5 库可按新开关规则打开；更早、结构不同的未发布 v5 开发库仍不具备同版本透明升级承诺。

构建／关闭命令在索引仓库的生命周期锁内按调用顺序排队。工作器不获取这把锁，避免取消等待时死锁；没有给聊天、群聊、故事等 Repository 增加全局锁。页面重建恢复应用作用域持有的任务状态。Long 修订号不增加溢出专项处理。

## 两种搜索与一致性

- 两种实现共享 Markdown 可见文本、think 排除、Unicode 规范化、片段生成和导航归属校验，不把直接匹配原始 Markdown 的 `LIKE` 当作等价替代。
- 普通扫描分批遍历完整正文，保留当前页和一条预读候选，不固定截取前几条历史。
- 普通扫描的变更版本使用主写连接上的单行 TEMP 表及 TEMP 触发器，不读写 FTS 修订、检查点或持久待办；直接 SQL 写入也能使分页失效。
- 查询游标同时绑定策略与查询管理会话；切换模式、关闭后重新开启，旧游标都不能复用。
- FTS 继续检查源修订、脏状态和删除状态；旧工作不能发布已经被修改的正文。

修复了同 ID `INSERT OR REPLACE` 的隐患：外层冲突策略可能覆盖触发器内 `INSERT OR IGNORE`，使现有源修订被重置。现在先以 `WHERE NOT EXISTS` 判断源记录是否存在，再递增已有修订；对应真实 SQLite 回归覆盖替换后旧结果不可见及增量完成后新结果可见。

## 触发器与空间清理

业务表及相关字段集中在 `SearchSourceContract`。打开已启用数据库时对比 `sqlite_master` 中的实际 SQL，仅替换变化的同名触发器；重复打开且定义未变时不重复执行 DDL。更新触发器同时限制 `UPDATE OF` 字段并比较新旧值，避免 Room 全字段同值更新造成不必要的分页失效。

后续 SQL 收敛、编译校验和规则复用见 [搜索 SQL 维护性优化](./Issue-21-搜索SQL维护性优化.md)。本节之前记录的生命周期与性能测试属于可选索引实现阶段，不作为后续 SQL 调整的性能结论。

取消或关闭时先移除本模块的 `search_source_*`／`search_meta_*` 业务维护触发器，保留 Room 的 FTS 同步结构，再分批删除正文块、源状态和进度。对空内容执行 FTS4 `rebuild`，清理倒排内部数据。

空间回收检查实际 `auto_vacuum`：FULL 依赖提交回收，INCREMENTAL 执行增量回收，NONE 在存在空闲页且空间充足时执行 VACUUM；前后执行 WAL 截断检查点。忙碌或空间不足时保持清理失败状态，不用“记录已删除”冒充“文件已缩小”。此行为依据 [SQLite FTS4 维护命令](https://www.sqlite.org/fts3.html#special_commands_for_fts3_and_fts4)、[auto_vacuum](https://www.sqlite.org/pragma.html#pragma_auto_vacuum) 与 [VACUUM](https://www.sqlite.org/lang_vacuum.html) 的存储约束。

主库打开、启用期间业务触发器写入、磁盘耗尽及文件损坏仍可能影响业务事务。后台异常捕获只能处理派生任务失败；本轮没有把它描述为完整故障隔离。

## 验证方式

Android 验证使用独立 applicationId `me.kafuuneko.rpclient.searchlifecycleverify.dev` 和专用合成数据库，设备为 API 37 的 `sdk_gphone16k_arm64` 模拟器。没有覆盖正式应用或读取真实聊天。

常规构建与 JVM 回归：

```sh
./gradlew --offline --console=plain \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

隔离测试包通过临时 Gradle init script 在 `androidComponents.finalizeDsl` 将 `defaultConfig.applicationId` 改为 `me.kafuuneko.rpclient.searchlifecycleverify`；项目已有 debug 后缀 `.dev`。该覆盖没有写入项目构建配置。

关键回归入口：

| 测试 | 覆盖 |
| --- | --- |
| `SearchIndexLifecycleTest` | 默认关闭、双模式结果、跨模式游标、快速取消、同 ID 替换、实际文件缩小、触发器升级、清理注入故障、v4 历史数据迁移 |
| `ContentSearchRepositoryTest` | 查询分页、脏源排除、编辑／删除、取消、投影格式、增量恢复 |
| `ContentSearchModeFlowTest` | 查询中开启确认、取消确认、构建弹窗、Activity 重建、取消清理、再次开启与关闭 |
| `ContentSearchFlowTest` | 首页入口、单聊／群聊历史定位及末尾按钮 |
| 迁移、会话窗口及概览测试 | 原有数据和页面读取回归 |
| `SearchProcessRecoveryTest` | 外部 force-stop 后，在新进程中恢复并清理实际半成品索引 |
| `SearchMixedLoadBenchmarkTest` | 无 FTS、全量构建并行、日常增量三组写入观测 |

强杀测试采用两次 instrumentation：传入 `searchCrashStage=prepare` 后，等待测试写出 `cache/search-crash-ready`，确认已有提交的正文块，再从外部 `am force-stop` 隔离测试应用；以 `searchCrashStage=verify` 在新进程检查关闭状态、清空派生记录、保留全部 250 条原文且可普通搜索。准备阶段进程崩溃是预期行为，不能算作正常通过的测试。

混合负载需显式传入 `runSearchMixedBenchmark=true`。三组相同初始语料包含 1,600 条消息，分别执行 40 轮单条保存、25 条批量导入和约 10 KB 故事正文自动保存；工作器在测量时运行。输出保留全部耗时样本、写入时状态、文件／WAL 采样峰值。主线程调度延迟只作为响应性代理指标，不是帧耗时或掉帧率。

## 本轮验证结果

- 构建、Debug APK 和 Android 测试 APK 成功；309 项 JVM 测试通过，无失败或跳过。
- 包调整后一次完整 instrumentation 运行通过 36 项测试：35 项功能回归及一项显式混合负载测试。
- 单独再次运行实际强杀恢复验证通过。准备阶段确认已提交半成品后从外部停止进程，新进程清理后保留 250 条原文且普通搜索可用。
- 小屏按 320 × 600 dp、字体缩放 1.5 复测开启、Activity 重建、取消及关闭流程通过；检查了确认、构建与关闭后的截图，按钮可达。随后恢复模拟器原尺寸和字体。
- 13 份语言资源的 11 条新增文案齐全，占位符与英文一致。`git diff --check` 通过，AppDatabase 与导出 schema 无改动。

完成编译和界面回归后，单独采集的混合负载结果如下。原始样本见 [issue21-search-mixed-emulator.json](./research/issue21-search-mixed-emulator.json)。

| 模式 | 单条保存 P95 / ms | 导入 25 条 P95 / ms | 故事自动保存 P95 / ms | 主线程调度 P95 / ms | 主库＋WAL＋SHM 采样峰值 / MiB | WAL 采样峰值 / MiB |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 关闭 | 5.345 | 4.918 | 1.007 | 0.523 | 7.25 | 0.50 |
| 全量构建并行 | 2.575 | 3.478 | 2.295 | 0.106 | 10.09 | 0.50 |
| 已启用增量 | 1.173 | 8.698 | 1.536 | 0.215 | 31.43 | 8.10 |

关闭组的 40 次写入状态均为 Disabled，构建组均为 Building，已启用组为 Ready 21 次／Updating 19 次。关闭组没有发起普通搜索，因此尚未安装扫描使用的连接内临时计数器。此对照量化的是无 FTS 维护与索引工作并行时的写入情况，不包含扫描并行负载。

这是固定顺序、每组 40 轮的单次模拟器采样，没有随机化或重复轮次；缓存、预热和宿主调度会影响组间差异。部分构建组数值低于关闭组不能解释为索引提升写入性能；采样峰值也不是整个完整构建周期的绝对峰值。所有测试库实际为 `auto_vacuum = FULL`。

## 统一入口优化补充验证

- 收敛入口后，离线构建、Debug APK 和 Android 测试 APK 均通过；309 项 JVM 测试通过，无失败或跳过。
- API 37 隔离模拟器上的 39 项 Android 回归全部通过，包含查询与文件库生命周期、搜索页面定位、开关流程、数据库迁移、会话窗口及概览。
- 新增真实数据库回归验证原始输入预检与直接搜索一致：全角／空白规范化、128 码点 emoji 跨块命中、129 码点拒绝、NFKC 展开超限及原始输入体积上限。
- 新增回归验证开启和关闭均取消同一仓库的活跃查询，取消后仍可从该仓库查询与复核目标；既有跨模式旧游标失效回归继续通过。
- `AppDatabase.version` 保持 5，导出 schema 与优化前逐字节一致。本次未重跑大库基准或外部进程强杀实验，上述历史结果不作为本次新增性能结论。

## 未验证边界

- 当前没有连接高性能物理设备；模拟器结果不能换算为用户此前要求的“实机性能除以二”，也不代表低端机。
- 本轮未重跑 100 MiB／500 MiB 全量搜索基准，不沿用旧基准宣称新开关、取消或清理的耗时。
- 最低 API 26、真实低磁盘环境、严重数据库损坏及超大单会话极远历史定位仍需独立验证。
- 实际强杀覆盖构建阶段；清理中断由注入故障及关闭重开验证，没有冒充清理阶段的实际进程强杀。
- 文件回收仍受主库写锁、读事务及可用临时空间约束，失败会保留可重试的清理状态。
- NONE／INCREMENTAL 文件回收分支尚未分别实测，本轮实际缩小文件的断言在 FULL 模式通过。
