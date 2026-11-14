# RPClient WebDAV 自动备份与恢复实现方案

> 文档状态：待评审
>
> 调研日期：2026-09-02
>
> 对应需求：[Issue #12 请求支持类似连接 WebDAV 的自动备份功能](https://github.com/KafuuNeko/RPClient/issues/12)
>
> 代码基线：`develop` / `f522af8`

---

## 结论

该功能可行，推荐实现为“用户自带存储”的加密快照备份：

```text
RPClient Android 客户端
    │
    │ HTTPS + WebDAV
    ▼
用户已有的 WebDAV / NAS / 云存储
```

RPClient 只实现客户端能力，不提供项目方账号、服务器、带宽或存储空间。项目维护者不承担服务器成本，也不经手用户备份数据。用户是否需要为 WebDAV 服务付费，取决于其自行选择的 NAS、云存储或托管服务。

首版必须同时具备可恢复性和客户端加密，不能只做“定时上传数据库文件”：

- 活动中的 SQLite/Room 数据库不能直接复制，否则可能得到与 WAL 不一致的文件。
- 主库包含私密对话、角色资料、模型 API Key 和自定义请求头，不能明文上传。
- 备份只有在能够完成校验、恢复和失败回滚时才具有实际价值。
- WebDAV 是远程文件协议，不负责理解 RPClient 数据，也不提供跨设备记录合并。

推荐按“本地完整备份与恢复 → 手动 WebDAV → 自动调度”分阶段交付。完整可靠版本预估需要约 18–28 人日。

---

## 产品边界

### 目标

- 生成覆盖 RPClient 主要业务数据和用户设置的完整快照。
- 在设备端完成压缩和加密后，将归档直接上传到用户配置的 WebDAV。
- 支持立即备份、周期备份、远端备份列表、下载恢复和历史版本清理。
- 支持应用升级后恢复旧版本备份，并复用现有 Room migration。
- 在断网、进程结束、空间不足、密码错误或归档损坏时保护当前本地数据。
- 向用户明确说明项目方不提供服务器，也无法找回备份口令。

### 非目标

- 不建设 RPClient 官方云服务、账号系统、对象存储或中转服务器。
- 不做多设备实时同步、消息级增量同步或冲突合并。
- 不允许远端备份覆盖正在使用的本地数据库。
- 不备份原始请求/响应调试日志、缓存、临时文件和 WorkManager 内部数据。
- 首版不支持 OAuth、Digest、客户端证书、WebDAV LOCK、分块上传或服务商专有接口。
- 首版不承诺准点执行，只承诺满足系统约束后的周期性尝试。
- 首版不支持把新版本应用生成的备份恢复到数据库结构更旧的应用。

### 推荐默认策略

| 项目 | 默认值 | 说明 |
|---|---:|---|
| 传输协议 | HTTPS | Basic 认证必须运行在 TLS 上；首版拒绝明文 HTTP |
| 自动备份周期 | 每日 | 同时提供每三日、每周和关闭 |
| 网络约束 | 非计费网络 | 用户可改为任意已连接网络 |
| 电量约束 | 电量不低 | 可选“仅充电时” |
| 本地空间约束 | 存储空间不低 | 始终启用 |
| 远端保留数量 | 7 份 | 只清理当前安装标识下由 RPClient 创建的归档 |
| 请求日志 | 不包含 | 延续现有隐私与系统备份规则 |
| API Key | 包含 | 主库完整快照的一部分，因此强制使用归档加密 |
| 归档口令 | 必填 | 自动备份开启时必须安全缓存在本机 |
| 多设备策略 | 独立目录 | 不进行合并，避免不同设备互相覆盖 |

---

## 现状与差距

### 当前存储

RPClient 目前有三类需要协调的持久化数据：

| 数据域 | 当前实现 | 本方案处理方式 |
|---|---|---|
| 业务主库 | [`AppDatabase.kt`](../../app/src/main/java/me/kafuuneko/rpclient/libs/room/AppDatabase.kt)，`primary.sqlite`，当前包含 20 个 Entity | 生成一致的临时数据库快照并加入归档 |
| 全局偏好 | [`AppModel.kt`](../../app/src/main/java/me/kafuuneko/rpclient/libs/AppModel.kt)，当前约 48 个 Kotpref 字段 | 编码为带类型信息的 `preferences.json` |
| 私有资源 | [`FileRepository.kt`](../../app/src/main/java/me/kafuuneko/rpclient/libs/room/repository/FileRepository.kt)，`getDir("repository")` | 按数据库中的文件映射收集并校验内容哈希 |
| 调试请求日志 | [`RequestLogDatabase.kt`](../../app/src/main/java/me/kafuuneko/rpclient/libs/room/RequestLogDatabase.kt)，`request_logs.sqlite` | 明确排除 |

主库中的 [`LLMProvider.kt`](../../app/src/main/java/me/kafuuneko/rpclient/libs/room/entity/LLMProvider.kt) 保存 API Key 和自定义请求头，因此完整备份不能提供“不加密上传”选项。

### 当前导入导出不是完整备份

现有角色卡、世界书、聊天和故事归档是面向单个领域对象的传输格式。例如 [`ChatArchiveModels.kt`](../../app/src/main/java/me/kafuuneko/rpclient/libs/chat/ChatArchiveModels.kt) 会主动舍弃本地 Room 主键，并要求导入时重新绑定角色。它们适合内容迁移，但不能恢复：

- 所有单聊、群聊、故事和关联关系。
- 模型配置、API Key、自定义请求头和选择状态。
- 全局 Prompt、摘要设置、用户身份和头像引用。
- Token 使用统计、Regex 授权关系和其他主库数据。

### 当前 Android Auto Backup

[`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml)、[`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) 和 [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) 已启用 Android Auto Backup，并只排除请求日志数据库。

Android 默认会备份 SharedPreferences、数据库和 `getDir()` 文件，但标准云备份每应用只有 25 MB，只保留最近一份，并受设备、账号、网络和空闲状态控制。该能力可以继续保留，但不能替代用户可管理的 WebDAV 历史快照。

引入本方案后，需要额外从 Android Auto Backup 和设备迁移中排除：

- WebDAV 密码和本地缓存的归档口令密文。
- Android Keystore 关联的备份连接设置。
- 待恢复标记、回滚副本和临时归档。
- WorkManager 或本功能生成的瞬时状态文件。

---

## 总体架构

### 模块关系

```text
Main Settings
    │ 打开页面
    ▼
feature/backup
    │ UiIntent / UiState / ViewEvent
    ▼
BackupCoordinator
    ├── BackupSnapshotRepository ── Room / AppModel / repository files
    ├── BackupArchiveCodec ──────── manifest / ZIP / size limits
    ├── BackupCrypto ────────────── PBKDF2-HMAC-SHA256 / AES-256-GCM
    ├── WebDavBackupRepository ──── list / upload / download / retention
    │       └── WebDavClient ────── OkHttp / PROPFIND / MKCOL / PUT / MOVE
    ├── BackupCredentialStore ───── Android Keystore protected secrets
    └── PendingRestoreManager ───── startup swap / rollback

BackupScheduler
    └── WebDavBackupWorker ──────── WorkManager periodic and one-time work
```

### 推荐目录

```text
app/src/main/java/me/kafuuneko/rpclient/
├── feature/backup/
│   ├── BackupActivity.kt
│   ├── BackupViewModel.kt
│   ├── model/
│   │   └── BackupUiModels.kt
│   ├── presentation/
│   │   ├── BackupUiIntent.kt
│   │   ├── BackupUiState.kt
│   │   └── BackupViewEvent.kt
│   └── ui/
│       └── BackupLayout.kt
└── libs/backup/
    ├── BackupArchiveCodec.kt
    ├── BackupCoordinator.kt
    ├── BackupCredentialStore.kt
    ├── BackupCrypto.kt
    ├── BackupPreferenceCodec.kt
    ├── BackupScheduler.kt
    ├── BackupSettingsStore.kt
    ├── BackupSnapshotRepository.kt
    ├── BackupWorker.kt
    ├── PendingRestoreManager.kt
    ├── WebDavBackupRepository.kt
    ├── WebDavClient.kt
    ├── WebDavXmlParser.kt
    └── model/
        ├── BackupArchiveModels.kt
        ├── BackupOperationModels.kt
        ├── BackupSettings.kt
        └── WebDavModels.kt
```

`feature/main` 只新增“备份与恢复”设置项、对应的用户 Intent 和打开 `BackupActivity` 的 ViewEvent。备份业务、网络、文件和恢复逻辑不得继续堆入 `MainViewModel`。

### 依赖调整

在 [`libs.versions.toml`](../../gradle/libs.versions.toml) 中增加当前稳定版 WorkManager：

```toml
[versions]
work = "2.11.2"

[libraries]
androidx-work-runtime = { group = "androidx.work", name = "work-runtime", version.ref = "work" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

应用依赖增加：

```kotlin
implementation(libs.androidx.work.runtime)
testImplementation(libs.okhttp.mockwebserver)
```

无需引入专用 WebDAV SDK或第三方加密框架：

- 复用现有 OkHttp 发送自定义 WebDAV 方法。
- 使用 `android.util.Xml` 流式解析 `207 Multi-Status`。
- 使用 Gson 编解码 manifest 和偏好快照。
- 使用 JCA、Android Keystore、`ZipOutputStream` 和 `ZipInputStream`。

---

## 配置与密钥存储

### 非敏感配置

使用独立的 `BackupSettingsStore`，不要把该功能的状态继续加入 `AppModel`。建议字段：

```kotlin
data class BackupSettings(
    val endpoint: String,
    val username: String,
    val remoteRoot: String,
    val installationId: String,
    val schedule: BackupSchedule,
    val networkPolicy: BackupNetworkPolicy,
    val requiresCharging: Boolean,
    val retentionCount: Int,
    val lastAttemptAt: Long?,
    val lastSuccessAt: Long?,
    val lastResult: BackupLastResult?
)
```

设计约束：

- `endpoint` 不允许带 URI user-info，避免用户名或密码进入 URL、日志和异常。
- `remoteRoot` 只保存规范化后的相对路径段。
- `installationId` 使用随机 UUID，区分同一 WebDAV 账户下的多台设备。
- `lastResult` 只保存稳定错误码和脱敏摘要，不保存响应体、真实本地路径或堆栈。
- 整个设置文件从 Android Auto Backup 中排除；系统恢复后要求用户重新连接 WebDAV。

### 敏感配置

`BackupCredentialStore` 保存：

- WebDAV 密码或应用专用密码。
- 自动备份使用的归档口令缓存。

实现规则：

- 使用 `AndroidKeyStore` 生成不可导出的 AES-256-GCM 密钥。
- Keystore alias 固定带版本，例如 `rpclient_backup_secrets_v1`。
- 每次写入生成独立随机 nonce，不得复用。
- 密文、nonce 和版本保存在专用 SharedPreferences 中。
- Keystore 密钥不要求每次用户认证，否则后台 Worker 无法无人值守运行。
- 密钥缺失、失效或解密失败时，只清除备份连接凭据、关闭自动任务并要求重新配置，不影响主库。
- 不使用已弃用的 `EncryptedSharedPreferences`。
- 不把凭据放入 WorkManager `Data`、通知、异常消息或日志。

归档口令与 WebDAV 密码承担不同职责，不应复用：

- WebDAV 密码用于连接用户的服务器。
- 归档口令用于在任何新设备上解密 `.rpbackup`。
- Android Keystore 只保护本机缓存，不能替代用户掌握的可移植归档口令。

### 归档口令体验

- 首次创建备份时要求输入并确认归档口令。
- 自动备份必须允许应用在本机安全缓存该口令，否则不能启用。
- 明确提示“项目方和 WebDAV 服务均无法帮助找回口令”。
- 恢复到新设备时，由用户重新输入口令。
- 错误口令只返回统一的“无法解密或归档已损坏”，不区分内部认证标签细节。

---

## 备份归档格式

### 文件名与远端布局

归档扩展名使用 `.rpbackup`：

```text
<remoteRoot>/RPClient/v1/<installationId>/
├── rpclient-20260902T031500Z-a81f0d2c.rpbackup
├── rpclient-20260903T031628Z-0e3241ac.rpbackup
└── .rpclient-upload-<uuid>.partial
```

规则：

- 时间统一使用 UTC，避免时区切换造成排序错误。
- 文件名只包含时间、随机后缀和固定字符，不包含用户名、角色名或设备型号。
- 临时文件使用随机名称，成功后通过 `MOVE` 改为最终名称。
- 当前安装只管理自己的 `installationId` 目录，不能删除其他设备目录。
- 失败遗留的 `.partial` 仅在超过安全时间窗口后清理。

### 外层加密容器

`.rpbackup` 是自描述的加密容器，外层明文头只保存解密必需的非业务参数：

```text
magic                 RPCBACKUP
container_version     1
kdf                   PBKDF2-HMAC-SHA256
kdf_salt              random bytes
kdf_iterations        calibrated positive integer
cipher                AES-256-GCM
nonce                 random bytes
encrypted_payload     authenticated ZIP stream
authentication_tag    emitted by GCM
```

约束：

- 归档口令使用带随机 salt 的 PBKDF2-HMAC-SHA256 派生 256 位密钥。
- 迭代次数写入头部，并在代表性 API 26–36 设备上校准到可接受耗时；设置安全下限。
- 固定头、版本和 KDF 参数作为 GCM AAD，防止参数被未检测地篡改。
- 每个归档使用新的 salt 和 nonce。
- ZIP 写入加密流，磁盘上不产生未加密的完整 ZIP。
- 临时数据库快照仍会短暂存在于应用私有 `noBackupFilesDir`，操作结束后必须删除。

### 内层 ZIP

解密后的 ZIP 结构：

```text
manifest.json
data/primary.sqlite
data/preferences.json
files/<sha256>
```

推荐 manifest：

```json
{
  "format": "rpclient_backup",
  "format_version": 1,
  "created_at": "2026-09-02T03:15:00Z",
  "app": {
    "version_code": 20260203,
    "version_name": "2026.2.3"
  },
  "database": {
    "schema_version": 4,
    "size": 1048576,
    "sha256": "..."
  },
  "preferences": {
    "format_version": 1,
    "size": 8192,
    "sha256": "..."
  },
  "files": [
    {
      "name": "<sha256>",
      "size": 123456,
      "sha256": "<sha256>"
    }
  ],
  "excluded": [
    "request_logs",
    "cache",
    "webdav_credentials"
  ],
  "warnings": []
}
```

编码规则：

- `format_version` 只在归档结构或语义变化时递增，与 Room schema version 分开。
- 所有大小使用 64 位整数。
- manifest 中的哈希覆盖对应条目的原始字节。
- 写归档前先计算数据库、偏好和资源的大小与哈希；`manifest.json` 必须是 ZIP 的第一个且唯一一个 manifest 条目。
- 恢复时拒绝未知关键格式版本、重复路径、绝对路径、`..`、反斜杠逃逸和超出限制的条目。
- 解密后先读取并校验 manifest，再接受任何数据条目；结合远端大小、本地剩余空间、条目数和解压总量上限执行配额检查，防止 ZIP bomb 和磁盘耗尽。

### 偏好快照

`BackupPreferenceCodec` 从 `AppModel.preferences.all` 读取支持的 SharedPreferences 类型，并写入显式类型标签：

```json
{
  "format_version": 1,
  "values": {
    "userName": { "type": "string", "value": "User" },
    "streamEnabled": { "type": "boolean", "value": true },
    "summaryWordsLimit": { "type": "int", "value": 500 }
  }
}
```

处理策略：

- 包含用户设置、选择状态和业务升级检查点，保证原始数据库与跨存储迁移状态一致。
- 排除 `llmRoutingInstallationId` 等安装级随机标识，恢复后重新生成。
- 备份设置、WebDAV 凭据和归档口令使用独立存储，不会进入该文件。
- 恢复时先清除 `AppModel` 已有键，再应用归档值，使备份中不存在的新设置回落到当前版本默认值。
- 未知 SharedPreferences 类型立即失败，避免静默遗漏。
- 恢复后校验 Provider ID、头像 UUID 等跨存储引用；失效引用使用现有业务默认策略修正并记录警告。

---

## 一致性数据库快照

### 禁止直接复制活动数据库

Room 在 Android 上通常使用 WAL。单独复制 `primary.sqlite`，或者按顺序复制 `primary.sqlite`、`-wal` 和 `-shm`，都不能保证这些文件来自同一个时间点。

`VACUUM INTO` 可以生成一致快照，但该语法依赖 SQLite 版本，不能作为 minSdk 26 的唯一方案。首选方案是使用当前 Room 2.8.4 的 `SupportSQLiteDatabase` 能力，在单一源连接事务中复制到临时数据库。

### 推荐快照算法

`BackupSnapshotRepository` 执行以下流程：

- 在 `noBackupFilesDir/backup-staging/<operationId>/` 创建任务目录。
- 使用相同 `AppDatabase` 类创建空的临时数据库，并强制使用非 WAL journal mode。
- 关闭临时 Room 实例，确保 schema、`room_master_table` 和文件状态稳定。
- 从源 `AppDatabase.openHelper.writableDatabase` 获取同一个 `SupportSQLiteDatabase` 连接。
- 将临时数据库 `ATTACH` 为固定、不可由用户输入的 schema 名。
- 开启事务，在第一条源库读取后固定一致的读快照。
- 按外键父表到子表的固定顺序执行 `INSERT INTO backup.table SELECT * FROM main.table`。
- 成功提交后 `DETACH`，关闭临时数据库的所有句柄。
- 对临时数据库执行 `PRAGMA quick_check`、`PRAGMA foreign_key_check` 和 Room schema identity 校验。
- 读取源快照中的 `files` 表，收集唯一 hash 对应的物理资源。
- 对每个资源重新计算 SHA-256，并与内容寻址文件名比较。
- 读取并编码 AppModel 偏好，校验关键跨存储引用。
- 将数据库、偏好和文件流式写入加密归档。
- 无论成功、失败或取消，都在 `finally` 中删除明文暂存数据库和 sidecar。

该方式在主库为 WAL 时不保证源库与附加目标库的跨文件提交在主机崩溃时整体原子，但这里不会修改源库，目标又是一次性暂存文件。因此可通过“提交完成后完整性检查，失败或进程中止时丢弃暂存文件”消除该风险。

### 表覆盖保护

维护显式 `BACKUP_TABLES`，并在运行时和测试中与 `sqlite_master` 的业务表集合比较：

- 排除 `android_metadata`、`room_master_table`、`sqlite_sequence` 和 SQLite 内部表。
- 新增 Room Entity 后如果忘记加入备份顺序，备份必须明确失败，不能静默漏表。
- 测试需要校验固定表顺序满足当前所有外键依赖。
- 自增序列由显式主键插入结果验证；必要时同步校验 `sqlite_sequence`。

### 并发策略

- `BackupCoordinator` 使用进程内 `Mutex`，避免手动备份、自动备份和恢复并发。
- 暂存目录同时持有 `FileChannel.tryLock()`，避免受控重启或异常多进程造成重复操作。
- 源库事务提供表间一致读视图，并允许 WAL 下其他正常写入继续进行。
- 资源文件为内容寻址且写入后不可变；如果快照记录对应的文件在收集时不存在，重新尝试一次快照。
- 重试后仍缺失时以 `SnapshotFailed` 结束，不生成或上传不完整归档；UI/自动任务状态仅显示缺失数量，不记录文件名或路径。
- SharedPreferences 与 Room 无法组成同一事务；编码后校验 Provider ID、头像 UUID 等关键引用，并依赖现有无效引用清理逻辑兜底。

### P0 技术门禁

在开发 UI 和 WebDAV 前，先完成独立快照原型。只有同时满足以下条件才进入后续阶段：

- API 26 和 API 36 均能生成 Room 可重新打开的临时数据库。
- 20 个主库 Entity 的记录数和关键关联与源快照一致。
- 快照期间并发写入不会进入一半状态，且不会损坏源库。
- 进程在复制、提交和校验阶段被终止后，源库保持完好，暂存文件会在下次启动清理。
- `quick_check`、`foreign_key_check` 和 Room schema identity 均通过。
- 新增测试 Entity/table 时，表覆盖校验会按预期失败。

如果 `ATTACH` 方案无法在当前 Room 连接模型下稳定通过门禁，则回退到“单个 Room 读事务 + 分表流式逻辑归档”。不得回退为复制活动数据库文件。

---

## WebDAV 客户端

### 支持的方法

| 方法 | 用途 | 关键请求头 |
|---|---|---|
| `PROPFIND` | 测试连接、读取目录和备份元数据 | `Depth: 0/1` |
| `MKCOL` | 逐级创建 `RPClient/v1/...` 目录 | 无请求体 |
| `PUT` | 上传探测文件和加密归档 | 已知 `Content-Length`、`If-None-Match: *` |
| `MOVE` | 将 `.partial` 改名为最终归档 | `Destination`、`Overwrite: F` |
| `GET` | 下载归档 | 校验 `Content-Length`，流式写入 |
| `DELETE` | 删除探测文件、过期备份和陈旧临时文件 | 按资源 URL |

不需要 `LOCK`：每次上传使用唯一临时名和唯一最终文件名，配合条件请求避免覆盖。

### 连接测试

“测试连接”不能只判断 URL 可访问，需要验证实际备份权限：

- 规范化 endpoint，并拒绝 fragment、URI user-info 和非 HTTPS scheme。
- 使用 `PROPFIND Depth: 0` 验证基础目录和认证。
- 按路径段逐级 `MKCOL`；RFC 4918 要求父目录先存在。
- `PUT` 一个随机、无用户数据的小型探测文件。
- `PROPFIND` 或 `HEAD/GET` 验证探测文件可见。
- `DELETE` 探测文件，并在失败时记录可清理的远端路径。
- 只在所有读写步骤通过后标记连接可用。

### 上传提交

```text
生成本地加密归档
    │
    ├── PUT 随机 .partial
    │       └── 失败：保留或清理陈旧临时文件，按错误分类重试
    │
    ├── PROPFIND/响应头校验远端长度
    │
    ├── MOVE 到唯一 .rpbackup，Overwrite: F
    │       └── MOVE 不可用：回退为最终名 PUT + 长度校验 + 失败删除
    │
    ├── 标记最近成功状态
    │
    └── 成功后执行保留策略
```

要求：

- 从本地文件创建已知长度的流式 RequestBody，避免将归档读入内存。
- 上传成功只代表服务端接受内容；最终真实性在恢复时由 AES-GCM 和 manifest 哈希校验。
- `ETag` 只作为远端资源版本标识，不能当作内容 MD5。
- 使用 `If-None-Match: *` 和 `Overwrite: F` 防止意外覆盖同名资源。
- 保留策略只在新归档成功提交后运行。
- 只删除名称、目录和格式均符合 RPClient 规则的资源。

### 列表解析

`PROPFIND Depth: 1` 请求以下属性：

- `DAV:resourcetype`
- `DAV:getcontentlength`
- `DAV:getlastmodified`
- `DAV:getetag`
- `DAV:displayname`

`WebDavXmlParser` 使用流式 XML 解析：

- 识别命名空间，不依赖服务器使用的前缀。
- 处理相对和绝对 `href`，规范化后必须仍位于配置目录内。
- 处理一个 `response` 中多个 `propstat` 和内部 HTTP status。
- 限制响应体大小、响应数量和文本长度。
- 禁止外部实体和文档类型声明。
- 忽略未知属性，但不忽略资源级错误状态。

### 认证和重定向

- 首版只支持 Basic 用户名和密码/应用专用密码。
- Basic 凭据本身只是 Base64，必须使用 HTTPS。
- 认证头只能发送给用户配置的 origin。
- 跨 scheme、host 或 port 的重定向不得自动携带 `Authorization`。
- 同 origin 重定向需要限制次数并重新验证目标路径。
- 日志只允许记录脱敏 host、方法、稳定错误码和耗时。
- 当前 [`network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml) 信任系统及用户证书，因此自签服务建议由用户正确安装 CA；首版不提供“忽略证书错误”。

### HTTP 状态分类

| 状态 | 分类 | Worker 行为 |
|---:|---|---|
| `200/201/204/207` | 成功 | 继续流程；`207` 必须解析内部状态 |
| `400` | 配置或兼容错误 | 失败，提示检查地址和服务支持 |
| `401/403` | 认证或权限错误 | 失败，不在同一次任务内重试 |
| `404` | 资源不存在 | 创建目录或提示备份已删除 |
| `409` | 父目录缺失或目标冲突 | 补建父目录后限次重试 |
| `412` | 条件请求冲突 | 生成新文件名后限次重试 |
| `423` | 资源被锁定 | 指数退避重试 |
| `429` | 服务限流 | 尊重 `Retry-After`，否则指数退避 |
| `507` | 远端空间不足 | 失败，提示用户释放空间 |
| `5xx` | 服务端暂时错误 | 指数退避重试 |
| IO/超时 | 网络暂时错误 | 指数退避重试 |

错误响应体最多读取固定小容量用于兼容诊断，不向 UI 或日志展示完整 HTML/XML。

---

## 后台调度

### WorkManager

使用唯一周期任务：

```kotlin
const val UNIQUE_PERIODIC_BACKUP_WORK = "rpclient_webdav_periodic_backup"
const val UNIQUE_MANUAL_BACKUP_WORK = "rpclient_webdav_manual_backup"
```

`BackupScheduler` 负责：

- 启用或修改计划时更新唯一 `PeriodicWorkRequest`。
- 关闭计划、凭据失效或恢复开始前取消周期任务。
- “立即备份”提交唯一 `OneTimeWorkRequest`，防止连续点击并发上传。
- 将触发来源、是否允许移动网络等非敏感信息放入 Work `Data`。
- 由 Worker 从 `BackupCredentialStore` 读取凭据，绝不通过 Work `Data` 传递密码。
- 使用指数退避处理可重试错误。

### 约束映射

| 用户设置 | WorkManager 约束 |
|---|---|
| 仅非计费网络 | `NetworkType.UNMETERED` |
| 任意网络 | `NetworkType.CONNECTED` |
| 仅充电时 | `setRequiresCharging(true)` |
| 电量不低 | `setRequiresBatteryNotLow(true)` |
| 存储空间不低 | `setRequiresStorageNotLow(true)` |

不提供“每天 03:00 准点”之类承诺。PeriodicWorkRequest 最短周期为 15 分钟，实际执行还会受 Doze、待机分桶、厂商限制和约束满足时间影响。

### 长任务

普通 WorkManager 任务应尽量在十分钟内完成。归档较大或网络较慢时：

- `BackupWorker` 使用 `CoroutineWorker` 并正确响应取消。
- 预计可能超时的任务调用 `setForeground()`，显示可取消的上传/下载通知。
- Manifest 声明 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`，并合并 WorkManager 的 `SystemForegroundService` 类型。
- Android 13+ 根据通知体验评估 `POST_NOTIFICATIONS`；拒绝通知权限不能导致数据损坏。
- Android 16 长时间 Worker 仍受 JobScheduler 配额影响，任务必须支持停止后从头重试。
- 通用 WebDAV 首版没有标准分块续传；被停止的 `.partial` 上传从头开始。

### Worker 生命周期

```text
读取并验证设置/凭据
    ├── 无配置：failure，关闭无效计划
    ├── 本地锁被占用：retry
    ▼
创建一致快照
    ▼
生成加密归档
    ▼
上传并提交
    ▼
执行保留策略
    ▼
保存脱敏结果并清理本地暂存
```

`doWork()` 的所有退出路径都必须清理明文快照。`isStopped`、协程取消和 OkHttp Call 取消需要贯通。

---

## 恢复流程

### 恢复语义

首版只支持“整包替换”：

- 主库、AppModel 用户偏好和私有资源恢复到同一备份快照。
- 当前请求日志不随归档恢复，可在恢复时保留或清空；推荐清空，避免时间线混淆。
- 当前 WebDAV 连接和归档口令缓存保留，不从归档覆盖。
- 不提供按角色、按会话或按设置合并；这类需求继续使用现有领域导入导出格式。

### 下载与预检

- 从 WebDAV 列表选择备份，或通过系统文件选择器选择本地 `.rpbackup`。
- 下载到 `noBackupFilesDir/restore-staging/<operationId>.partial`。
- 校验远端声明大小和实际下载大小，并在写入时限制最大字节数。
- 用户输入归档口令，解密到受限 ZIP 读取器。
- 校验容器版本、GCM 标签、manifest、全部 SHA-256、条目数量、路径和总大小。
- 数据库 schema version 大于当前 `AppDatabase` 时拒绝恢复并提示升级应用。
- 解密后要求第一个 ZIP 条目为唯一的 `manifest.json`，先校验其格式和声明配额，再把后续条目流式写入受限 staging 目录。
- 使用独立临时 Room 实例打开快照数据库，执行可用 migration、Room schema 校验、完整的 `PRAGMA integrity_check` 和 `PRAGMA foreign_key_check`。
- 检查恢复所需空间，并为当前数据生成可回滚副本。
- 展示备份时间、应用版本、数据大小、文件数量和警告，由用户再次确认全量替换。

### 暂存和受控重启

运行中的 Koin Repository 和 DAO 持有当前 Room 实例，不能直接替换文件。恢复采用启动前切换：

```text
BackupActivity 完成下载、解密、迁移和校验
    │
    ├── 写入 pending-restore.json
    ├── 保留已校验 staging 数据
    ├── 取消并等待备份 Worker
    └── 用户确认后触发受控进程重启

RPClientApp.onCreate
    │
    ├── PendingRestoreManager 在 Koin/Room 之前交换数据库和资源目录
    ├── Kotpref.init
    ├── 应用归档中的 AppModel 偏好
    ├── startKoin
    ├── AppUpgradeManager.upgrade
    ├── 打开并验证主库
    └── 成功后删除 rollback；失败则恢复旧数据
```

### 文件切换

`PendingRestoreManager` 不依赖 Koin、DAO 或 Activity，只使用 Application Context 和明确路径：

- 在同一应用私有文件系统内准备 staging 和 rollback，确保 rename 不跨卷。
- 备份当前 `primary.sqlite`、`-wal`、`-shm`、journal 和 `repository` 目录。
- 将已关闭、无 sidecar 的 staging 数据库切换为 `primary.sqlite`。
- 将 staging 资源目录切换为 `getDir("repository")` 对应目录。
- 在 Kotpref 初始化后应用偏好，并同时保留旧偏好回滚文件。
- 设置 `APPLIED_AWAITING_VALIDATION` 标记，直到 Koin、业务升级和数据库打开全部成功。
- 失败时停止新依赖图，恢复旧数据库、资源和偏好，再清除 pending 状态。
- 连续恢复失败时进入安全模式，保留 rollback 并提示用户，不得无限重启。

受控重启属于用户已确认的恢复动作，需要独立封装并写清原因；不能散落调用 `killProcess`。

### 启动顺序调整

当前 [`RPClientApp.kt`](../../app/src/main/java/me/kafuuneko/rpclient/RPClientApp.kt) 先初始化 Kotpref 和 Koin，再通过 `AppUpgradeManager` 打开主库。实现恢复后调整为：

```text
清理废弃 staging
→ 检查并应用文件级 pending restore
→ Kotpref.init
→ 应用 pending preferences
→ startKoin
→ AppUpgradeManager.upgrade
→ 验证恢复结果并提交或回滚
→ 恢复 WorkManager 计划
```

该改动必须使用启动与恢复集成测试保护，避免破坏普通冷启动和现有业务升级。

---

## 页面与 MVI 设计

### Main 设置入口

在全局设置页的数据管理区域新增“备份与恢复”Tile：

- `MainUiIntent.BackupSettingsClick`
- `MainViewEvent.OpenBackupSettings`
- `MainActivity` 负责打开 `BackupActivity`

现有聊天导入面板继续只负责聊天导入，不和全量备份状态合并。

### Backup UiState

```kotlin
sealed class BackupUiState {
    data object None : BackupUiState()

    data class Normal(
        val connectionState: BackupConnectionState,
        val scheduleState: BackupScheduleState,
        val operationState: BackupOperationState,
        val latestState: BackupLatestState,
        val remoteListState: BackupRemoteListState,
        val dialogState: BackupDialogState
    ) : BackupUiState()

    data class Finished(val previous: BackupUiState) : BackupUiState()
}
```

`BackupOperationState` 至少表达：

- `Idle`
- `PreparingSnapshot`
- `Encrypting`
- `Uploading(bytesSent, totalBytes)`
- `Downloading(bytesRead, totalBytes?)`
- `Validating`
- `WaitingForRestoreConfirmation`
- `ApplyingRestore`

敏感值不进入 UiState。密码输入草稿只存在于对应对话框受控状态或 ViewModel 私有快照，操作结束立即清理引用。

### 主要 UiIntent

- `Init`
- `Resume`
- `Back`
- `EditConnectionClick`
- `ChangeEndpoint`
- `ChangeUsername`
- `ChangePassword`
- `ChangeRemoteRoot`
- `TestConnectionClick`
- `SaveConnectionClick`
- `BackupNowClick`
- `ExportLocalBackupClick`
- `ImportLocalBackupClick`
- `ImportLocalBackupResult`
- `RefreshRemoteBackups`
- `SelectSchedule`
- `ToggleUnmeteredOnly`
- `ToggleChargingOnly`
- `ChangeRetentionCount`
- `RestoreBackupClick`
- `ConfirmRestore`
- `DeleteRemoteBackupClick`
- `ConfirmDeleteRemoteBackup`
- `DismissDialog`

### 主要 ViewEvent

- `OpenLocalBackupExporter(fileName)`
- `OpenLocalBackupImporter`
- `RequestNotificationPermission`
- `RestartForRestore`

Activity 只处理系统文件选择器、通知权限和受控重启入口。归档解析、文件复制、网络请求和业务判断全部位于 ViewModel 以下的数据层。

### 页面信息层级

```text
BackupPage
├── ConnectionPanel
│   ├── Endpoint summary
│   ├── Connection status
│   └── Edit / Test
├── ManualActionsPanel
│   ├── Back up now
│   ├── Export local backup
│   └── Import local backup
├── SchedulePanel
│   ├── Frequency
│   ├── Network constraint
│   ├── Charging constraint
│   └── Retention
├── LatestResultPanel
│   ├── Last success
│   ├── Last attempt
│   └── Warning or error summary
├── RemoteBackupsPanel
│   └── Backup rows / restore / delete
└── DialogHost
```

文案必须持续强调：

- 备份直接发送到用户配置的 WebDAV。
- RPClient 项目方不提供服务器，也无法访问备份。
- 忘记归档口令将无法恢复。
- 恢复会整体替换当前业务数据和设置。
- 自动任务不是准点任务。

---

## 错误模型与可观测性

### 稳定错误码

建议使用领域错误而不是把异常文本直接传到 UI：

```kotlin
enum class BackupErrorCode {
    InvalidConfiguration,
    AuthenticationFailed,
    CertificateRejected,
    ServerIncompatible,
    RemoteNotFound,
    RemoteQuotaExceeded,
    LocalStorageLow,
    SnapshotFailed,
    ArchiveCorrupted,
    WrongPassphraseOrCorrupted,
    UnsupportedBackupVersion,
    BackupFromNewerApp,
    UploadInterrupted,
    DownloadInterrupted,
    RestoreValidationFailed,
    RestoreRolledBack,
    Unknown
}
```

### 日志边界

允许记录：

- 操作 ID、触发来源、稳定错误码、耗时、字节数和重试次数。
- 脱敏后的 host，不包含 query、user-info 和完整远端路径。
- 数据库 schema version、归档 format version 和校验阶段。

禁止记录：

- WebDAV 用户名、密码、Authorization header 和归档口令。
- API Key、自定义请求头、对话内容、角色内容和 Prompt。
- 完整 WebDAV 响应体、私有本地路径和解密后的 manifest 内容。
- 加密派生密钥、Keystore 密钥、nonce 与密文组合的调试转储。

调试请求日志数据库同样不能记录 WebDAV 请求；该数据库用于 LLM 调试，不应扩大职责。

---

## 安全与隐私

### 威胁模型

| 威胁 | 控制措施 |
|---|---|
| WebDAV 服务端读取备份 | 客户端 AES-256-GCM 整包加密 |
| 网络窃听 Basic 凭据 | 强制 HTTPS，不提供忽略证书错误 |
| 远端文件被篡改 | GCM 认证标签、manifest SHA-256、严格格式校验 |
| 恶意 ZIP 路径穿越 | 规范化相对路径、拒绝重复和越界路径 |
| ZIP bomb / 超大响应 | manifest 配额、总大小/条目数/响应体上限、空间预检 |
| 本机凭据被普通备份恢复但 Keystore 丢失 | 排除凭据偏好；解密失败时清理连接设置 |
| Worker 数据或日志泄密 | 密码不进 Work Data、UiState、通知和日志 |
| 恢复中断损坏当前数据 | staging、rollback、启动前切换、启动后提交 |
| 多设备互相覆盖 | 安装级目录、唯一文件名、条件请求 |
| 用户忘记口令 | 明确提示并要求确认；项目方不提供找回 |

### Privacy Policy 调整

实现前更新 [`PrivacyPolicy.md`](PrivacyPolicy.md)，至少说明：

- 用户可主动配置第三方 WebDAV 服务。
- RPClient 会按用户指示把加密备份直接发送到该服务。
- WebDAV 地址、用户名和密码在本机处理；密码使用设备密钥保护。
- 备份可能包含对话、角色、故事、模型配置、API Key 和用户偏好。
- 项目方不运营中转服务器，不接收、存储或控制这些备份。
- 远端保留和删除受用户选择及 WebDAV 服务条款影响。
- 用户可随时关闭计划、删除连接信息和删除远端归档。

应用商店 Data Safety 声明也要根据“用户主动配置第三方服务并直接传输”的最终实现重新核对。

---

## 兼容与迁移

### 归档兼容

- `format_version` 控制容器和 ZIP 语义。
- `database.schema_version` 控制 Room 结构。
- 旧归档恢复到新应用时，先在 staging 数据库运行 Room migration，再进入切换流程。
- 数据库版本高于当前应用时拒绝恢复，不尝试 destructive downgrade。
- 每次格式升级都保留脱敏的旧版本 golden archive 测试资产。
- 解析器允许未知非关键 manifest 字段，但拒绝未知关键版本或缺失必要字段。

### 应用业务升级

App Upgrade 跨越 Room 和 AppModel。备份应保留：

- `lastMigratedVersionCode`
- `lastCleanedUpgradeVersionCode`

恢复后仍通过现有 `AppUpgradeManager` 运行当前应用尚未完成的后续升级。任何新的跨存储升级都必须验证：

- 从升级前归档恢复后可以正确执行。
- 从升级后归档恢复不会重复导入或清理有效数据。
- 失败检查点与 rollback 不产生相互矛盾的状态。

### 备份配置迁移

`BackupSettingsStore` 和 `BackupCredentialStore` 各自带独立版本。凭据格式升级遵循：

- 先解密旧值并写入新 alias。
- 新值提交成功后再删除旧 alias。
- 失败时保留旧值并关闭自动备份，不能静默丢密码。
- 凭据存储永远不进入 `.rpbackup`。

---

## 测试方案

### 纯单元测试

优先覆盖具有协议和安全语义的纯逻辑：

- WebDAV endpoint 和远端路径规范化。
- `207 Multi-Status` 命名空间、相对 href、多 propstat 和内部状态解析。
- HTTP 状态到领域错误和 retry/failure 的分类。
- 备份文件名解析、排序、安装目录隔离和保留策略。
- manifest 编解码、必填字段、大小上限和未知版本拒绝。
- PBKDF2/AES-GCM 正确解密、错误口令、密文篡改、头部篡改和 nonce 唯一性。
- ZIP 路径穿越、重复 entry、超大声明、条目数超限和哈希不匹配。
- SharedPreferences 支持类型的编解码及排除键策略。
- 跨存储引用校验规则。

使用 MockWebServer 覆盖：

- Basic Authorization 仅发往配置 origin。
- 跨 host 重定向不携带 Authorization。
- `PROPFIND`、`MKCOL`、`PUT`、`MOVE`、`GET`、`DELETE` 请求头和路径。
- 上传中断、超时、429、507、5xx、MOVE 回退和陈旧 partial 清理。
- 响应体大小限制和恶意 XML。

### Android 集成测试

- 构造覆盖当前 20 个 Entity 的关联数据、AppModel 设置和重复 hash 文件。
- 快照后比较每张表记录、外键、文件字节和关键偏好。
- 快照期间并发创建/删除消息、修改 Provider 和替换头像。
- 在复制、加密、上传、下载、解密、切换和升级阶段模拟取消或进程结束。
- 从数据库 schema 1–4 的测试资产恢复并迁移到当前版本。
- 恢复成功后验证单聊、群聊、故事、角色、世界书、Provider、Regex、Token 统计和头像。
- 恢复失败后验证旧数据库、旧偏好和旧资源目录完整回滚。
- Android Auto Backup 规则确认不会包含凭据、pending restore 和 staging。
- WorkManager TestDriver 验证约束、唯一任务、更新计划、取消和退避。

### 设备与服务验证

设备覆盖：

- API 26，验证最低系统 SQLite、Keystore 和后台行为。
- API 33，验证通知权限。
- API 35，验证 dataSync 前台服务限制。
- API 36，验证 WorkManager/JobScheduler 配额与目标 SDK 行为。

WebDAV 覆盖至少包含：

- 标准 Apache mod_dav 环境。
- Nextcloud/ownCloud 类实现。
- 常见 NAS WebDAV 实现。
- 具有反向代理、子路径和用户安装 CA 的自建环境。

服务兼容测试使用专用测试账号和生成数据，禁止使用真实聊天、真实 API Key 或个人 WebDAV 凭据。

### 手工故障注入

- 上传一半切换飞行模式。
- 上传或恢复时强制停止应用。
- WebDAV 密码过期、权限改为只读、远端空间耗尽。
- 本地空间只够下载但不够 staging/rollback。
- 修改密文任意字节、截断归档、伪造大小和重复 ZIP entry。
- 删除主库引用的资源文件。
- 备份期间持续生成消息或故事内容。
- 恢复后首次启动时让业务升级抛错，确认自动回滚。

---

## 开发阶段

### 阶段 P0：数据库快照原型

交付：

- `BackupSnapshotRepository` 最小原型。
- 主库表集合和外键顺序检查。
- API 26/36 一致性、并发和进程终止集成测试。

退出标准：通过“一致性数据库快照”章节中的全部技术门禁。

预估：2–3 人日。

### 阶段 A：本地加密备份与恢复

交付：

- 归档容器、manifest、偏好和资源文件编解码。
- PBKDF2/AES-GCM 与 Keystore 凭据存储。
- 本地导出、本地导入、恢复预检、受控重启和 rollback。
- `feature/backup` 基础页面。
- 归档安全单测、数据库恢复与跨版本集成测试。

退出标准：完全离线时能够生成、校验和恢复 `.rpbackup`，错误口令或任意中断不损坏当前数据。

预估：7–10 人日。

### 阶段 B：手动 WebDAV

交付：

- WebDAV 配置、连接读写测试和安全凭据保存。
- 远端列表、上传临时提交、下载、删除和手动恢复。
- HTTP/XML 单测与代表性服务兼容测试。
- Privacy Policy 初次更新。

退出标准：项目方无服务器参与，客户端能在至少三类标准实现上完成上传和恢复闭环。

预估：4–6 人日。

### 阶段 C：自动调度与保留策略

交付：

- WorkManager 周期任务和立即备份任务。
- 网络、电量、充电、存储约束和指数退避。
- 长任务通知、取消和 Android 15/16 行为适配。
- 最近结果、历史保留、陈旧 partial 清理。

退出标准：重启设备、离线、受限网络和进程结束后任务能按策略恢复尝试，不并发生成备份。

预估：3–5 人日。

### 阶段 D：发布加固

交付：

- 完整多语言文案和无障碍检查。
- API 26–36、低空间、损坏归档和服务差异验证。
- 使用指南、隐私文档、故障排查和备份格式说明。
- golden archive 兼容资产和发布回归清单。

退出标准：所有验收标准完成，不存在会静默生成不可恢复归档的已知路径。

预估：2–4 人日。

---

## 验收标准

### 成本与数据路径

- RPClient 不依赖项目方服务器、账号、对象存储、API 网关或遥测服务。
- 网络抓包可确认归档从 Android 客户端直连用户配置的 WebDAV。
- 项目维护者无法读取、找回或删除用户的远端备份。

### 完整性

- 主库当前全部 20 个 Entity 被快照覆盖；新增 Entity 未登记时备份明确失败。
- AppModel 支持类型被完整编码，安装级随机 ID 和备份凭据按规则排除。
- 主库文件记录对应的私有资源完成 SHA-256 校验。
- 请求日志、缓存、临时文件和 WebDAV 凭据不进入归档。
- manifest、数据库、偏好和资源任意字节损坏都会在恢复前被发现。

### 安全

- 不存在明文远端备份模式。
- Basic 认证仅通过 HTTPS 发送，跨 origin 重定向不泄露认证头。
- WebDAV 密码和归档口令不进入 Work Data、UiState、通知、日志或 Android Auto Backup。
- 错误口令、Keystore 失效和恶意归档不会导致崩溃循环或当前数据损坏。

### 可靠性

- 活动数据库不会被直接复制或在线覆盖。
- 备份任务中断只留下可安全清理的 staging/partial，不影响主库。
- 恢复在启动前切换数据，并在应用初始化失败时自动回滚。
- 同一时间最多有一个备份或恢复操作。
- 新归档成功提交前不会执行远端历史清理。

### 用户体验

- 页面明确说明“连接用户已有 WebDAV，RPClient 不提供服务器”。
- 页面显示最近尝试、最近成功、归档大小和脱敏错误原因。
- 恢复前展示备份元数据并二次确认整体替换。
- 用户能够关闭自动任务、清除连接信息、删除远端备份和取消长任务。
- UI 不承诺周期任务在某个精确时间执行。

---

## 主要风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| Room/WAL 快照原型不稳定 | 无法生成可信数据库副本 | P0 先行；失败则改为单事务逻辑流式归档 |
| 大归档超过 Worker 时间或配额 | 自动上传反复中断 | 非计费网络、前台通知、可取消、从头幂等重试、明确大文件限制 |
| WebDAV 实现差异 | 某些服务无法列表或 MOVE | 严格基于 RFC、流式 XML、MOVE 回退、服务兼容矩阵 |
| 用户忘记归档口令 | 远端备份永久无法解密 | 首次设置强提醒、确认输入、允许用户主动更换后生成新备份链 |
| 新旧应用恢复不兼容 | Room 打不开或数据丢失 | schema version 预检、staging migration、新备份到旧应用拒绝 |
| 恢复中断 | 当前数据损坏或启动循环 | rollback、状态机、启动后提交、失败安全模式 |
| 临时空间不足 | 备份或恢复失败 | 操作前估算、StorageNotLow、流式加密、失败清理 |
| 凭据随系统备份但 Keystore 丢失 | 自动任务持续失败 | 独立存储并从 Auto Backup 排除；失败即停用并要求重连 |
| 多设备共享目录相互删除 | 误删其他设备备份 | installationId 子目录、唯一名、保留策略限定当前目录 |
| 用户误解为同步 | 覆盖或冲突预期不一致 | 文案明确“完整快照恢复”，不提供自动合并 |

---

## 评审时需要确认的产品决定

推荐在开始阶段 A 前确认以下默认决定：

- 完整备份包含 Provider API Key，因此归档加密不可关闭。
- 请求日志始终排除，Token 使用统计随主库包含。
- 首版只接受 HTTPS；自建服务通过用户安装 CA 解决证书信任。
- 恢复采用整包替换，不提供选择性合并。
- 自动备份默认每日、非计费网络、电量不低、保留 7 份。
- 多设备使用独立目录，不做同步和冲突解决。
- 项目方不建设服务器，也不承诺任何第三方 WebDAV 的免费额度或可用性。

---

## 参考资料

- [RFC 4918: HTTP Extensions for Web Distributed Authoring and Versioning](https://www.rfc-editor.org/rfc/rfc4918.html)
- [RFC 7617: The Basic HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617)
- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- [Android WorkManager](https://developer.android.com/reference/androidx/work/WorkManager)
- [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Define Work Requests and Constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Support for Long-Running Workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Android Cryptography](https://developer.android.com/privacy-and-security/cryptography)
- [Android Keystore System](https://developer.android.com/privacy-and-security/keystore)
- [EncryptedSharedPreferences deprecation and backup warning](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [SQLite Online Backup API](https://www.sqlite.org/backup.html)
- [SQLite ATTACH DATABASE](https://www.sqlite.org/lang_attach.html)
- [SQLite Write-Ahead Logging](https://www.sqlite.org/wal.html)
- [SQLite VACUUM INTO](https://www.sqlite.org/lang_vacuum.html)
- [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work)
- [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
