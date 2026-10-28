# 完整数据备份与恢复

在 **设置 → 数据与系统 → 备份与恢复** 中可导出 `RPClient-Portable-Data.zip`。

## 归档结构（v3）

```text
RPClient-Portable-Data.zip
├── manifest.json                  格式与版本，不含校验和
├── characters/<名称>-<id>.json     Character Card V2，与单个角色导出相同（内嵌绑定的世界书与角色正则）
├── worldbooks/<名称>-<id>.json     酒馆世界书（含角色绑定的世界书）
├── regex/global-scripts.json       仅全局正则脚本数组
├── stories/<标题>-<id>.rpstory.json
├── chats/<标题>-<id>.jsonl         酒馆 JSONL
├── group-chats/<标题>-<id>.jsonl   每行一个数据库行（无菜单导入器）
├── tables/<表名>.jsonl             其余业务表，每行一个数据库行
├── request-logs/<表名>.jsonl       请求日志库
├── attachments/<sha256>            附件，保持原始二进制
├── shared-prefs/*.xml              偏好，保持原始 XML（保留类型）
├── private-files/                  应用私有文件
└── app-data/
    ├── database-schema.json        技术结构，不要手改
    └── request-log-schema.json
```

### 单一事实来源

- 角色、世界书、正则、故事、聊天文件使用各菜单导入所用的格式，文件内容就是恢复时读取的内容；每个文件都能在对应菜单单独导入。
- 这些格式无法表达的列（数据库主键、头像文件 ID、故事章节修订号等）放在文件顶层的 `rpclient_backup` 键下。各菜单的导入解码器会忽略该键。格式内已有的字段不会在该键里重复保存，所以改正文只需要改一处。
- 没有菜单导入器的表使用每行一个 JSON 对象的 JSONL，行内容就是数据库行。
- 偏好保持 Android 原始 XML，附件保持二进制，二者没有 JSON 等价格式。

### 手动编辑规则

- 修改：直接编辑文件内容，保存回 ZIP 即可。
- 删除：删除文件即删除该条目；指向它的子记录（如该角色的聊天）会在恢复时按外键一并移除，并写入详情日志。
- 新增：复制一个文件并删除其中的 `rpclient_backup` 键，恢复时自动分配新主键。复制文件但保留 `rpclient_backup.id` 时，重复主键会被重新分配并记录警告。
- 新增聊天文件时，用文件头里的角色名匹配已恢复的角色；也可以在头行 `rpclient_backup.session.characterId` 指定角色主键。
- 角色作用域正则只存在于角色卡里，恢复时以卡内内容为准。绑定的世界书以 `worldbooks/` 下同一本为准；只有该文件缺失时才使用角色卡内嵌的副本，并记录警告。
- 角色卡只保存首条开场白；`<START>` 分隔的额外开场白与应用自身的角色卡导出一样不会保留。
- 恢复仍会检查路径安全、重复条目、SQLite 完整性、外键以及 Room 迁移兼容性。不兼容的数据库在暂存目录被拒绝，不会覆盖当前数据。

### 兼容旧格式

仍可恢复 portable v2（只读）、V1 JSONL 快照和原始 SQLite 快照。v2 沿用校验和与 `_tableRows`，不支持手动编辑。新的导出一律为 v3。

## 恢复与失败处理

完整恢复会覆盖两套数据库、偏好、附件和应用私有文件，随后重启应用。需要保留当前数据时，应先另存一份备份。

归档校验和迁移在独立暂存目录进行。通过校验后先关闭数据库，再保存回滚副本，避免遗漏 WAL 中已提交的数据。替换失败时尝试恢复所有条目；若回滚本身失败，则保留缓存目录中的回滚副本，不将其作为普通临时文件删除。数据库关闭后的路径都要求重启，不能继续使用旧连接。

## 模块职责

- `BackupRestoreActivity` 只负责系统文件选择和重启；ViewModel 管理操作、确认弹窗与本地化状态。
- `PortableBackupRepository` 编排导出和恢复；`BackupV3Writer` / `BackupV3Reader`（`PortableBackupV3.kt`）按各菜单的编解码器读写文档。
- `BackupReport` 收集阶段日志与警告；页面在备份和恢复结束后以可展开、可复制的区块展示，失败时仅附固定错误类别和异常类型，不透传原始异常消息、输入内容或堆栈。恢复成功后由用户点击按钮重启，警告不会随进程消失。
- `BackupDatabaseCodec` 处理结构、数据行和 FTS 重建；`BackupDatabaseValidator` 使用正常 Room 迁移校验暂存数据库。
- `BackupArchiveFiles` 处理 ZIP 和文件校验；`BackupDataReplacer` 统一执行新旧格式的替换与回滚。
- 新增文案放在各语言既有的 `strings.xml`，不另设备份或 Markdown 专用资源文件。

## 数据隐私

ZIP 不加密，可能包含 API Key、自定义请求头、提示词、对话、原始请求响应日志和附件。获得归档的人可以读取这些内容，请妥善保存，勿公开发布。

## 回归验证

设备测试使用独立临时目录和真实 SQLite，覆盖完整往返、FTS 与背景附件、私有同名 manifest、无效归档、未来数据库版本、损坏行数据、手动编辑角色卡与删除角色文件后的恢复结果，以及替换失败后的 WAL 回滚。测试不读取或恢复用户实际数据。

## 混淆（R8）注意

备份用 Gson 按“字段名 = 数据库列名”读写 `libs/room/entity` 下的实体，因此 `proguard-rules.pro` 保留了这些实体的字段名。去掉该规则后 release 包会读到全为 null 的实体；导出时会在首次映射某个实体时校验字段名，并在备份详情中给出明确原因。
