package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.PromptType
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.PromptPreset
import me.kafuuneko.rpclient.libs.room.entity.PromptPresetEntry

/**
 * 自定义提示词预设的业务入口。
 * - Room 仅保存自定义预设与相对默认值的覆盖项。
 * - 当前选择存入 AppModel，生成时解析为独立快照。
 */
class PromptPresetRepository(private val mDatabase: AppDatabase) {
    private val mDao = mDatabase.getPromptPresetDao()
    private val mActiveId = MutableStateFlow(AppModel.activePromptPresetId)

    /** 合并自定义列表与轻量选择状态，默认预设不需要数据库记录。 */
    fun observePresets() = combine(mDao.observePresets(), mActiveId) { presets, selected ->
        val active = selected.takeIf { id -> presets.any { it.id == id } }
            ?: PromptTemplateSnapshot.BUILTIN_ID
        (listOf(defaultPreset()) + presets).map { PromptPresetListing(it, it.id == active) }
    }

    /** 读取指定预设，-1 直接返回枚举默认值。 */
    suspend fun getDetail(id: Long): PromptPresetDetail = mDatabase.withTransaction {
        val preset = if (id == PromptTemplateSnapshot.BUILTIN_ID) defaultPreset() else
            requireNotNull(mDao.getPreset(id)) { "Prompt preset is missing" }
        PromptPresetDetail(preset, readSnapshot(preset))
    }

    /** 捕获当前整套配置；选择失效时回到默认值，不影响已有请求快照。 */
    suspend fun getActiveSnapshot(): PromptTemplateSnapshot = mDatabase.withTransaction {
        val selected = AppModel.activePromptPresetId
        val preset =
            if (selected == PromptTemplateSnapshot.BUILTIN_ID) null else mDao.getPreset(selected)
        if (preset == null && selected != PromptTemplateSnapshot.BUILTIN_ID) setActiveId(
            PromptTemplateSnapshot.BUILTIN_ID
        )
        // 先解析模板覆盖项，再捕获不属于模板的全局构建参数。
        readSnapshot(preset ?: defaultPreset()).copy(
            summaryInjectionPosition = AppModel.summaryInjectionPosition,
            summaryInjectionDepth = AppModel.summaryInjectionDepth,
            summaryInjectionRole = AppModel.summaryInjectionRole,
            worldInfoBudgetPercent = AppModel.worldInfoBudgetPercent,
            worldInfoBudgetCap = AppModel.worldInfoBudgetCap,
            includeThinkInContext = AppModel.includeThinkInContext,
        )
    }

    /** 新建时只插入元数据；复制时只复制来源的覆盖项，不自动启用。 */
    suspend fun create(
        sourceId: Long = PromptTemplateSnapshot.BUILTIN_ID,
        name: String,
        description: String
    ): Long = mDatabase.withTransaction {
        require(name.isNotBlank()) { "Prompt preset name is empty" }
        val entries = if (sourceId == PromptTemplateSnapshot.BUILTIN_ID) emptyList() else {
            requireNotNull(mDao.getPreset(sourceId)) { "Source prompt preset is missing" }
            mDao.getEntries(sourceId)
        }
        val now = System.currentTimeMillis()
        val id = mDao.insertPreset(
            PromptPreset(
                name = name.trim(),
                description = description,
                createdAt = now,
                updatedAt = now
            )
        )
        // 复制保留未知键，避免旧应用编辑时抹掉未来新增的内容。
        mDao.insertEntries(entries.map { it.copy(presetId = id) })
        id
    }

    /** 使用 revision 防止另一页面覆盖尚未看到的修改。 */
    suspend fun rename(id: Long, revision: Long, name: String, description: String) =
        mDatabase.withTransaction {
            require(id != PromptTemplateSnapshot.BUILTIN_ID && name.isNotBlank()) { "Invalid custom prompt preset" }
            checkRevision(
                mDao.updatePreset(
                    id = id,
                    revision = revision,
                    name = name.trim(),
                    description = description,
                    now = System.currentTimeMillis()
                )
            )
        }

    /** 按原文比较默认值；恢复默认删除条目，显式空串与空白保持各自语义。 */
    suspend fun savePrompt(id: Long, revision: Long, type: PromptType, content: String) =
        mDatabase.withTransaction {
            require(id != PromptTemplateSnapshot.BUILTIN_ID) { "Default prompt preset is read-only" }
            val preset = requireNotNull(mDao.getPreset(id)) { "Prompt preset is missing" }
            checkRevision(
                mDao.updatePreset(
                    id = id,
                    revision = revision,
                    name = preset.name,
                    description = preset.description,
                    now = System.currentTimeMillis()
                )
            )
            // 元数据版本与差异项共享事务，冲突不会产生部分保存。
            if (content == type.defaultValue) mDao.deleteEntry(id, type.key)
            else mDao.saveEntry(PromptPresetEntry(id, type.key, content))
        }

    /** 校验自定义预设存在后把当前 ID 保存到 AppModel。 */
    suspend fun activate(id: Long) = mDatabase.withTransaction {
        if (id != PromptTemplateSnapshot.BUILTIN_ID) checkNotNull(mDao.getPreset(id)) { "Prompt preset is missing" }
        setActiveId(id)
    }

    /** 先回到默认选择再删除当前预设，中断时最多保留尚未删除的自定义记录。 */
    suspend fun delete(id: Long) = mDatabase.withTransaction {
        require(id != PromptTemplateSnapshot.BUILTIN_ID) { "Default prompt preset is read-only" }
        if (AppModel.activePromptPresetId == id) setActiveId(PromptTemplateSnapshot.BUILTIN_ID)
        mDao.deletePreset(id)
    }

    /** 缺项一律来自枚举，不把默认正文物化到条目表。 */
    private suspend fun readSnapshot(preset: PromptPreset): PromptTemplateSnapshot {
        val overrides = if (preset.id == PromptTemplateSnapshot.BUILTIN_ID) emptyMap() else
            mDao.getEntries(preset.id).associate { it.promptKey to it.content }
        return PromptTemplateSnapshot.resolve(preset.id, preset.revision, overrides)
    }

    /** 只用于领域与列表展示的默认记录，从不插入数据库。 */
    private fun defaultPreset() = PromptPreset(
        id = PromptTemplateSnapshot.BUILTIN_ID,
        name = "",
        createdAt = 0,
        updatedAt = 0
    )

    /** 与其他全局偏好一致，在持久化后通知已打开的列表。 */
    private fun setActiveId(id: Long) {
        AppModel.activePromptPresetId = id
        mActiveId.value = id
    }

    /** 保存冲突时由页面保留草稿并提示重新加载。 */
    private fun checkRevision(updated: Int) {
        if (updated != 1) throw PromptPresetConflictException()
    }
}

/** 列表投影由 Repository 合并 SP 选择后生成。 */
data class PromptPresetListing(val preset: PromptPreset, val isActive: Boolean)

/** 已解析默认值的预设详情，页面不需要直接访问 DAO。 */
data class PromptPresetDetail(val preset: PromptPreset, val snapshot: PromptTemplateSnapshot)

/** 保存时发现编辑版本已变化。 */
class PromptPresetConflictException : IllegalStateException("Prompt preset changed during editing")
