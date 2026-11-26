package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.map
import me.kafuuneko.rpclient.libs.memory.MemoryConflictException
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryEntry
import me.kafuuneko.rpclient.libs.memory.MemoryPackage
import me.kafuuneko.rpclient.libs.memory.MemoryPackageCodec
import me.kafuuneko.rpclient.libs.memory.MemoryRef
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTable
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.libs.memory.MemoryTargetMissingException
import me.kafuuneko.rpclient.libs.memory.MemoryWriteValidator
import me.kafuuneko.rpclient.libs.memory.PortableMemory
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.MemoryContent
import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import me.kafuuneko.rpclient.libs.room.entity.MemoryRevisionFields
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields

/** 目标标题、合法私有读者和实际人设，供编辑器及授权共同使用。 */
data class MemoryTargetInfo(
    val title: String,
    val characters: List<MemoryOwner>,
    val personaId: Long?,
    val inherit: Boolean = true,
    val recall: Boolean = false,
)

/** 角色身份与展示快照，名称不参与授权。 */
data class MemoryOwner(val id: Long, val name: String)

/** 编辑器可查看的不可变修订及其完整证据。 */
data class MemoryRevisionDetail(
    val fields: MemoryRevisionFields,
    val effectiveAfterMessageId: Long,
    val sources: List<MemorySourceFields>,
)

/**
 * 独立记忆仓库，不依赖 Agent、页面或模型网络请求。
 * - 三族类型化 DAO 共用目标校验、版本比较、来源及修订事务。
 * - 管理者读取与模型可见性分别处理，私有范围始终按实际发言者过滤。
 * - 条目主表不使用 REPLACE，跨会话并发修改由 expectedRevision 仲裁。
 */
class MemoryRepository(private val mDatabase: AppDatabase) {
    private val mStores = mapOf(
        MemoryTable.Character to CharacterMemoryStore(mDatabase.getCharacterMemoryDao()),
        MemoryTable.Chat to ChatMemoryStore(mDatabase.getChatMemoryDao()),
        MemoryTable.Group to GroupChatMemoryStore(mDatabase.getGroupChatMemoryDao()),
    )

    /**
     * 返回含零条角色的目录，角色改名或记忆保存后自动刷新。
     *
     * @return 全部角色及其通用记忆条数的实时流
     */
    fun observeCharacters() = mDatabase.getCharacterMemoryDao().observeCharacters()

    /**
     * 管理页读取当前目标；会话编辑器只展示当前实际人设可用条目。
     *
     * @param target 观察的记忆范围目标（角色、单聊或群聊）
     * @return 按置顶、重要度和主键降序排列的实时条目流
     */
    fun observeEntries(target: MemoryTarget) = mDatabase.invalidationTracker.createFlow(
        "character_memories",
        "chat_memories",
        "group_chat_memories",
        "character_memory_sources",
        "chat_memory_sources",
        "group_chat_memory_sources",
        "character",
        "chat_sessions",
        "group_chat_sessions",
        "group_chat_members",
        "user_personas",
        "user_persona_settings",
    ).map {
        // 每次关联表失效后在事务内复核目标人设并重新排序输出条目列表。
        mDatabase.withTransaction {
            val info = targetInfo(target)
            store(target).list(target.id)
                .filter { entry ->
                    target is MemoryTarget.Character ||
                        entry.fields.personaId == null ||
                        entry.fields.personaId == info.personaId
                }
                .sortedWith(
                    compareByDescending<MemoryEntry> { it.fields.content.pinned }
                        .thenByDescending { it.fields.content.importance }
                        .thenByDescending { it.ref.id },
                )
        }
    }

    /**
     * 返回已持久化的人设选项，禁止把跟随默认占位值写入记忆。
     *
     * @return 已持久化的用户人设选项列表
     */
    suspend fun personas() = mDatabase.getUserPersonaDao().list()

    /**
     * 读取并复核目标，已删除的页面不能继续保存到其他范围。
     *
     * @param target 类别及真实正 ID
     * @return 当前标题、读者与人设
     */
    suspend fun targetInfo(target: MemoryTarget): MemoryTargetInfo = mDatabase.withTransaction {
        require(target.id > 0) { "Invalid memory target" }
        // 每次进入和写入都查询实际所有者，不信任页面持有的旧标题或成员。
        when (target) {
            is MemoryTarget.Character -> {
                // 校验角色仍存在并返回单角色拥有者信息。
                val character = mDatabase.getCharacterDao().getCharacterById(target.id)
                    ?: throw MemoryTargetMissingException()
                MemoryTargetInfo(
                    title = character.name,
                    characters = listOf(MemoryOwner(id = character.id, name = character.name)),
                    personaId = null,
                )
            }

            is MemoryTarget.Chat -> {
                // 校验单聊会话与绑定角色均存在，并解析会话实际人设与记忆开关。
                val session = mDatabase.getChatSessionDao().getSessionById(target.id)
                    ?: throw MemoryTargetMissingException()
                val character = mDatabase.getCharacterDao().getCharacterById(session.characterId)
                    ?: throw MemoryTargetMissingException()
                MemoryTargetInfo(
                    title = session.title,
                    characters = listOf(MemoryOwner(id = character.id, name = character.name)),
                    personaId = persona(session.userPersonaId),
                    inherit = session.inheritCharacterMemory,
                    recall = session.recallMemoryInNormalChat,
                )
            }

            is MemoryTarget.Group -> {
                // 校验群聊会话及全部在群成员仍存在，并解析群聊实际人设与记忆开关。
                val session = mDatabase.getGroupChatSessionDao().getSessionById(target.id)
                    ?: throw MemoryTargetMissingException()
                val characters = mDatabase.getGroupChatMemberDao().getMembers(target.id)
                    .map { member ->
                        val character =
                            mDatabase.getCharacterDao().getCharacterById(member.characterId)
                                ?: throw MemoryTargetMissingException()
                        MemoryOwner(id = character.id, name = character.name)
                    }
                MemoryTargetInfo(
                    title = session.title,
                    characters = characters,
                    personaId = persona(session.userPersonaId),
                    inherit = session.inheritCharacterMemory,
                    recall = session.recallMemoryInNormalChat,
                )
            }
        }
    }

    /**
     * 手动保存条目，正文变更或明确恢复生效会追加独立用户证据。
     *
     * @param target 编辑器目标
     * @param draft 当前正文和元数据
     * @param ref 修改时的完整跨表引用，新建时为空
     * @param expectedRevision 修改所依据的版本，新建时为零
     * @return 提交后的完整条目
     */
    suspend fun save(
        target: MemoryTarget,
        draft: MemoryDraft,
        ref: MemoryRef? = null,
        expectedRevision: Long = 0,
    ): MemoryEntry = saveInternal(target, draft, ref, expectedRevision)

    /** 由工具层提供已经核验的消息证据，避免把模型写入冒充用户手动确认。 */
    internal suspend fun saveAgent(
        target: MemoryTarget, draft: MemoryDraft, ref: MemoryRef?, expectedRevision: Long,
        evidence: List<MemorySourceFields>,
    ): MemoryEntry {
        require(evidence.isNotEmpty()) { "Agent memory requires evidence" }
        evidence.forEach(MemoryWriteValidator::validateSource)
        return saveInternal(target, draft, ref, expectedRevision, evidence)
    }

    private suspend fun saveInternal(
        target: MemoryTarget, draft: MemoryDraft, ref: MemoryRef?, expectedRevision: Long,
        agentEvidence: List<MemorySourceFields>? = null,
    ): MemoryEntry = mDatabase.withTransaction {
        // 前置校验目标、受众、版本号及身份不可在普通编辑中隐式更改。
        require(ref != null || expectedRevision == 0L) { "New memory cannot have a revision" }
        val info = targetInfo(target)
        validateAudience(target, info, draft)
        val dao = store(target)
        val old = ref?.let { owned(target, it) }
        if (old != null && old.fields.currentRevision != expectedRevision) {
            throw MemoryConflictException()
        }
        require(
            old == null || (
                old.fields.personaId == draft.personaId &&
                    old.privateCharacterId == draft.privateCharacterId &&
                    old.overrideCharacterMemoryId == draft.overrideCharacterMemoryId
                ),
        ) { "Memory identity requires explicit scope change" }
        // 组装新旧快照字段并执行共享正文及槽位键校验。
        val now = System.currentTimeMillis()
        val overrideEntry =
            draft.overrideCharacterMemoryId?.let { mStores.getValue(MemoryTable.Character).get(it) }
        val content = MemoryContent(
            body = draft.body,
            kind = draft.kind.name,
            status = draft.status.name,
            importance = draft.importance,
            pinned = draft.pinned,
        )
        // 身份固定后以条件 UPDATE 推进版本，修订与证据失败时整个事务回滚。
        val fields = old?.fields?.copy(
            content = content,
            currentRevision = expectedRevision + 1,
            updatedAt = now,
        ) ?: MemoryFields(
            uuid = UUID.randomUUID().toString(),
            personaId = draft.personaId,
            subjectKey = overrideEntry?.fields?.subjectKey ?: "self",
            factKey = overrideEntry?.fields?.factKey ?: UUID.randomUUID().toString(),
            slotKey = MemoryWriteValidator.slotKey(draft.personaId, draft.privateCharacterId),
            content = content,
            createdAt = now,
            updatedAt = now,
        )
        MemoryWriteValidator.validate(fields, draft.privateCharacterId)
        val id = if (old == null) {
            dao.insert(
                ownerId = target.id,
                privateId = draft.privateCharacterId,
                overrideId = draft.overrideCharacterMemoryId,
                fields = fields,
            )
        } else {
            if (dao.update(old.ref.id, expectedRevision, content, now) != 1) {
                throw MemoryConflictException()
            }
            old.ref.id
        }
        // 新建、正文修改或从非生效恢复为生效时追加独立用户证据，仅改元数据沿用旧证据。
        val existingSources = old?.let { currentSources(it) }.orEmpty()
        val confirmsBody = old == null ||
            old.fields.content.body != draft.body ||
            (old.fields.content.status != MemoryStatus.Active.name && draft.status == MemoryStatus.Active)
        // 同一事实的工具 upsert 合并独立证据；正文修正仍使用本次新依据。
        val evidence = if (agentEvidence != null) {
            val retained = existingSources.filter { it.valid }
                .takeIf { old?.fields?.content?.body == draft.body && draft.status == MemoryStatus.Active }
                .orEmpty()
            (retained + agentEvidence).distinctBy {
                listOf(
                    it.sourceKind,
                    it.sourceKey,
                    it.fingerprint,
                    it.quote
                )
            }
        } else if (confirmsBody) listOf(userEvidence(now)) else existingSources
        appendRevision(
            dao = dao,
            id = id,
            fields = fields,
            boundary = boundary(target),
            sources = evidence,
            origin = if (agentEvidence == null) "UserEdit" else "AgentTool",
            overrideUuid = overrideEntry?.fields?.uuid,
        )
        requireNotNull(dao.get(id))
    }

    /**
     * 在一个事务中捕获当前目标的完整条目、修订及来源。
     *
     * @param target 待导出的记忆范围目标
     * @return 通过格式自检的记忆便携包 JSON 字符串
     */
    suspend fun exportPackage(target: MemoryTarget): String = mDatabase.withTransaction {
        // 读取当前目标的合法拥有者及按会话人设过滤后的记忆列表。
        val info = targetInfo(target)
        val entries = store(target).list(target.id).filter { entry ->
            target is MemoryTarget.Character ||
                entry.fields.personaId == null ||
                entry.fields.personaId == info.personaId
        }
        // 附带局部覆盖目标 UUID 及完整修订历史，封装为便携条目。
        val data = entries.map { entry ->
            val overrideUuid = entry.overrideCharacterMemoryId?.let {
                mStores.getValue(MemoryTable.Character).get(it)?.fields?.uuid
            }
            PortableMemory(
                fields = entry.fields,
                privateId = entry.privateCharacterId,
                overrideUuid = overrideUuid,
                history = history(target, entry.ref),
            )
        }
        // 人设名称仅供导入映射使用，不附带本次条目及历史未引用的设备身份。
        val referencedPersonaIds = data.flatMap { entry ->
            listOfNotNull(entry.fields.personaId) +
                entry.history.mapNotNull { revision -> revision.fields.snapshotPersonaId }
        }.toSet()
        // 编码并自检整个便携包后返回 JSON 快照。
        MemoryPackageCodec.encode(
            MemoryPackage(
                table = table(target),
                ownerId = target.id,
                owners = info.characters.associate { it.id to it.name },
                personas = personas().filter { it.id in referencedPersonaIds }
                    .associate { it.id to it.name },
                entries = data,
            ),
        )
    }

    /**
     * 显式映射目标、人设与成员后原子导入；同 UUID 重导入不增加条目。
     * - 外部消息 ID 保留在来源标识中，绝不直接绑定本机同号消息。
     * - 已有 UUID 的不同正文或范围视作冲突，不静默覆盖本地修改。
     * - 会话修订统一以本机导入时的最新消息为生效边界。
     *
     * @param target 本机导入目标范围
     * @param data 已解析并通过格式校验的便携包
     * @param personaMap 外部人设 ID 到本机人设 ID 的显式映射表
     * @param memberMap 外部角色成员 ID 到本机成员 ID 的显式映射表
     * @return 本次实际新写入的条目数量
     */
    suspend fun importPackage(
        target: MemoryTarget,
        data: MemoryPackage,
        personaMap: Map<Long, Long>,
        memberMap: Map<Long, Long>,
    ): Int = mDatabase.withTransaction {
        // 先复核便携包合法性与目标范围类别一致性。
        MemoryPackageCodec.encode(data)
        require(data.table == table(target)) { "Memory package target type mismatch" }
        val info = targetInfo(target)
        val importBoundary = boundary(target)
        val dao = store(target)
        var count = 0
        // 逐条解析映射关系并原子写入主表、历史修订与降级后的外部证据。
        data.entries.forEach { portable ->
            if (importSingleEntry(
                    target = target,
                    info = info,
                    dao = dao,
                    data = data,
                    portable = portable,
                    personaMap = personaMap,
                    memberMap = memberMap,
                    importBoundary = importBoundary,
                )
            ) {
                count++
            }
        }
        count
    }

    /** 导入单条便携记忆及其修订历史；若同 UUID 已存在且完全一致则跳过并返回 false。 */
    private suspend fun importSingleEntry(
        target: MemoryTarget,
        info: MemoryTargetInfo,
        dao: MemoryStore,
        data: MemoryPackage,
        portable: PortableMemory,
        personaMap: Map<Long, Long>,
        memberMap: Map<Long, Long>,
        importBoundary: Long,
    ): Boolean {
        // 将外部人设、私有成员及覆盖目标 UUID 全部解析为本机真实主键并校验归属。
        val personaId = portable.fields.personaId?.let {
            requireNotNull(personaMap[it]) { "Missing persona mapping" }
        }
        val privateId = portable.privateId?.let {
            requireNotNull(memberMap[it]) { "Missing member mapping" }
        }
        val override = portable.overrideUuid?.let {
            requireNotNull(mStores.getValue(MemoryTable.Character).byUuid(it)) {
                "Missing override target"
            }
        }
        validateAudience(
            target = target,
            info = info,
            draft = MemoryDraft(
                body = portable.fields.content.body,
                personaId = personaId,
                privateCharacterId = privateId,
                overrideCharacterMemoryId = override?.ref?.id,
            ),
        )
        val fields = portable.fields.copy(
            personaId = personaId,
            slotKey = MemoryWriteValidator.slotKey(personaId, privateId),
        )
        MemoryWriteValidator.validate(fields, privateId)
        if (override != null) {
            require(
                fields.subjectKey == override.fields.subjectKey &&
                    fields.factKey == override.fields.factKey,
            ) { "Override fact mismatch" }
        }
        // 同 UUID 只能在同一显式目标中复用，绝不能按名称或不同设备 ID 合并。
        val existing = dao.byUuid(fields.uuid)
        if (existing != null) {
            val isSameRecord = existing.target == target &&
                existing.privateCharacterId == privateId &&
                existing.fields == fields &&
                existing.overrideCharacterMemoryId == override?.ref?.id
            if (!isSameRecord) throw MemoryConflictException()
            return false
        }
        // 写入主表记录，并将外部消息来源降级为 External 保留原始引用轨迹。
        val id = dao.insert(target.id, privateId, override?.ref?.id, fields)
        importPortableHistory(
            dao = dao,
            entryId = id,
            data = data,
            portable = portable,
            personaMap = personaMap,
            memberMap = memberMap,
            importBoundary = importBoundary,
        )
        return true
    }

    /** 按修订顺序写入全部修订和脱敏外部证据，并统一从本次导入边界生效。 */
    private suspend fun importPortableHistory(
        dao: MemoryStore,
        entryId: Long,
        data: MemoryPackage,
        portable: PortableMemory,
        personaMap: Map<Long, Long>,
        memberMap: Map<Long, Long>,
        importBoundary: Long,
    ) {
        portable.history.sortedBy { it.fields.revision }.forEach { revision ->
            // 跨设备消息主键不可直接绑定本机消息，统一降级为 External 并保留原引用路径。
            val evidence = revision.sources.map { source ->
                if (source.sourceKind in setOf("UserEdit", "External", "PromotedSnapshot")) {
                    source
                } else {
                    val externalId =
                        source.chatMessageId ?: source.groupMessageId ?: source.groupSummaryId
                    source.copy(
                        sourceKind = "External",
                        chatMessageId = null,
                        groupMessageId = null,
                        groupSummaryId = null,
                        originRef = "${data.table}/${data.ownerId}/${source.sourceKind}/$externalId/${source.originRef}",
                    )
                }
            }
            // 按显式映射表回填历史快照人设与私有成员，并写入修订与证据表。
            val historicalPersona = revision.fields.snapshotPersonaId?.let {
                requireNotNull(personaMap[it]) { "Missing historical persona mapping" }
            }
            val historicalPrivate = revision.fields.snapshotPrivateCharacterId?.let {
                requireNotNull(memberMap[it]) { "Missing historical member mapping" }
            }
            val revisionId = dao.appendRevision(
                fields = revision.fields.copy(
                    entryId = entryId,
                    snapshotPersonaId = historicalPersona,
                    snapshotPrivateCharacterId = historicalPrivate,
                ),
                boundary = importBoundary,
            )
            dao.appendSources(evidence.map { it.copy(revisionId = revisionId) })
        }
    }

    /**
     * 显式扩大到角色通用范围，用户确认目标、人设和跨会话影响后调用。
     *
     * @param target 原会话范围（单聊或群聊）
     * @param ref 原会话记忆条目引用
     * @param expectedRevision 期望的当前修订号
     * @param characterId 提升后的目标角色主键 ID
     * @return 新创建的角色通用记忆条目
     */
    suspend fun promote(
        target: MemoryTarget,
        ref: MemoryRef,
        expectedRevision: Long,
        characterId: Long,
    ): MemoryEntry = mDatabase.withTransaction {
        // 校验原条目版本、会话成员合法性及私有受众不得跨角色泄露。
        val info = targetInfo(target)
        val source = owned(target, ref)
        if (source.fields.currentRevision != expectedRevision) throw MemoryConflictException()
        require(target !is MemoryTarget.Character && info.characters.any { it.id == characterId }) {
            "Invalid promotion target"
        }
        require(source.privateCharacterId == null || source.privateCharacterId == characterId) {
            "Promotion cannot expose another member's memory"
        }
        // 生成拥有独立 UUID 的角色通用快照并写入主表。
        val destination = MemoryTarget.Character(characterId)
        val now = System.currentTimeMillis()
        val fields = source.fields.copy(
            uuid = UUID.randomUUID().toString(),
            slotKey = MemoryWriteValidator.slotKey(source.fields.personaId, null),
            currentRevision = 1,
            createdAt = now,
            updatedAt = now,
        )
        MemoryWriteValidator.validate(fields, null)
        val dao = store(destination)
        val id = dao.insert(characterId, null, null, fields)
        // 提升确认仅扩大范围，仍复制实际正文证据，不能替代正文的独立确认。
        val evidence = currentSources(source).map {
            it.copy(originRef = "${source.ref.table}/${source.fields.uuid}/${source.fields.currentRevision}")
        }
        appendRevision(dao, id, fields, 0, evidence, "Promotion")
        requireNotNull(dao.get(id))
    }

    /**
     * 显式范围变更使用条件更新，保留条目 UUID 与已保存历史。
     *
     * @param target 条目所属记忆范围
     * @param ref 条目跨表引用
     * @param expectedRevision 期望的当前修订号
     * @param personaId 新适用人设 ID，null 表示不限人设
     * @param privateId 新私有读者角色 ID，null 表示共享或通用
     * @return 范围更新后的完整记忆条目
     */
    suspend fun changeScope(
        target: MemoryTarget,
        ref: MemoryRef,
        expectedRevision: Long,
        personaId: Long?,
        privateId: Long?,
    ): MemoryEntry = mDatabase.withTransaction {
        // 复核原条目归属、版本号及目标新受众的合法性。
        val old = owned(target, ref)
        if (old.fields.currentRevision != expectedRevision) throw MemoryConflictException()
        val draft = MemoryDraft(
            body = old.fields.content.body,
            personaId = personaId,
            privateCharacterId = privateId,
            overrideCharacterMemoryId = old.overrideCharacterMemoryId,
        )
        validateAudience(target, targetInfo(target), draft)
        // 生成新槽位键并以乐观锁条件更新主表记录。
        val now = System.currentTimeMillis()
        val fields = old.fields.copy(
            personaId = personaId,
            slotKey = MemoryWriteValidator.slotKey(personaId, privateId),
            currentRevision = expectedRevision + 1,
            updatedAt = now,
        )
        MemoryWriteValidator.validate(fields, privateId)
        val dao = store(target)
        val updatedRows = dao.changeScope(
            id = ref.id,
            revision = expectedRevision,
            personaId = personaId,
            privateId = privateId,
            slotKey = fields.slotKey,
            now = now,
        )
        if (updatedRows != 1) throw MemoryConflictException()
        val overrideUuid = old.overrideCharacterMemoryId?.let {
            mStores.getValue(MemoryTable.Character).get(it)?.fields?.uuid
        }
        // 通用记录改为特定人设后，旧局部覆盖仍是独立事实，但不再屏蔽其他身份。
        if (target is MemoryTarget.Character && personaId != null && personaId != old.fields.personaId) {
            mDatabase.getChatMemoryDao().detachIncompatibleOverrides(ref.id, personaId)
            mDatabase.getGroupChatMemoryDao().detachIncompatibleOverrides(ref.id, personaId)
        }
        // 追加范围变更修订并延续原条目当前有效证据。
        appendRevision(
            dao = dao,
            id = ref.id,
            fields = fields,
            boundary = boundary(target),
            sources = currentSources(old),
            origin = "ScopeChange",
            overrideUuid = overrideUuid,
        )
        requireNotNull(dao.get(ref.id))
    }

    /**
     * 用户明确确认当前正文，追加独立证据；与置顶、排序等元数据编辑不同。
     *
     * @param target 条目所属记忆范围
     * @param ref 条目跨表引用
     * @param expectedRevision 期望的当前修订号
     */
    suspend fun confirm(target: MemoryTarget, ref: MemoryRef, expectedRevision: Long) =
        mDatabase.withTransaction {
            // 校验目标、条目所有权与并发修订号。
            targetInfo(target)
            val entry = owned(target, ref)
            if (entry.fields.currentRevision != expectedRevision) throw MemoryConflictException()
            val dao = store(target)
            val now = System.currentTimeMillis()
            // 将条目状态置为 Active 并推进修订号。
            val fields = entry.fields.copy(
                content = entry.fields.content.copy(status = MemoryStatus.Active.name),
                currentRevision = expectedRevision + 1,
                updatedAt = now,
            )
            if (dao.update(ref.id, expectedRevision, fields.content, now) != 1) {
                throw MemoryConflictException()
            }
            // 保留现有有效证据并追加独立用户确认证据。
            val evidence = currentSources(entry).filter { it.valid } + userEvidence(now)
            val overrideUuid = entry.overrideCharacterMemoryId?.let {
                mStores.getValue(MemoryTable.Character).get(it)?.fields?.uuid
            }
            appendRevision(
                dao = dao,
                id = ref.id,
                fields = fields,
                boundary = boundary(target),
                sources = evidence,
                origin = "UserConfirmation",
                overrideUuid = overrideUuid,
            )
        }

    /**
     * 成员移除确认展示所有人设、全部状态下将被级联删除的私有条数。
     *
     * @param sessionId 群聊会话主键 ID
     * @param characterId 待移除的角色主键 ID
     * @return 该角色在当前群聊中的全部私有记忆条数
     */
    suspend fun privateMemoryCount(sessionId: Long, characterId: Long): Int =
        store(MemoryTarget.Group(sessionId)).list(sessionId)
            .count { it.privateCharacterId == characterId }

    /**
     * 删除时复核所属目标和版本，防止相同主键误删另一张表或覆盖并发编辑。
     *
     * @param target 条目所属记忆范围
     * @param ref 条目跨表引用
     * @param expectedRevision 期望的当前修订号
     */
    suspend fun delete(target: MemoryTarget, ref: MemoryRef, expectedRevision: Long) =
        mDatabase.withTransaction {
            targetInfo(target)
            owned(target, ref)
            if (store(target).delete(ref.id, expectedRevision) != 1) throw MemoryConflictException()
        }

    /**
     * 读取完整历史，证据只进入显式打开的详情，不进入普通 Prompt。
     *
     * @param target 条目所属记忆范围
     * @param ref 条目跨表引用
     * @return 按修订号降序排列的修订及证据详情列表
     */
    suspend fun history(target: MemoryTarget, ref: MemoryRef): List<MemoryRevisionDetail> =
        mDatabase.withTransaction {
            // 复核目标及条目归属，会话范围仅展示当前会话人设可见的历史修订。
            val info = targetInfo(target)
            owned(target, ref)
            val dao = store(target)
            // 组装各修订记录及其关联的全部证据来源。
            dao.revisions(ref.id).filter { revision ->
                target is MemoryTarget.Character ||
                    revision.fields.snapshotPersonaId == null ||
                    revision.fields.snapshotPersonaId == info.personaId
            }.map { revision ->
                MemoryRevisionDetail(
                    fields = revision.fields,
                    effectiveAfterMessageId = revision.boundary,
                    sources = dao.sources(revision.id),
                )
            }
        }

    /**
     * 单独更新记忆开关，保存会话的旧表单不得覆盖这些字段。
     *
     * @param target 单聊或群聊会话目标
     * @param inherit 是否继承角色通用记忆
     * @param recall 是否在普通回复中启用剩余预算召回
     */
    suspend fun updateSettings(target: MemoryTarget, inherit: Boolean, recall: Boolean) =
        mDatabase.withTransaction {
            targetInfo(target)
            when (target) {
                is MemoryTarget.Chat -> mDatabase.getChatSessionDao()
                    .updateMemorySettings(target.id, inherit, recall)

                is MemoryTarget.Group -> mDatabase.getGroupChatSessionDao()
                    .updateMemorySettings(target.id, inherit, recall)

                is MemoryTarget.Character -> error("Character memory has no conversation switches")
            }
        }

    /**
     * 为实际发言者提供可继承的有效通用记忆，局部覆盖会屏蔽旧通用事实。
     *
     * @param target 当前会话，不能传角色目录目标
     * @param speakerId 实际发言角色
     * @param personaId 本轮已经解析的真实人设
     * @param excludedMessageId 重生成排除的本会话消息
     * @return 权限与证据均通过的通用候选，不含其他会话条目
     */
    suspend fun inherited(
        target: MemoryTarget,
        speakerId: Long,
        personaId: Long,
        excludedMessageId: Long = 0,
    ): List<MemoryEntry> = mDatabase.withTransaction {
        // 前置校验会话开关、实际人设及发言角色成员身份。
        val info = targetInfo(target)
        require(target !is MemoryTarget.Character && personaId > 0) {
            "Invalid conversation reader"
        }
        val canInherit =
            info.inherit && info.personaId == personaId && info.characters.any { it.id == speakerId }
        if (!canInherit) return@withTransaction emptyList()
        val excludedChat = if (target is MemoryTarget.Chat) excludedMessageId else 0
        val excludedGroup = if (target is MemoryTarget.Group) excludedMessageId else 0
        // SQL 在读取正文前先过滤角色、人设及有效来源；局部覆盖只影响明确关联的事实。
        val local = store(target).eligible(
            ownerId = target.id,
            personaId = personaId,
            speakerId = speakerId,
            excludedChatId = excludedChat,
            excludedGroupId = excludedGroup,
        )
        val overridden = local.mapNotNull { it.overrideCharacterMemoryId }.toSet()
        mStores.getValue(MemoryTable.Character)
            .eligible(
                ownerId = speakerId,
                personaId = personaId,
                speakerId = speakerId,
                excludedChatId = excludedChat,
                excludedGroupId = excludedGroup,
            )
            .filterNot { it.ref.id in overridden }
    }

    /** 模型可见的会话及继承记忆，先在 SQL 层按实际发言者与人设过滤。 */
    internal suspend fun visibleToAgent(
        target: MemoryTarget,
        speakerId: Long,
        personaId: Long
    ): List<MemoryEntry> = mDatabase.withTransaction {
        val info = targetInfo(target)
        require(info.personaId == personaId && info.characters.any { it.id == speakerId }) { "Invalid memory reader" }
        val local = store(target).eligible(target.id, personaId, speakerId, 0, 0)
        local + if (info.inherit) inherited(target, speakerId, personaId) else emptyList()
    }

    /**
     * 在消息编辑或硬删除的原事务内撤销来源并复核独立证据。
     *
     * @param chatIds 被替换或删除的单聊消息及摘要
     * @param groupIds 被替换或删除的群聊消息
     * @param summaryIds 被删除或替换的群聊摘要
     * @param hardDelete 是否要求物理清理无支持的派生正文
     */
    suspend fun invalidateSources(
        chatIds: List<Long> = emptyList(),
        groupIds: List<Long> = emptyList(),
        summaryIds: List<Long> = emptyList(),
        hardDelete: Boolean = true,
    ) = mDatabase.withTransaction {
        if (chatIds.isEmpty() && groupIds.isEmpty() && summaryIds.isEmpty()) return@withTransaction
        // 分批避免长会话超过 SQLite 的参数上限，全部撤回后再复核每个条目。
        val chatBatches = chatIds.chunked(250)
        val groupBatches = groupIds.chunked(250)
        val summaryBatches = summaryIds.chunked(250)
        mStores.values.forEach { dao ->
            val affected = linkedMapOf<MemoryRef, MemoryEntry>()
            repeat(maxOf(chatBatches.size, groupBatches.size, summaryBatches.size)) { index ->
                val chat = chatBatches.getOrNull(index).orEmpty()
                val group = groupBatches.getOrNull(index).orEmpty()
                val summary = summaryBatches.getOrNull(index).orEmpty()
                dao.affected(chat, group, summary).forEach { affected[it.ref] = it }
                if (hardDelete) {
                    dao.clearSources(chat, group, summary)
                } else {
                    dao.invalidateSources(chat, group, summary)
                }
            }
            // 编辑后保留失效来源的外键，之后删除原消息时仍能清理无支持的正文。
            affected.values.forEach { entry ->
                val remaining = currentSources(entry)
                if (remaining.none { it.valid }) {
                    if (hardDelete) {
                        dao.delete(entry.ref.id, entry.fields.currentRevision)
                    } else if (entry.fields.content.status != MemoryStatus.Invalidated.name) {
                        invalidateUnsupported(dao, entry, remaining)
                    }
                }
                if (hardDelete) dao.pruneUnsupportedRevisions(entry.ref.id)
            }
        }
    }

    /**
     * 在单聊原事务内处理消息证据及即将被删除的摘要证据。
     *
     * @param id 发生编辑或删除的单聊消息 ID
     * @param hardDelete 是否为硬删除操作
     */
    suspend fun chatMessageChanged(id: Long, hardDelete: Boolean = false) =
        mDatabase.withTransaction {
            val message = mDatabase.getChatMessageDao().getMessageById(id) ?: return@withTransaction
            invalidateSources(chatIds = listOf(id), hardDelete = hardDelete)
            val summaries = mDatabase.getChatMessageDao().memorySummaryIds(message.sessionId, id)
            invalidateSources(chatIds = summaries, hardDelete = true)
        }

    /**
     * 群聊消息与摘要有不同表身份，不能将相同主键混作同一来源。
     *
     * @param id 发生编辑或删除的群聊消息 ID
     * @param hardDelete 是否为硬删除操作
     */
    suspend fun groupMessageChanged(id: Long, hardDelete: Boolean = false) =
        mDatabase.withTransaction {
            val message =
                mDatabase.getGroupChatMessageDao().getMessageById(id) ?: return@withTransaction
            invalidateSources(groupIds = listOf(id), hardDelete = hardDelete)
            invalidateSources(
                summaryIds = mDatabase.getGroupChatSummaryDao()
                    .memorySummaryIds(message.sessionId, id),
            )
        }

    /**
     * 移出群成员前冻结已提升的本群证据，角色通用记忆不再依赖当前群成员资格。
     *
     * @param sessionId 即将退出的群聊会话 ID
     * @param characterId 即将移出的角色 ID
     */
    suspend fun groupMemberRemoved(sessionId: Long, characterId: Long) = mDatabase.withTransaction {
        mDatabase.getCharacterMemoryDao().snapshotGroupSources(sessionId, characterId)
    }

    /**
     * 删除会话之前清理仍被其他范围引用的来源，随后所有者外键负责本会话级联。
     *
     * @param target 即将被删除的单聊或群聊会话目标
     */
    suspend fun conversationDeleted(target: MemoryTarget) = mDatabase.withTransaction {
        when (target) {
            is MemoryTarget.Chat -> invalidateSources(
                chatIds = mDatabase.getChatMessageDao().memoryAllIds(target.id),
            )

            is MemoryTarget.Group -> invalidateSources(
                groupIds = mDatabase.getGroupChatMessageDao().getMessageIdsBySessionId(target.id),
                summaryIds = mDatabase.getGroupChatSummaryDao().memorySummaryIds(target.id, 0),
            )

            is MemoryTarget.Character -> error("Expected conversation memory target")
        }
    }

    /**
     * 在原分支事务内复制分支点有效修订与可重映射证据。
     *
     * @param source 原会话范围
     * @param target 已创建的新会话范围
     * @param messages 已复制的消息 ID 映射，包含单聊摘要映射
     * @param summaries 群聊有效摘要映射
     */
    suspend fun copyBranch(
        source: MemoryTarget,
        target: MemoryTarget,
        messages: Map<Long, Long>,
        summaries: Map<Long, Long> = emptyMap(),
    ) = mDatabase.withTransaction {
        // 校验源与目标属于同一类会话且为不同实例。
        require(source !is MemoryTarget.Character && table(source) == table(target) && source != target) {
            "Invalid memory branch"
        }
        val targetCharacters = targetInfo(target).characters.map { it.id }.toSet()
        val dao = store(source)
        // 生效边界必须位于实际复制的历史前缀，未来修订不因时间戳接近而进入分支。
        dao.list(source.id).forEach { entry ->
            val selected = dao.revisions(entry.ref.id)
                .firstOrNull { it.boundary == 0L || it.boundary in messages }
                ?: return@forEach
            // 历史修订的读者或人设可能已被删除，不能扩大可见范围或让分支事务失败。
            val privateId = selected.fields.snapshotPrivateCharacterId
            val personaId = selected.fields.snapshotPersonaId
            if (privateId != null && privateId !in targetCharacters) return@forEach
            if (personaId != null && mDatabase.getUserPersonaDao().find(personaId) == null) {
                return@forEach
            }
            val evidence = dao.sources(selected.id)
                .filter { it.valid }
                .mapNotNull { remapSource(it, source, messages, summaries) }
            if (evidence.isEmpty()) return@forEach
            // 按历史修订快照还原当时正文、人设和私有读者，并分配新 UUID。
            val now = System.currentTimeMillis()
            val fields = entry.fields.copy(
                uuid = UUID.randomUUID().toString(),
                content = selected.fields.content,
                personaId = personaId,
                slotKey = MemoryWriteValidator.slotKey(personaId, privateId),
                currentRevision = 1,
                createdAt = now,
                updatedAt = now,
            )
            val override = selected.fields.overrideCharacterMemoryUuid
                ?.let { mStores.getValue(MemoryTable.Character).byUuid(it) }
                ?.takeIf { it.fields.personaId == null || it.fields.personaId == fields.personaId }
            MemoryWriteValidator.validate(fields, privateId)
            val id = dao.insert(target.id, privateId, override?.ref?.id, fields)
            appendRevision(
                dao = dao,
                id = id,
                fields = fields,
                boundary = messages[selected.boundary] ?: 0,
                sources = evidence,
                origin = "Branch",
                overrideUuid = override?.fields?.uuid,
            )
        }
    }

    /** 仅复制新分支仍持有的证据，不能保留指向源会话未来消息的外键。 */
    private fun remapSource(
        source: MemorySourceFields,
        target: MemoryTarget,
        messages: Map<Long, Long>,
        summaries: Map<Long, Long>,
    ): MemorySourceFields? {
        // 单聊消息与单聊摘要来源仅在单聊分支中重映射至新消息 ID。
        if (source.chatMessageId != null) {
            if (target !is MemoryTarget.Chat) return null
            return source.copy(chatMessageId = messages[source.chatMessageId] ?: return null)
        }
        // 群聊消息来源仅在群聊分支中重映射至新消息 ID。
        if (source.groupMessageId != null) {
            if (target !is MemoryTarget.Group) return null
            return source.copy(groupMessageId = messages[source.groupMessageId] ?: return null)
        }
        // 群聊摘要来源重映射至新分支摘要 ID，未落入分支边界的丢弃。
        if (source.groupSummaryId != null) {
            return source.copy(groupSummaryId = summaries[source.groupSummaryId] ?: return null)
        }
        return source
    }

    /** 明确的用户证据无消息外键，删除原会话不会撤销独立手动确认。 */
    private fun userEvidence(now: Long) = MemorySourceFields(
        revisionId = 0,
        sourceKind = "UserEdit",
        confidence = "UserConfirmed",
        sourceKey = UUID.randomUUID().toString(),
        createdAt = now,
    )

    private suspend fun currentSources(entry: MemoryEntry): List<MemorySourceFields> {
        val dao = mStores.getValue(entry.ref.table)
        val revision = dao.revisions(entry.ref.id)
            .firstOrNull { it.fields.revision == entry.fields.currentRevision }
            ?: return emptyList()
        return dao.sources(revision.id)
    }

    /** 所有者和身份校验在外键之前执行，为编辑器提供可识别的失效目标。 */
    private suspend fun validateAudience(
        target: MemoryTarget,
        info: MemoryTargetInfo,
        draft: MemoryDraft,
    ) {
        // 校验私有受众必须属于会话真实成员且角色通用范围禁止指定私有受众。
        require(
            draft.privateCharacterId == null ||
                (target !is MemoryTarget.Character && info.characters.any { it.id == draft.privateCharacterId }),
        ) { "Invalid memory audience" }
        // 校验适用人设存在，且会话条目不能绑定到非当前会话人设。
        if (draft.personaId != null) {
            requireNotNull(mDatabase.getUserPersonaDao().find(draft.personaId)) {
                "Unknown memory persona"
            }
        }
        if (target !is MemoryTarget.Character) {
            require(draft.personaId == null || draft.personaId == info.personaId) {
                "Memory persona does not match conversation"
            }
        }
        // 存在局部覆盖引用时校验原通用记忆存在、角色归属与人设兼容。
        draft.overrideCharacterMemoryId?.let { id ->
            require(target !is MemoryTarget.Character) { "Character memory cannot override itself" }
            val original =
                mStores.getValue(MemoryTable.Character).get(id) ?: throw MemoryConflictException()
            require(
                info.characters.any { it.id == original.target.id } &&
                    (draft.privateCharacterId == original.target.id || target is MemoryTarget.Chat) &&
                    (original.fields.personaId == null || original.fields.personaId == draft.personaId),
            ) { "Invalid override audience" }
        }
    }

    private suspend fun owned(target: MemoryTarget, ref: MemoryRef): MemoryEntry {
        require(ref.table == table(target)) { "Memory table does not match target" }
        val entry = store(target).get(ref.id) ?: throw MemoryConflictException()
        require(entry.target == target) { "Memory owner does not match target" }
        if (target !is MemoryTarget.Character && entry.fields.personaId != null) {
            require(entry.fields.personaId == targetInfo(target).personaId) {
                "Memory persona does not match conversation"
            }
        }
        return entry
    }

    /** 原始消息 ID 作为生效边界，分支不以墙上时钟推断剧情进度。 */
    private suspend fun boundary(target: MemoryTarget): Long = when (target) {
        is MemoryTarget.Character -> 0
        is MemoryTarget.Chat -> mDatabase.getChatMessageDao()
            .getLatestMessageBySessionId(target.id)?.id ?: 0

        is MemoryTarget.Group -> mDatabase.getGroupChatMessageDao().getLatestMessage(target.id)?.id
            ?: 0
    }

    private suspend fun persona(id: Long): Long = if (id > 0) {
        mDatabase.getUserPersonaDao().get(id).id
    } else {
        mDatabase.getUserPersonaDao().active().id
    }

    private fun table(target: MemoryTarget) = when (target) {
        is MemoryTarget.Character -> MemoryTable.Character
        is MemoryTarget.Chat -> MemoryTable.Chat
        is MemoryTarget.Group -> MemoryTable.Group
    }

    private fun store(target: MemoryTarget) = mStores.getValue(table(target))

    /** 每版复制全部仍有效的证据，不只保存本次新增来源。 */
    private suspend fun appendRevision(
        dao: MemoryStore,
        id: Long,
        fields: MemoryFields,
        boundary: Long,
        sources: List<MemorySourceFields>,
        origin: String,
        overrideUuid: String? = null,
    ) {
        // 写入修订前先逐条校验来源外键与区间合法性。
        sources.forEach(MemoryWriteValidator::validateSource)
        // 写入不可变修订快照并回填关联的来源证据列表。
        val revisionId = dao.appendRevision(
            fields = MemoryRevisionFields(
                entryId = id,
                revision = fields.currentRevision,
                content = fields.content,
                overrideCharacterMemoryUuid = overrideUuid,
                snapshotPersonaId = fields.personaId,
                snapshotPrivateCharacterId = fields.slotKey.substringAfter('/').toLong()
                    .takeIf { it > 0 },
                origin = origin,
                reason = "",
                createdAt = fields.updatedAt,
            ),
            boundary = boundary,
        )
        dao.appendSources(sources.map { it.copy(revisionId = revisionId) })
    }

    private suspend fun invalidateUnsupported(
        dao: MemoryStore,
        entry: MemoryEntry,
        sources: List<MemorySourceFields>,
    ) {
        // 构造失效态快照并以乐观锁条件推进主表版本。
        val now = System.currentTimeMillis()
        val fields = entry.fields.copy(
            content = entry.fields.content.copy(status = MemoryStatus.Invalidated.name),
            currentRevision = entry.fields.currentRevision + 1,
            updatedAt = now,
        )
        if (dao.update(entry.ref.id, entry.fields.currentRevision, fields.content, now) != 1) {
            throw MemoryConflictException()
        }
        // 追加来源失效修订，保留脱敏后的外键关联以支持后续硬删除清理。
        appendRevision(
            dao = dao,
            id = entry.ref.id,
            fields = fields,
            boundary = boundary(entry.target),
            sources = sources,
            origin = "SourceInvalidated",
        )
    }
}
