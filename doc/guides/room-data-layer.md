# Room 数据层规范

本文档说明 Room、DAO、Entity、Repository 和文件记录的层级关系。涉及数据库或本地文件时优先阅读本文档。

---

## 1. 依赖方向

Room 是业务数据的主持久化层，必须按以下依赖方向组织：

```text
Feature ViewModel
    ↓ 只依赖 Repository / Codec / Builder / Runtime
Repository / Manager
    ↓ 持有 AppDatabase，获取 DAO
DAO
    ↓ 读写
Entity
    ↓ 声明在
AppDatabase
```

禁止反向依赖：

1. `Entity` 不依赖 DAO、Repository、ViewModel、Compose。
2. `DAO` 不依赖 Repository、ViewModel、Compose。
3. `Repository` 不依赖 ViewModel、Activity、Compose。
4. `feature/*` 不直接依赖 DAO，必须通过 Repository。
5. Compose 不直接依赖 Room 的任何类型。

---

## 2. 目录职责

```text
libs/room/
├── AppDatabase.kt              # 数据库入口，集中声明 Entity、DAO、Converter
├── Converters.kt               # Room TypeConverter
├── MutableDao.kt               # 通用增删改接口
├── entity/                     # 持久化实体
├── dao/                        # SQL 查询接口
└── repository/                 # 面向业务的仓库
```

当前核心 Entity 包括 `Character`、`Lorebook`、`LorebookEntry`、`ChatSession`、`ChatMessage`、`LLMProvider`、`LLMRequestLog`、`FileEntity`、`GroupChatSession`、`GroupChatMember`、`GroupChatMessage`、`GroupChatSummary`。

`AppDatabase` 只做数据库声明和 DAO 暴露：

```kotlin
@Database(
    entities = [
        Character::class,
        Lorebook::class,
        LorebookEntry::class,
        ChatSession::class,
        ChatMessage::class,
        LLMProvider::class,
        FileEntity::class,
        LLMRequestLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCharacterDao(): CharacterDao
    abstract fun getChatSessionDao(): ChatSessionDao
    abstract fun getChatMessageDao(): ChatMessageDao
}
```

`AppDatabase` 不写业务方法，不格式化 UI 文案，不做文件读写。

---

## 3. Entity

Entity 只描述持久化字段：

```kotlin
@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String,
    val role: LLMMessageRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

规则：

1. Entity 可以包含与持久化强相关的轻量方法。
2. 时间格式化、资源字符串、Markdown 渲染、Prompt 检查展示不放 Entity。
3. 不保存不必要的 API Key、认证 token、真实请求头。
4. 请求日志和错误信息必须在写入前评估脱敏策略。
5. 文件类 Entity 优先保存稳定标识、文件名或 URI 信息，不把可恢复业务逻辑塞进路径字符串。

---

## 4. DAO

DAO 只写 SQL 和基础增删改，不写业务流程：

```kotlin
@Dao
interface ChatMessageDao : MutableDao<ChatMessage> {
    @Query("SELECT * FROM chat_message WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getByChatId(chatId: String): List<ChatMessage>

    @Query("DELETE FROM chat_message WHERE chatId = :chatId")
    suspend fun deleteByChatId(chatId: String)
}
```

通用写入优先继承 `MutableDao<T>`。

DAO 不做：

1. 事务编排。
2. 多 DAO 聚合。
3. 文件复制、删除、导入导出。
4. UI model 构建。
5. Toast、日志展示、错误文案生成。
6. Prompt 构建、世界书激活、Regex 执行。

---

## 5. Repository

Repository 是 Feature/ViewModel 访问 Room 的唯一入口。它负责组合 DAO、事务、数据清洗、序列化转换和领域一致性。

简单 Repository：

```kotlin
class ChatRepository(
    appDatabase: AppDatabase
) {
    private val mChatSessionDao = appDatabase.getChatSessionDao()
    private val mChatMessageDao = appDatabase.getChatMessageDao()

    suspend fun getMessages(chatId: String): List<ChatMessage> {
        return mChatMessageDao.getByChatId(chatId)
    }
}
```

跨表 Repository 必须使用事务保证一致性：

```kotlin
class GroupChatRepository(
    private val mAppDatabase: AppDatabase
) {
    private val mSessionDao = mAppDatabase.getGroupChatSessionDao()
    private val mMemberDao = mAppDatabase.getGroupChatMemberDao()
    private val mMessageDao = mAppDatabase.getGroupChatMessageDao()

    suspend fun deleteGroupChat(sessionId: String) {
        mAppDatabase.withTransaction {
            mMessageDao.deleteBySessionId(sessionId)
            mMemberDao.deleteBySessionId(sessionId)
            mSessionDao.deleteById(sessionId)
        }
    }
}
```

事务适用场景：

1. 新建会话并写入首条消息。
2. 删除会话、消息、摘要和成员记录。
3. 创建分支会话并复制消息。
4. 导入角色卡时同时保存角色、头像文件和内嵌 Regex 脚本。
5. 导入世界书时同时写入世界书和条目。
6. 修改会影响多个表的一致性字段。

---

## 6. 聚合数据与 UI model

Repository 可以返回 Entity，也可以返回面向领域的聚合模型；ViewModel 负责把它映射成 UI model。

```kotlin
data class ChatSessionWithCharacter(
    val session: ChatSession,
    val character: Character?,
    val latestMessage: ChatMessage?
)
```

建议：

1. Repository 返回领域数据或聚合数据。
2. ViewModel 根据页面需要构建 `ChatMessageItem`、`CharacterItem` 等 UI model。
3. UI model 不下沉到 DAO。
4. Entity 不直接作为复杂页面的 UiState 列表项，除非字段完全匹配且无隐私风险。

---

## 7. 文件与数据库

RPClient 会保存头像、角色卡 PNG、导入导出文件记录等本地文件，应把文件操作封装在 Repository/Codec/Manager 中。

推荐关系：

```text
系统 Uri / 导入文件
    ↓ Repository/Codec 读取和解析
领域模型 / 文件副本
    ↓ 生成稳定 fileId/name/metadata
FileEntity
    ↓ 被 Character 或其他实体引用
```

规则：

1. 文件保存成功后再写入数据库索引，或使用临时记录并在失败时清理。
2. 数据库写入失败时必须清理临时文件。
3. 导出文件由用户明确触发，文件名应可读但不包含敏感内容。
4. 导入失败时给出脱敏错误，不把完整文件内容或堆栈展示给用户。
5. 文件 Repository 可以依赖 Application Context，不能持有 Activity。

---

## 8. Koin 注册

Room 数据库和 Repository 都在 `RPClientApp.kt` 注册：

```kotlin
private val appModules = module {
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "primary.sqlite")
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }

    singleOf(::CharacterRepository)
    singleOf(::ChatRepository)
    singleOf(::LorebookRepository)
    singleOf(::LLMRepository)
    singleOf(::GroupChatRepository)
}
```

ViewModel 只注入 Repository 或跨模块业务能力：

```kotlin
class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    initStatus = ChatUiState.None
), KoinComponent {
    private val mChatRepository by inject<ChatRepository>()
    private val mPromptBuilder by inject<ChatPromptBuilder>()
}
```

---

## 9. 依赖允许范围

| 类型 | 可以依赖 | 不应依赖 |
|------|----------|----------|
| Entity | Room 注解、基础类型、稳定枚举 | DAO、Repository、ViewModel、Compose |
| DAO | Entity、SQL、Room 注解 | Repository、Context、ViewModel |
| Repository | AppDatabase、DAO、Context、Gson/Codec/Service | Activity、Compose、UiState |
| Codec/Builder/Runtime | Repository、领域模型、协议模型、工具类 | Activity、Compose |
| ViewModel | Repository、Codec/Builder/Runtime、AppModel | DAO、AppDatabase、Context 文件 IO |
| Compose | UiState、UiIntent、UI model | Repository、DAO、Entity、AppModel |

---

## 10. 数据库版本与迁移

1. 新增、删除、修改 Entity 字段时必须更新 `AppDatabase.version`。
2. 开发初期可以 destructive migration，但涉及用户真实数据后必须补 migration。
3. migration 中只处理结构和数据迁移，不写 UI 逻辑。
4. 修改 Converter 会影响已存数据时，必须说明兼容策略。
5. `exportSchema` 是否开启按发布策略决定；需要稳定迁移时建议开启并纳入版本管理。

Room 快速检查：

1. 新表是否有 Entity、DAO、Repository、AppDatabase 注册？
2. Feature 是否只注入 Repository？
3. 跨表读写是否使用 `withTransaction`？
4. 文件与数据库是否有失败回滚或清理策略？
5. 是否避免把 API Key、私密对话、完整请求头和无脱敏原始响应落库？
6. 是否更新数据库版本和迁移策略？
