# Issue #21 搜索功能实施与验证

> 更新日期：2026-10-19（Asia/Shanghai）。需求依据：[Issue #21](https://github.com/KafuuNeko/RPClient/issues/21)。本文描述当前实现；存储分项和实机数据集中在[搜索存储优化实施与实机验证](./Issue-21-搜索存储优化实施与实机验证.md)。

## 页面与检索范围

- 首页“最近内容”标题右侧提供搜索按钮，打开独立搜索页。
- 创建单聊、群聊、故事统一使用 `CharacterSearchField`，按名称、完整描述或标签筛选；首尾去空白、忽略大小写、整段字面子串匹配。后台过滤可取消，保留原顺序和已有选择。
- 独立搜索页显式提交，支持全部／单聊／群聊／故事分类，以及全部内容／标题与角色／正文范围。
- 正文覆盖全部已保存的普通单聊消息、群聊消息和故事章节，不受当前聊天窗口、摘要边界和当前章节限制。内部 Summary 消息及 think 块不参与搜索。
- 首层按内容对象去重，每页 20 个，元数据命中优先，其余按最近活动排序；进入单个对象后浏览命中消息或章节。
- 查询期间可停止并保留部分结果；准备中、部分结果、停止、错误和完整空结果分别展示。索引未就绪或查询失败时，可主动选择较慢的完整正文扫描。
- 点击标题正常打开内容，点击正文片段定位历史消息或章节；导航前复核当前归属和匹配。新增文案覆盖默认英文及全部 12 个翻译目录。

## 索引与数据一致性

正文使用 Room FTS4 外部内容表和 simple tokenizer。可见 Markdown 文字经 NFKC、小写、空白规整后，按 Unicode 码点生成可逆 token，并使用绑定的短语参数检索。中文单字、双字、英文子串和可见符号均可搜索；引号、OR、星号按用户文字处理。

| 表 | 职责 |
| --- | --- |
| `search_sources` | 源修订号、待办和删除状态 |
| `search_chunks` | 仅保存可逆 token 及块归属；每块 4,096 码点、重叠 127 码点 |
| `search_progress` | 全库变更代次、各源历史补建游标和投影格式版本 |
| `search_chunks_fts` | 字符与位置倒排索引，外部内容来自正文块 |

独立搜索最多接受 128 个规范化码点。SQL 先匹配、聚合和分页，再读取有限块并解码片段；不通过截断匹配候选数缩小搜索范围。片段来自规范化后的可见文字，英文大小写、全半角可能不同于原始存储。

应用级单工作器维护索引。源触发器在业务写入事务中记录修订和待办，历史源分批登记；变更合并约 500 ms 后处理。文字投影在事务外完成，写入批次受块数和编码字节预算限制，提交前复核源修订号，最后一批才确认。脏源、删除源不返回旧命中。

删除先使源修订失效，工作器随后删除关联块并由 Room 触发器同步 FTS。在历史播种结束、本批旧快照用完的安全点，回收已无业务源和关联块的干净删除标记。启动、停止、重试共用生命周期互斥锁，防止旧任务与同 ID 重建交叠。

分页游标携带全库变更代次，源变更和补建使旧游标失效。格式变化会先屏蔽旧正文索引，再逐源补建；显式重试从业务原文重新投影，可恢复损坏的 token。低空间时暂停派生写入，保留标题查询与主动原文扫描。

超长源通过 SQLite `substr` 分段读取，避免单行 CursorWindow 限制。Markdown 投影仍以单条消息或单章为单位在内存处理，内存需求与最大单源长度相关。元数据结果去重键集与匹配对象数量相关。

## 历史定位

单聊和群聊直接读取命中消息前后窗口，支持双向加载，恢复时保留两侧窗口数量。Display Regex 的 depth 仍以真实最新消息为零点。历史阅读期间不自动跟随流式尾部；主动生成新内容后恢复最新窗口。

故事按稳定章节 ID 打开。纯文本命中通过 Unicode 字符簇映射到原始 UTF-16 区间，完成高亮与滚动。含 Markdown 格式、链接等导致原文与可见投影不一致时，保守定位章节。对象内结果按消息／章节聚合，不提供每一处重复命中的逐处计数与翻页。

## Room 版本

`AppDatabase.version` 保持 **5**，直接更新导出的 `5.json`，已有 1→5 自动迁移链保留。旧的未发布 v5 开发库与当前结构哈希不同，不支持同版本透明升级；代码未添加启动时自动清空业务数据的逻辑，实机验证使用独立 applicationId 和专用测试库。

投影格式记录在 `search_progress.kind = -1`。以后改变分词、规范化、可见正文投影或分块规则时，应递增格式版本并补建派生索引。

## 验证与维护入口

- JVM 单元测试 309 项通过，覆盖 Unicode 编码往返、损坏编码拒绝、跨块查询、原文范围和隐藏内容映射。
- 实机回归 21 项通过，覆盖历史消息、非当前章节、分页、编辑与级联删除、同 ID 重插、20 轮增删、长源恢复、损坏 token 恢复、并发生命周期和首页导航。
- 首次实现已检查首页、搜索结果与历史定位截图，完成 360dp／字体 1.0 和 320dp／字体 2.0 导航；后续存储优化未调整页面布局。
- Debug APK 与 Android 测试 APK 构建通过；搜索新增 24 个资源键已校验全部 13 个语言目录。
- 100 MiB、500 MiB 实机基准及性能减半估算见[存储优化验证记录](./Issue-21-搜索存储优化实施与实机验证.md)。

```sh
./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

核心回归类：

```text
me.kafuuneko.rpclient.libs.search.ContentSearchRepositoryTest
me.kafuuneko.rpclient.libs.room.repository.ConversationPageRepositoryTest
me.kafuuneko.rpclient.libs.room.repository.SessionOverviewRepositoryTest
me.kafuuneko.rpclient.libs.room.AppDatabaseMigrationTest
me.kafuuneko.rpclient.feature.contentsearch.ContentSearchFlowTest
```

`ContentSearchBenchmarkTest` 默认跳过，显式传入 `runSearchBenchmark=true` 才生成专用合成库。原始基准输出在测试应用缓存和日志中，专用数据库在测试结束时删除。

尚未覆盖最低 API 26、真实低端机、低重复真实语料、极端单源内存峰值、磁盘耗尽和真实数据库文件损坏。搜索派生表与业务正文同库，底层损坏或磁盘耗尽仍可能影响业务写入。TalkBack、横屏、平板和全部主题组合需另行验收。
