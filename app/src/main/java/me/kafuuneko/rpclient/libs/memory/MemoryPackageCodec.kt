package me.kafuuneko.rpclient.libs.memory

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import me.kafuuneko.rpclient.libs.room.repository.MemoryRevisionDetail
import java.io.StringReader

/** 便携包只记录外部身份，导入必须显式映射到当前编辑器目标。 */
data class MemoryPackage(
    val format: String = "rpclient.memory",
    val version: Int = 1,
    val table: MemoryTable,
    val ownerId: Long,
    val owners: Map<Long, String>,
    val personas: Map<Long, String>,
    val entries: List<PortableMemory>,
)

/** 保存修订与来源快照，不将设备上的消息主键作为另一设备的授权。 */
data class PortableMemory(
    val fields: MemoryFields,
    val privateId: Long?,
    val overrideUuid: String?,
    val history: List<MemoryRevisionDetail>,
)

/**
 * 独立记忆格式编解码，不扩展标准角色卡。
 * - 严格限制大小、嵌套和重复字段。
 * - 所有条目通过共享正文校验器，缺失当前修订或相互矛盾的快照拒绝整包导入。
 */
object MemoryPackageCodec {
    const val MAX_BYTES = 4 * 1024 * 1024

    /**
     * 编码完整快照并在返回前自检往返一致性，超过上限时要求用户缩小导出范围。
     *
     * @param value 待编码的独立记忆便携包
     * @return 校验通过的 JSON 字符串
     */
    fun encode(value: MemoryPackage): String = Gson().toJson(value).also { decode(it) }

    /**
     * 解析和校验外部数据，名称仅用于映射界面展示。
     *
     * @param json UTF-8 格式的记忆包 JSON 原文
     * @return 通过格式、修订与证据完整性校验的记忆包对象
     */
    fun decode(json: String): MemoryPackage {
        // 先校验总字节上限，再在严格流式模式下构建无重复键的 JSON 树。
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Memory package exceeds size limit"
        }
        val tree = JsonReader(StringReader(json)).use { reader ->
            reader.strictness = Strictness.STRICT
            readValue(reader, 0).also { require(reader.peek() == JsonToken.END_DOCUMENT) }
        }
        // 反序列化并校验包头格式、版本、所有者与条目去重上限。
        val value = requireNotNull(Gson().fromJson(tree, MemoryPackage::class.java))
        require(value.format == "rpclient.memory" && value.version == 1 && value.ownerId > 0) {
            "Unsupported memory package"
        }
        require(
            value.entries.size <= 10000 &&
                value.entries.map { it.fields.uuid }.distinct().size == value.entries.size,
        ) { "Duplicate or excessive memories" }
        // 在任何数据库写入之前验证全部修订，不能靠部分导入掩盖坏记录。
        value.entries.forEach { entry ->
            MemoryWriteValidator.validate(entry.fields, entry.privateId)
            require(entry.fields.personaId == null || entry.fields.personaId in value.personas) {
                "Missing persona mapping"
            }
            require(entry.privateId == null || entry.privateId in value.owners) {
                "Missing member mapping"
            }
            require(entry.history.isNotEmpty() && entry.history.size <= 10000) {
                "Missing memory history"
            }
            require(entry.history.map { it.fields.revision }
                .distinct().size == entry.history.size) {
                "Duplicate memory revision"
            }
            // 核对当前修订快照与主表正文、人设和私有受众完全一致。
            val current =
                entry.history.single { it.fields.revision == entry.fields.currentRevision }
            require(
                current.fields.content == entry.fields.content &&
                    current.fields.snapshotPersonaId == entry.fields.personaId &&
                    current.fields.snapshotPrivateCharacterId == entry.privateId,
            ) { "Memory snapshot does not match revision" }
            // 逐版校验修订边界、来源去重以及生效状态必需的有效证据。
            entry.history.forEach { revision ->
                MemoryWriteValidator.validate(
                    fields = entry.fields.copy(
                        content = revision.fields.content,
                        currentRevision = revision.fields.revision,
                    ),
                    privateCharacterId = entry.privateId,
                )
                require(
                    revision.fields.revision <= entry.fields.currentRevision &&
                        revision.effectiveAfterMessageId >= 0,
                ) { "Invalid revision boundary" }
                require(revision.sources.map { it.sourceKey }
                    .distinct().size == revision.sources.size) {
                    "Duplicate memory evidence"
                }
                revision.sources.forEach { source -> MemoryWriteValidator.validateSource(source) }
                require(
                    revision.fields.revision != entry.fields.currentRevision ||
                        revision.fields.content.status != MemoryStatus.Active.name ||
                        revision.sources.any { it.valid },
                ) { "Active memory has no evidence" }
            }
        }
        return value
    }

    /** 显式遍历对象以拒绝重复键，避免后出现的权限字段静默覆盖前者。 */
    private fun readValue(reader: JsonReader, depth: Int): JsonElement {
        // 限制嵌套深度防止恶意深层 JSON 耗尽调用栈。
        require(depth < 32) { "Memory package nesting limit exceeded" }
        // 按流式 token 分发解析，对对象显式检测重复键名。
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val value = JsonObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!value.has(key)) { "Duplicate memory package field" }
                    value.add(key, readValue(reader, depth + 1))
                }
                reader.endObject()
                value
            }

            JsonToken.BEGIN_ARRAY -> {
                val value = JsonArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    value.add(readValue(reader, depth + 1))
                }
                reader.endArray()
                value
            }

            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                JsonNull.INSTANCE
            }

            else -> error("Invalid memory package JSON")
        }
    }
}
