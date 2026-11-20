package me.kafuuneko.rpclient.libs.memory

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.room.entity.MemoryContent
import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields
import java.security.MessageDigest

/**
 * 暂存恢复库的记忆业务校验器。
 * - 使用与手动编辑相同的正文和来源规则，外键不能替代跨字段校验。
 * - 任一权威记录不完整即拒绝整库，调用者尚未替换在线数据库。
 */
object MemoryRestoreValidator {
    /**
     * 依次检查三族所有者、当前快照、修订边界及来源，没有静默修剪。
     *
     * @param db 已完成结构迁移但尚未替换在线主库的恢复暂存库
     */
    fun validate(db: SupportSQLiteDatabase) {
        listOf("character", "chat", "group_chat").forEach { family ->
            db.query("SELECT * FROM ${family}_memories").use { entries ->
                while (entries.moveToNext()) {
                    validateEntry(db, family, entries)
                }
            }
        }
    }

    /** 主表身份与当前修订快照必须完全一致，允许因隐私清理而缺少早期修订。 */
    private fun validateEntry(db: SupportSQLiteDatabase, family: String, row: Cursor) {
        // 读取主表行并执行共享字段校验与所有者归属复核。
        val privateId = if (family == "character") null else row.optionalLong("privateCharacterId")
        val owner = row.number(if (family == "character") "characterId" else "sessionId")
        val fields = MemoryFields(
            uuid = row.text("uuid"),
            personaId = row.optionalLong("personaId"),
            subjectKey = row.text("subjectKey"),
            factKey = row.text("factKey"),
            slotKey = row.text("slotKey"),
            content = row.content(),
            currentRevision = row.number("currentRevision"),
            createdAt = row.number("createdAt"),
            updatedAt = row.number("updatedAt"),
        )
        MemoryWriteValidator.validate(fields, privateId)
        require(owner > 0 && row.number("id") > 0) { "Invalid memory owner" }
        if (family == "chat" && privateId != null) {
            require(
                scalar(
                    db,
                    "SELECT characterId FROM chat_sessions WHERE id = ?",
                    owner
                ) == privateId,
            ) { "Invalid private chat memory" }
        }
        // 校验局部覆盖关系指向当前会话可见角色的兼容事实。
        val override =
            if (family == "character") null else row.optionalLong("overrideCharacterMemoryId")
        validateOverride(db, family, owner, privateId, fields, override)
        var foundCurrent = false
        // 历史可以有清理造成的版本空隙，但不能有未来版本或无当前快照。
        db.query(
            "SELECT * FROM ${family}_memory_revisions WHERE entryId = ?",
            arrayOf(row.number("id")),
        ).use { revisions ->
            while (revisions.moveToNext()) {
                val revision = revisions.number("revision")
                require(revision in 1..fields.currentRevision) {
                    "Invalid memory revision sequence"
                }
                val content = revisions.content()
                MemoryWriteValidator.validate(
                    fields = fields.copy(content = content, currentRevision = revision),
                    privateCharacterId = privateId,
                )
                if (revision == fields.currentRevision) {
                    require(
                        content == fields.content &&
                            revisions.optionalLong("snapshotPersonaId") == fields.personaId &&
                            revisions.optionalLong("snapshotPrivateCharacterId") == privateId,
                    ) { "Memory revision snapshot mismatch" }
                    foundCurrent = true
                }
                if (family != "character") {
                    validateBoundary(db, family, owner, revisions.number("effectiveAfterMessageId"))
                }
                val supported = validateSources(db, family, owner, fields, revisions.number("id"))
                require(
                    revision != fields.currentRevision ||
                        content.status != MemoryStatus.Active.name ||
                        supported,
                ) { "Active memory has no valid evidence" }
            }
        }
        require(foundCurrent) { "Current memory revision is missing" }
    }

    /** 覆盖必须指向本会话可见角色的同一事实，不能借恢复绕过范围授权。 */
    private fun validateOverride(
        db: SupportSQLiteDatabase,
        family: String,
        owner: Long,
        privateId: Long?,
        fields: MemoryFields,
        override: Long?,
    ) {
        if (override == null) return
        // 查询被覆盖的角色通用记忆并核对事实键与人设兼容性。
        db.query("SELECT * FROM character_memories WHERE id = ?", arrayOf(override)).use { target ->
            require(target.moveToFirst()) { "Missing overridden memory" }
            val characterId = target.number("characterId")
            val targetPersonaId = target.optionalLong("personaId")
            require(
                target.text("subjectKey") == fields.subjectKey &&
                    target.text("factKey") == fields.factKey &&
                    (targetPersonaId == null || targetPersonaId == fields.personaId),
            ) { "Incompatible overridden fact" }
            // 按单聊或群聊分别复核角色归属与私有成员资格。
            if (family == "chat") {
                require(
                    scalar(
                        db,
                        "SELECT characterId FROM chat_sessions WHERE id = ?",
                        owner,
                    ) == characterId,
                ) { "Invalid override owner" }
            } else {
                require(
                    privateId == characterId && scalar(
                        db,
                        "SELECT COUNT(*) FROM group_chat_members WHERE sessionId = ? AND characterId = ?",
                        owner,
                        characterId,
                    ) == 1L,
                ) { "Invalid private override" }
            }
        }
    }

    /** 已硬删除的边界可以失去原文，但不能引用其他会话的同号消息。 */
    private fun validateBoundary(
        db: SupportSQLiteDatabase,
        family: String,
        owner: Long,
        boundary: Long,
    ) {
        require(boundary >= 0) { "Invalid memory boundary" }
        if (boundary == 0L) return
        val sourceOwner =
            scalar(db, "SELECT sessionId FROM ${family}_messages WHERE id = ?", boundary)
        require(sourceOwner == null || sourceOwner == owner) {
            "Memory boundary belongs to another conversation"
        }
    }

    /** 遍历所有历史证据，拒绝错误外键组合、越界摘录或被篡改的有效来源。 */
    private fun validateSources(
        db: SupportSQLiteDatabase,
        family: String,
        owner: Long,
        fields: MemoryFields,
        revisionId: Long,
    ): Boolean {
        var supported = false
        // 逐条查询该修订下的全部来源并校验结构合法性。
        db.query(
            "SELECT * FROM ${family}_memory_sources WHERE revisionId = ?",
            arrayOf(revisionId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val source = cursor.source()
                MemoryWriteValidator.validateSource(source)
                // 对仍标记有效的来源进一步复核本机消息正文指纹与归属。
                if (source.valid) {
                    validateLiveSource(db, family, owner, fields, source)
                    supported = true
                }
            }
        }
        return supported
    }

    /** 外部资料只用稳定快照，本机消息来源则复核所有权、来源类型和正文指纹。 */
    private fun validateLiveSource(
        db: SupportSQLiteDatabase,
        family: String,
        owner: Long,
        fields: MemoryFields,
        source: MemorySourceFields,
    ) {
        // 本机消息与摘要联表复核；离群前冻结的提升快照不依赖当前群成员资格。
        val table = when {
            source.chatMessageId != null -> "chat_messages"
            source.groupMessageId != null -> "group_chat_messages"
            source.groupSummaryId != null -> "group_chat_summaries"
            else -> return
        }
        val id = source.chatMessageId ?: source.groupMessageId ?: source.groupSummaryId
        db.query("SELECT * FROM $table WHERE id = ?", arrayOf(requireNotNull(id))).use { message ->
            require(message.moveToFirst()) { "Missing memory evidence message" }
            val sessionId = message.number("sessionId")
            val single = source.chatMessageId != null
            // 校验记忆所有者是否有权读取该证据所属会话。
            if (family == "character") {
                val authorized = if (single) {
                    scalar(
                        db,
                        "SELECT COUNT(*) FROM chat_sessions WHERE id = ? AND characterId = ?",
                        sessionId,
                        owner,
                    )
                } else {
                    scalar(
                        db,
                        "SELECT COUNT(*) FROM group_chat_members WHERE sessionId = ? AND characterId = ?",
                        sessionId,
                        owner,
                    )
                }
                require(authorized == 1L) { "Character cannot read evidence conversation" }
            } else {
                require((family == "chat") == single && owner == sessionId) {
                    "Evidence belongs to another conversation"
                }
            }
            // 复核单聊摘要标识、原文 SHA-256 指纹及摘录起止区间一致性。
            if (single) {
                require((message.text("source") == "Summary") == (source.sourceKind == "ChatSummary")) {
                    "Evidence type mismatch"
                }
            }
            val body = message.text("content")
            require(source.fingerprint == fingerprint(body)) {
                "Memory evidence fingerprint mismatch"
            }
            val start = source.rangeStart
            val end = source.rangeEnd
            if (start != null && end != null) {
                require(end <= body.length && body.substring(start, end) == source.quote) {
                    "Memory evidence quote mismatch"
                }
            } else {
                require(source.quote.isEmpty() || source.quote == body) {
                    "Memory evidence quote has no range"
                }
            }
            require(fields.personaId == null || fields.personaId > 0) { "Invalid evidence persona" }
        }
    }

    /**
     * 计算消息正文 SHA-256 指纹，只用于本地一致性检查，不把正文写入诊断日志。
     *
     * @param body 原始消息或摘要正文
     * @return 小写十六进制编码的 SHA-256 指纹串
     */
    fun fingerprint(body: String): String =
        MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun scalar(db: SupportSQLiteDatabase, sql: String, vararg args: Long): Long? =
        db.query(sql, args.toTypedArray()).use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun Cursor.text(name: String) = getString(getColumnIndexOrThrow(name))

    private fun Cursor.number(name: String) = getLong(getColumnIndexOrThrow(name))

    private fun Cursor.optionalLong(name: String): Long? =
        getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.flag(name: String): Boolean {
        val value = number(name)
        require(value == 0L || value == 1L) { "Invalid memory boolean" }
        return value == 1L
    }

    private fun Cursor.content() = MemoryContent(
        body = text("body"),
        kind = text("kind"),
        status = text("status"),
        importance = number("importance").toInt(),
        pinned = flag("pinned"),
    )

    private fun Cursor.source(): MemorySourceFields {
        // 映射来源表各列为领域来源结构，并对布尔标志与可空列执行严格读取。
        return MemorySourceFields(
            revisionId = number("revisionId"),
            sourceKind = text("sourceKind"),
            confidence = text("confidence"),
            valid = flag("valid"),
            sourceKey = text("sourceKey"),
            chatMessageId = optionalLong("chatMessageId"),
            groupMessageId = optionalLong("groupMessageId"),
            groupSummaryId = optionalLong("groupSummaryId"),
            authorKey = text("authorKey"),
            fingerprint = text("fingerprint"),
            quote = text("quote"),
            rangeStart = optionalLong("rangeStart")?.toInt(),
            rangeEnd = optionalLong("rangeEnd")?.toInt(),
            originRef = text("originRef"),
            createdAt = number("createdAt"),
        )
    }
}
