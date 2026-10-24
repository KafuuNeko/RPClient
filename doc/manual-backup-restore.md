# 完整数据备份与恢复

在 **设置 → 数据与系统 → 备份与恢复** 中可导出 `RPClient-Portable-Data.zip`。

## 归档结构

```text
RPClient-Portable-Data.zip
├── manifest.json
├── settings.json
├── characters/character-<id>.json
├── stories/story-<id>.rpstory.json
├── chats/conversation-<id>.jsonl
├── group-chats/group-<id>.jsonl
├── worldbooks/worldbook-<id>.json
├── regex/
│   ├── global-scripts.json
│   ├── character-scripts/character-<id>.json
│   └── character-authorizations.json
├── providers/provider-configurations.json
├── attachments/
│   ├── characters/
│   ├── chat-images/
│   └── other-files/
└── app-data/
    ├── token-usage.json
    ├── message-images.json
    ├── files-index.json
    ├── request-logs.jsonl
    ├── database-schema.json
    ├── request-log-schema.json
    ├── other-tables.jsonl
    ├── shared-prefs/*.xml
    └── private-files/
```

归档按功能组织 JSON/JSONL，并使用 `_tableRows` 保存无损原始行。覆盖角色、故事、单聊、群聊、世界书、正则脚本及授权、供应商与密钥、用量、请求日志、文件引用、设置和应用私有文件。新增但尚未单独分类的表写入 `other-tables.jsonl`，包括搜索状态和页面背景设置。

附件标准副本保存在 `attachments/other-files/`，角色头像和消息图片另外提供分类副本。故事文档沿用 `.rpstory.json` V2 外层格式，精确恢复依赖扩展原始行。可读偏好写入 `settings.json`，恢复以原始偏好 XML 为准。

根目录 `manifest.json` 记录每个文件的大小和 SHA-256；私有目录允许存在同名文件，并参与校验。恢复会检查路径、重复条目、校验和、SQLite 完整性、外键以及 Room 迁移兼容性。不兼容的数据库在暂存目录被拒绝，不会覆盖当前数据。FTS 虚表和影子表根据原始搜索行重建，保留 rowid 和搜索开关。

兼容早期 V1 JSONL 快照和原始 SQLite 快照。旧 SQLite 格式只恢复其包含的主库、偏好和附件，不改变请求日志或应用私有文件。

## 恢复与失败处理

完整恢复会覆盖两套数据库、偏好、附件和应用私有文件，随后重启应用。需要保留当前数据时，应先另存一份备份。

归档校验和迁移在独立暂存目录进行。通过校验后先关闭数据库，再保存回滚副本，避免遗漏 WAL 中已提交的数据。替换失败时尝试恢复所有条目；若回滚本身失败，则保留缓存目录中的回滚副本，不将其作为普通临时文件删除。数据库关闭后的路径都要求重启，不能继续使用旧连接。

## 模块职责

- `BackupRestoreActivity` 只负责系统文件选择和重启；ViewModel 管理操作、确认弹窗与本地化状态。
- `PortableBackupRepository` 编排导出和恢复；`PortableBackupDocumentWriter` 组织功能文档。
- `BackupDatabaseCodec` 处理结构、数据行和 FTS 重建；`BackupDatabaseValidator` 使用正常 Room 迁移校验暂存数据库。
- `BackupArchiveFiles` 处理 ZIP 和文件校验；`BackupDataReplacer` 统一执行新旧格式的替换与回滚。
- 新增文案放在各语言既有的 `strings.xml`，不另设备份或 Markdown 专用资源文件。

## 数据隐私

ZIP 不加密，可能包含 API Key、自定义请求头、提示词、对话、原始请求响应日志和附件。获得归档的人可以读取这些内容，请妥善保存，勿公开发布。

## 回归验证

设备测试使用独立临时目录和真实 SQLite，覆盖完整往返、FTS 与背景附件、私有同名 manifest、无效归档、未来数据库版本、损坏行数据以及替换失败后的 WAL 回滚。测试不读取或恢复用户实际数据。
