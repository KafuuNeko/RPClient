package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields
import java.util.UUID

/**
 * 记忆写入、便携包导入与备份恢复共用的领域字段校验器。
 * - 统一校验正文长度、分类、状态、重要度、修订号及规范身份槽位键。
 * - 校验来源证据类型与外键组合形状，避免跨表混用消息主键；真实所有者归属继续由数据库外键和仓库事务保障。
 */
object MemoryWriteValidator {
    /**
     * 验证来源类型与外键形状，实际消息归属在仓库事务内校验。
     *
     * @param source 待校验的单条修订来源证据
     */
    fun validateSource(source: MemorySourceFields) {
        // 检查来源标识键、时间戳及外键 ID 基本合法性。
        val links = listOfNotNull(
            source.chatMessageId,
            source.groupMessageId,
            source.groupSummaryId,
        )
        require(source.sourceKey.isNotBlank() && source.createdAt >= 0 && links.all { it > 0 }) {
            "Invalid memory source identity"
        }
        // 校验可信度枚举值处于允许集合内。
        require(
            source.confidence in setOf(
                "UserConfirmed",
                "Observed",
                "Inferred",
                "Unverified",
            ),
        ) { "Invalid memory source confidence" }
        // 按来源类型校验引用外键数量和目标列是否匹配。
        when (source.sourceKind) {
            "UserEdit", "External", "PromotedSnapshot" -> require(links.isEmpty()) {
                "Independent evidence cannot reference a message"
            }

            "ChatMessage", "ChatSummary" -> require(links.size == 1 && source.chatMessageId != null) {
                "Invalid chat evidence"
            }

            "GroupMessage" -> require(links.size == 1 && source.groupMessageId != null) {
                "Invalid group evidence"
            }

            "GroupSummary" -> require(links.size == 1 && source.groupSummaryId != null) {
                "Invalid summary evidence"
            }

            else -> error("Invalid memory source kind")
        }
        // 校验摘录起止区间成对出现且不倒挂。
        val hasEmptyRange = source.rangeStart == null && source.rangeEnd == null
        val hasValidRange = source.rangeStart != null &&
            source.rangeEnd != null &&
            source.rangeStart >= 0 &&
            source.rangeEnd >= source.rangeStart
        require(hasEmptyRange || hasValidRange) { "Invalid evidence range" }
    }

    /**
     * 校验同构快照及规范身份键。
     *
     * @param fields 已解析但尚未写入的字段
     * @param privateCharacterId 实际私有读者，通用范围为空
     */
    fun validate(fields: MemoryFields, privateCharacterId: Long?) {
        // 身份占位值只允许用于 slotKey，外键必须为真实正 ID 或空。
        require(fields.personaId == null || fields.personaId > 0) { "Invalid memory persona" }
        require(privateCharacterId == null || privateCharacterId > 0) { "Invalid memory reader" }
        require(fields.slotKey == slotKey(fields.personaId, privateCharacterId)) {
            "Invalid memory slot"
        }
        require(UUID.fromString(fields.uuid).toString() == fields.uuid) { "Invalid memory UUID" }
        require(fields.subjectKey.isNotBlank() && fields.factKey.isNotBlank()) {
            "Invalid memory identity"
        }
        // 正文上限限制本地索引、导入和提示构建的内存成本。
        require(fields.content.body.isNotBlank() && fields.content.body.length <= 32_768) {
            "Invalid memory body"
        }
        // 校验分类、状态、重要度及修订时间边界合法。
        require(fields.content.kind in MemoryKind.entries.map { it.name }) {
            "Invalid memory kind"
        }
        require(fields.content.status in MemoryStatus.entries.map { it.name }) {
            "Invalid memory status"
        }
        require(fields.content.importance in 0..100) { "Invalid memory importance" }
        require(
            fields.currentRevision > 0 &&
                fields.createdAt >= 0 &&
                fields.updatedAt >= fields.createdAt,
        ) { "Invalid memory revision" }
    }

    /**
     * 将可空身份规范为唯一索引中的非空十进制键。
     *
     * @param personaId 适用人设 ID，不限人设时为空
     * @param privateCharacterId 私有读者角色 ID，共享或角色通用范围时为空
     * @return 形如 `personaId/privateCharacterId` 的规范槽位键
     */
    fun slotKey(personaId: Long?, privateCharacterId: Long?): String =
        "${personaId ?: 0}/${privateCharacterId ?: 0}"
}
