package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.room.entity.MemoryContent
import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import me.kafuuneko.rpclient.libs.room.entity.MemoryRevisionFields
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields
import me.kafuuneko.rpclient.libs.room.repository.MemoryRevisionDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 便携协议拒绝歧义身份及不完整证据，不能靠 Gson 宽松解析静默修复。 */
class MemoryPackageCodecTest {
    /** 完整来源与修订保持字面文本，重复 JSON 键不能覆盖版本或身份。 */
    @Test
    fun roundTripPreservesEvidenceAndRejectsDuplicateKeys() {
        // 验证正常往返解码完全一致。
        val data = fixture()
        val json = MemoryPackageCodec.encode(data)
        assertEquals(data, MemoryPackageCodec.decode(json))
        // 验证重复键及不支持的版本号均被严格拒绝。
        assertTrue(
            runCatching {
                MemoryPackageCodec.decode(
                    json.replace("\"version\":1", "\"version\":99,\"version\":1"),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                MemoryPackageCodec.decode(json.replace("\"version\":1", "\"version\":2"))
            }.isFailure,
        )
    }

    /** 主表与当前修订不一致、占位人设和重复 UUID 均拒绝整包。 */
    @Test
    fun rejectsSnapshotMismatchInvalidIdentityAndDuplicateEntries() {
        // 构造快照失配、非法负人设、重复条目及空历史四种畸形样本。
        val data = fixture()
        val entry = data.entries.single()
        val malformed = listOf(
            data.copy(
                entries = listOf(
                    entry.copy(
                        fields = entry.fields.copy(
                            content = entry.fields.content.copy(body = "different"),
                        ),
                    ),
                ),
            ),
            data.copy(entries = listOf(entry.copy(fields = entry.fields.copy(personaId = -1)))),
            data.copy(entries = listOf(entry, entry)),
            data.copy(entries = listOf(entry.copy(history = emptyList()))),
        )
        // 逐个校验编码自检均抛出异常。
        malformed.forEach { assertTrue(runCatching { MemoryPackageCodec.encode(it) }.isFailure) }
    }

    /** 消息证据不能跨表混用外键，独立手动来源也不能伪装成消息引用。 */
    @Test
    fun rejectsAmbiguousEvidenceAndUnsupportedActiveRevision() {
        // 构造无证据、手动来源伪造消息 ID、跨表混填外键三种非法来源。
        val data = fixture()
        val entry = data.entries.single()
        val revision = entry.history.single()
        val invalidSources = listOf(
            emptyList(),
            listOf(revision.sources.single().copy(chatMessageId = 1)),
            listOf(
                revision.sources.single().copy(sourceKind = "PromotedSnapshot", groupMessageId = 1)
            ),
            listOf(
                revision.sources.single()
                    .copy(sourceKind = "ChatMessage", chatMessageId = 1, groupMessageId = 1),
            ),
        )
        // 验证全部非法来源配置都被拒绝。
        invalidSources.forEach { sources ->
            val invalid = data.copy(
                entries = listOf(entry.copy(history = listOf(revision.copy(sources = sources)))),
            )
            assertTrue(runCatching { MemoryPackageCodec.encode(invalid) }.isFailure)
        }
    }

    private fun fixture(): MemoryPackage {
        // 组装带字面宏及尖括号的记忆条目与独立用户确认修订。
        val content = MemoryContent("tea {{literal}} <tag>", "preference", "Active")
        val fields = MemoryFields(
            uuid = "00000000-0000-0000-0000-000000000001",
            subjectKey = "self",
            factKey = "tea",
            slotKey = "0/0",
            content = content,
            createdAt = 1,
            updatedAt = 1,
        )
        val evidence = MemorySourceFields(
            revisionId = 1,
            sourceKind = "UserEdit",
            confidence = "UserConfirmed",
            sourceKey = "user/1",
            createdAt = 1,
        )
        val revision = MemoryRevisionDetail(
            fields = MemoryRevisionFields(
                entryId = 1,
                revision = 1,
                content = content,
                origin = "UserEdit",
                reason = "",
                createdAt = 1,
            ),
            effectiveAfterMessageId = 0,
            sources = listOf(evidence),
        )
        // 返回角色范围的单条目标准测试便携包。
        return MemoryPackage(
            table = MemoryTable.Character,
            ownerId = 1,
            owners = mapOf(1L to "Same name"),
            personas = emptyMap(),
            entries = listOf(PortableMemory(fields, null, null, listOf(revision))),
        )
    }
}
