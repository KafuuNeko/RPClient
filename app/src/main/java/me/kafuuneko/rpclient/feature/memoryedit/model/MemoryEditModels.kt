package me.kafuuneko.rpclient.feature.memoryedit.model

import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryRef
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTarget

/** 记忆编辑器中展示的角色或人设身份选项，名称仅作界面展示。 */
data class MemoryEditOwnerUiModel(
    val id: Long,
    val name: String,
)

/** 记忆编辑目标的标题、合法角色范围与会话级记忆开关展示模型。 */
data class MemoryEditSettingsUiModel(
    val title: String,
    val characters: List<MemoryEditOwnerUiModel>,
    val personaId: Long?,
    val inherit: Boolean = true,
    val recall: Boolean = false,
)

/** 记忆条目列表项展示模型，由 ViewModel 从持久化条目映射并隔离 Room 类型。 */
data class MemoryEditEntryItem(
    val ref: MemoryRef,
    val target: MemoryTarget,
    val uuid: String,
    val body: String,
    val kind: MemoryKind,
    val status: MemoryStatus,
    val importance: Int,
    val pinned: Boolean,
    val personaId: Long?,
    val privateCharacterId: Long?,
    val overrideCharacterMemoryId: Long?,
    val currentRevision: Long,
)

/** 只读继承记忆条目的展示模型，附带其所属角色展示信息。 */
data class MemoryEditInheritedItem(
    val owner: MemoryEditOwnerUiModel,
    val entry: MemoryEditEntryItem,
)

/** 修订历史中的单条来源证据展示模型。 */
data class MemoryEditSourceUiModel(
    val sourceKind: String,
    val quote: String,
)

/** 修订历史展示模型，保留版本正文、分类、状态与证据摘录。 */
data class MemoryEditRevisionUiModel(
    val revision: Long,
    val body: String,
    val kind: MemoryKind,
    val status: MemoryStatus,
    val sources: List<MemoryEditSourceUiModel>,
)

/** 便携包导入时需由用户显式映射的外部人设或成员项。 */
data class MemoryEditImportMappingItem(
    val sourceId: Long,
    val label: String,
)
