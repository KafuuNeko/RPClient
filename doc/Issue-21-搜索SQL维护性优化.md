# Issue #21：搜索 SQL 维护性优化

> 实施日期：2026-10-20。主数据库保持 Version 5，独立索引数据库留待后续评估。

## 调研结论

原实现的主要维护成本来自查询分散和隐式约定，而非 SQL 数量本身：

- `ContentSearchDao` 的 `sources/text/objects/matches` 四个通用 `@RawQuery` 入口不表达查询意图，调用方负责维护 SQL 与位置参数数组。
- 普通扫描、历史播种、导航检查分别维护摘要排除条件。
- FTS 与普通扫描分别维护单聊发言者、群聊快照、章节标题规则。
- 全文分页只有分组粒度不同，原实现却在运行时插入 GROUP BY 片段；对象集合查询还手动拼接 IN 占位符。

这些查询形状在编译时都已知，适合改为 `@Query`。Room 官方说明了其编译期 SQL 校验、具名参数和列表绑定能力；对于已知查询，也明确建议优先使用 `@Query`。参考 [Query](https://developer.android.com/reference/androidx/room/Query) 与 [RawQuery](https://developer.android.com/reference/androidx/room/RawQuery)。

## 实施边界与职责

| 位置 | 职责 |
| --- | --- |
| `room/dao/SearchSourceDao` | 可见源批次、所属对象、元数据分页、标签、存在性和分段原文读取 |
| `room/dao/ContentSearchDao` | 派生表维护、修订校验及全文聚合分页 |
| `room/dao/search/SearchProjectionSql` | 编译期共享可见源、对象、元数据投影和标签表达式 |
| `room/dao/search/SearchFtsSql` | 完整的全文聚合分页查询，不拆成零散条件生成器 |
| `search/storage/SearchSourceContract` | 触发器观察的表与列，以及查询和触发器共用的摘要隐藏条件 |
| `search/engine`、`search/query`、搜索 Repository | 策略、排序、投影、生命周期和事务编排，调用具名 DAO 方法 |

搜索查询路径的四个 `@RawQuery` 入口全部移除，不再由引擎、查询辅助类或 Repository 构造 `SimpleSQLiteQuery`。DAO 中的 SQL 常量在编译期展开，继续接受 Room 对表、列、参数及结果映射的检查；没有使用跳过校验或警告抑制。

本次没有新增 Entity、DatabaseView、持久化表或索引，导出的 Version 5 schema 保持不变。分页大小、正文分段长度、默认关闭 FTS 和索引生命周期规则保持原有约定。

## 规则与性能约束

- 播种、普通扫描与导航检查共用可见源投影；其摘要隐藏表达式也用于业务触发器，保证消息在普通来源与 Summary 之间切换时规则一致。
- 标签使用一份 CASE 表达式。单聊 User 显示会话用户名，其他可见来源显示角色名；群聊显示历史发言者快照；故事显示章节标题。
- FTS 在匹配源修订后聚合，再使用稳定键集分页，最后读取 token 与标签。`parentId` 是否为空决定按对象还是按源聚合，无需运行时替换 GROUP BY 文本。
- 原文查询始终在各业务表分支中先限定主键和归属，再执行 `substr`。每段最多 16,384 个 Unicode 码点，读取器继续按码点推进并检查协程取消。
- 普通扫描仍以每批 32 个源限制内存；对象 ID 列表由 Room 负责展开和绑定。
- 元数据和历史播种仍每批读取 200 行；没有为了消除 SQL 重复而一次加载整库正文。

本机 SQLite 3.51.0 的 `EXPLAIN QUERY PLAN` 检查显示：源批次使用主键范围，存在性及正文片段使用主键定位，最终标签关联也使用主键定位。FTS 聚合仍使用临时分组结构，对象元数据仍可能物化，元数据分页仍可能排序；本次不宣称查询耗时或总体复杂度下降。查询计划随 SQLite 版本和统计信息变化，不能替代设备上的运行验证。

## 保留的运行时 SQL

- `SearchDatabaseCallback`：打开数据库时读取生命周期、安装和卸载业务触发器。此时直接使用回调连接，不能在打开回调内再次获取 Room DAO 来打开同一个数据库。
- `SearchDataRevision`：主写连接上的 TEMP 表与 TEMP 触发器，用于普通扫描游标失效。
- `SearchStorageMaintenance`：检查点、auto_vacuum、增量回收和 VACUUM。

这些连接与存储维护语句不属于 Room `@Query` 支持的普通查询入口，仍需要运行时验证。动态标识符只来自应用内固定表列契约或本模块拥有的触发器名；搜索文字与分页参数不会进入 SQL 模板。

## 验证

- Room/KSP 与应用、Android 测试包编译通过。
- JVM 单元测试 309 项通过，无失败或跳过。
- API 37 模拟器上的 Android SQLite 回归 37 项全部通过，耗时 111.909 秒：搜索 Repository 12 项、索引生命周期 11 项、搜索页面流程 4 项，以及数据库迁移和原有会话查询 10 项。使用隔离测试包执行，没有操作连接实机上的应用数据。
- 扩展真实数据库测试覆盖同时间对象排序、对象内源分页、两种策略的一致性、跨表重合 ID、摘要双向转换、标签更新、跨元数据批次的章节标题和补充平面字符跨正文分段边界。
- 没有为 DTO 属性透传或 Room 注解本身添加单元测试。
- 本轮没有重跑大语料性能基准或最低 API 设备测试；查询计划检查与功能回归不作为性能提升承诺。
