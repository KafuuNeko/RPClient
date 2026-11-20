package me.kafuuneko.rpclient.feature.memoryedit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditEntryItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditImportMappingItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditInheritedItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditOwnerUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditRevisionUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditSettingsUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditSourceUiModel
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditContentState
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditDialogState
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditTab
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiIntent
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiState
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.memory.MemoryConflictException
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryEntry
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryPackage
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.libs.memory.MemoryTargetMissingException
import me.kafuuneko.rpclient.libs.memory.MemoryTransferRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryOwner
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryRevisionDetail
import me.kafuuneko.rpclient.libs.room.repository.MemoryTargetInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 独立范围编辑器的 MVI 状态持有者。
 * - 列表订阅与编辑草稿分离，后台更新不覆盖用户正在输入的内容。
 * - 原始持久化模型与导入便携包仅留在私有快照中，映射为页面 UI 模型后提供给 Compose。
 * - 写入、提升及导入均通过同一仓库事务，冲突保留草稿并提示重新读取。
 */
class MemoryEditViewModel :
    CoreViewModelWithEvent<MemoryEditUiIntent, MemoryEditUiState>(MemoryEditUiState.None),
    KoinComponent {
    private val mRepository by inject<MemoryRepository>()
    private val mTransfer by inject<MemoryTransferRepository>()
    private var mSubscription: Job? = null
    private var mEntries: List<MemoryEditEntryItem> = emptyList()
    private var mPendingExport: String? = null
    private var mPendingImport: MemoryPackage? = null

    /**
     * 首次解析导航身份，非法或已删除目标不会回退到其他角色。
     *
     * @param intent 包含目标类型与主键 ID 的初始化意图
     */
    @UiIntentObserver(MemoryEditUiIntent.Init::class)
    private fun onInit(intent: MemoryEditUiIntent.Init) {
        if (!isStateOf<MemoryEditUiState.None>()) return
        // 按类型编码解析目标范围，仅允许已定义的三种范围。
        val target = when (intent.type) {
            "Character" -> MemoryTarget.Character(intent.id)
            "Chat" -> MemoryTarget.Chat(intent.id)
            "Group" -> MemoryTarget.Group(intent.id)
            else -> null
        }
        // 非法或缺失目标立即报错并退出页面。
        if (target == null || target.id <= 0) {
            fail(MemoryTargetMissingException())
            return
        }
        // 建立正常态并启动仓库订阅。
        MemoryEditUiState.Normal(target).setup()
        subscribe(target)
    }

    /** 只重建列表订阅，不抹去未保存的弹窗输入。 */
    @UiIntentObserver(MemoryEditUiIntent.Refresh::class)
    private fun onRefresh() {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        state.copy(content = state.content.copy(loading = true, failed = false)).setup()
        subscribe(state.target)
    }

    /**
     * 切换本会话与只读继承视图，保留当前筛选条件。
     *
     * @param intent 包含目标分段标签的切换意图
     */
    @UiIntentObserver(MemoryEditUiIntent.SelectTab::class)
    private fun onSelectTab(intent: MemoryEditUiIntent.SelectTab) {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        if (state.target is MemoryTarget.Character && intent.tab == MemoryEditTab.Inherited) return
        state.copy(content = state.content.copy(tab = intent.tab)).setup()
    }

    /** 仓库每次发射时重新解析实际人设和成员，防止页面使用过时授权。 */
    private fun subscribe(target: MemoryTarget) {
        mSubscription?.cancel()
        mSubscription = viewModelScope.launch {
            // 订阅当前目标的持久化条目流，并在异常时交给统一错误处理。
            mRepository.observeEntries(target).catch { fail(it) }.collect { entries ->
                try {
                    // 复核目标标题、成员列表、当前人设及只读继承条目。
                    val info = mRepository.targetInfo(target)
                    val personas = mRepository.personas()
                        .filter { target is MemoryTarget.Character || it.id == info.personaId }
                        .map { option ->
                            MemoryEditOwnerUiModel(
                                id = option.id,
                                name = option.name
                            )
                        }
                    val inherited = if (target is MemoryTarget.Character) {
                        emptyList()
                    } else {
                        info.characters.flatMap { owner ->
                            mRepository.inherited(target, owner.id, requireNotNull(info.personaId))
                                .map { entry ->
                                    MemoryEditInheritedItem(
                                        owner = owner.toUiModel(),
                                        entry = entry.toUiModel(),
                                    )
                                }
                        }
                    }
                    // 缓存映射后的条目快照并同步发布最新页面状态。
                    mEntries = entries.map { entry -> entry.toUiModel() }
                    val state = getOrNull<MemoryEditUiState.Normal>() ?: return@collect
                    state.copy(
                        title = info.title,
                        settings = info.toUiModel(),
                        personas = personas,
                        content = state.content.copy(
                            loading = false,
                            failed = false,
                            inherited = inherited,
                        ),
                    ).setup()
                    publish()
                } catch (error: Exception) {
                    fail(error)
                }
            }
        }
    }

    /**
     * 更新状态、人设与受众筛选条件，筛选只作用于已授权的管理列表。
     *
     * @param intent 包含筛选条件的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.Filter::class)
    private fun onFilter(intent: MemoryEditUiIntent.Filter) {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        state.copy(
            content = state.content.copy(
                status = intent.status,
                persona = intent.persona,
                audience = intent.audience,
            ),
        ).setup()
        publish()
    }

    /** 按状态、人设和共享／成员视图筛选，默认全部与目录总数一致。 */
    private fun publish() {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        val filter = state.content
        val entries = mEntries.filter { entry ->
            (filter.status == null || entry.status == filter.status) &&
                (filter.persona == -1L || (entry.personaId ?: 0) == filter.persona) &&
                (filter.audience == -1L || (entry.privateCharacterId ?: 0) == filter.audience)
        }
        state.copy(
            content = filter.copy(
                totalCount = mEntries.size,
                entries = entries,
            ),
        ).setup()
    }

    /** 新建时人设默认不限，群内共享与成员私有由用户明确选择。 */
    @UiIntentObserver(MemoryEditUiIntent.Create::class)
    private fun onCreate() = dialog(
        MemoryEditDialogState.Edit(
            entry = null,
            draft = MemoryDraft(body = ""),
        ),
    )

    /**
     * 打开条目编辑器并保存读取时的版本，不能自动替换为后台已更新版本。
     *
     * @param intent 包含待编辑条目模型的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.Edit::class)
    private fun onEdit(intent: MemoryEditUiIntent.Edit) = dialog(
        MemoryEditDialogState.Edit(
            entry = intent.entry,
            draft = draft(intent.entry),
        ),
    )

    /**
     * 更新内存中的编辑草稿，取消时不向数据库写入半成品。
     *
     * @param intent 包含最新表单草稿的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ChangeDraft::class)
    private fun onChangeDraft(intent: MemoryEditUiIntent.ChangeDraft) {
        val current =
            getOrNull<MemoryEditUiState.Normal>()?.dialog as? MemoryEditDialogState.Edit ?: return
        dialog(current.copy(draft = intent.draft))
    }

    /** 保存成功后关闭编辑器；版本冲突时保留原文供用户选择重新读取。 */
    @UiIntentObserver(MemoryEditUiIntent.Save::class)
    private fun onSave() = perform { state ->
        val edit = state.dialog as? MemoryEditDialogState.Edit ?: return@perform
        mRepository.save(
            target = state.target,
            draft = edit.draft,
            ref = edit.entry?.ref,
            expectedRevision = edit.entry?.currentRevision ?: 0,
        )
        closeDialog()
    }

    /** 明确重新读取时才替换用户草稿，不自动重试覆盖并发版本。 */
    @UiIntentObserver(MemoryEditUiIntent.Reload::class)
    private fun onReload() {
        val edit =
            getOrNull<MemoryEditUiState.Normal>()?.dialog as? MemoryEditDialogState.Edit ?: return
        val entry = mEntries.find { it.ref == edit.entry?.ref } ?: return
        dialog(
            MemoryEditDialogState.Edit(
                entry = entry,
                draft = draft(entry),
            ),
        )
    }

    /**
     * 确认当前正文时追加独立用户证据，未来清理来源仍保留这次确认。
     *
     * @param intent 包含待确认条目的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.Confirm::class)
    private fun onConfirm(intent: MemoryEditUiIntent.Confirm) = perform { state ->
        mRepository.confirm(
            target = state.target,
            ref = intent.entry.ref,
            expectedRevision = intent.entry.currentRevision,
        )
    }

    /**
     * 打开删除确认弹窗，展示正文及不可恢复提示。
     *
     * @param intent 包含待删除条目的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ShowDeleteDialog::class)
    private fun onShowDeleteDialog(intent: MemoryEditUiIntent.ShowDeleteDialog) =
        dialog(MemoryEditDialogState.Delete(intent.entry))

    /** 删除使用原版本比较，确认期间的并发写入仍受到保护。 */
    @UiIntentObserver(MemoryEditUiIntent.ConfirmDelete::class)
    private fun onConfirmDelete() = perform { state ->
        val entry = (state.dialog as? MemoryEditDialogState.Delete)?.entry ?: return@perform
        mRepository.delete(
            target = state.target,
            ref = entry.ref,
            expectedRevision = entry.currentRevision,
        )
        closeDialog()
    }

    /**
     * 只有显式打开详情时才读取来源摘录和完整修订，并映射为展示模型。
     *
     * @param intent 包含待查看历史条目的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ShowHistoryDialog::class)
    private fun onShowHistoryDialog(intent: MemoryEditUiIntent.ShowHistoryDialog) =
        perform { state ->
            val revisions = mRepository.history(state.target, intent.entry.ref)
                .map { detail -> detail.toUiModel() }
            getOrNull<MemoryEditUiState.Normal>()?.copy(
                dialog = MemoryEditDialogState.History(
                    entry = intent.entry,
                    revisions = revisions,
                ),
            )?.setup()
        }

    /**
     * 继承原件只读；创建带显式来源关系的局部覆盖草稿。
     *
     * @param intent 包含只读继承条目的覆盖意图
     */
    @UiIntentObserver(MemoryEditUiIntent.Override::class)
    private fun onOverride(intent: MemoryEditUiIntent.Override) {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        if (state.target is MemoryTarget.Character) return
        val value = draft(intent.entry).copy(
            privateCharacterId = intent.entry.target.id,
            overrideCharacterMemoryId = intent.entry.ref.id,
        )
        dialog(MemoryEditDialogState.Edit(entry = null, draft = value))
    }

    /**
     * 打开独立范围调整弹窗，正文保存不隐式更换人设或受众。
     *
     * @param intent 包含待调整范围条目的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ShowScopeDialog::class)
    private fun onShowScopeDialog(intent: MemoryEditUiIntent.ShowScopeDialog) = dialog(
        MemoryEditDialogState.Scope(
            entry = intent.entry,
            personaId = intent.entry.personaId,
            privateId = intent.entry.privateCharacterId,
        ),
    )

    /**
     * 仅暂存待确认的范围选择。
     *
     * @param intent 包含新选人设和私有受众 ID 的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ChangeScope::class)
    private fun onChangeScope(intent: MemoryEditUiIntent.ChangeScope) {
        val current =
            getOrNull<MemoryEditUiState.Normal>()?.dialog as? MemoryEditDialogState.Scope ?: return
        dialog(
            current.copy(
                personaId = intent.personaId,
                privateId = intent.privateId,
            ),
        )
    }

    /** 用户确认受众变化后再次校验数据库实际归属。 */
    @UiIntentObserver(MemoryEditUiIntent.ConfirmScope::class)
    private fun onConfirmScope() = perform { state ->
        val scope = state.dialog as? MemoryEditDialogState.Scope ?: return@perform
        mRepository.changeScope(
            target = state.target,
            ref = scope.entry.ref,
            expectedRevision = scope.entry.currentRevision,
            personaId = scope.personaId,
            privateId = scope.privateId,
        )
        closeDialog()
    }

    /**
     * 打开提升确认弹窗；按受众预筛合法目标角色，唯一可选角色自动预填但仍须用户确认。
     *
     * @param intent 包含待提升条目的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ShowPromoteDialog::class)
    private fun onShowPromoteDialog(intent: MemoryEditUiIntent.ShowPromoteDialog) {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        val eligibleCharacters = state.settings?.characters.orEmpty().filter { owner ->
            intent.entry.privateCharacterId == null || owner.id == intent.entry.privateCharacterId
        }
        val initialCharacterId = intent.entry.privateCharacterId
            ?: eligibleCharacters.singleOrNull()?.id
        dialog(
            MemoryEditDialogState.Promote(
                entry = intent.entry,
                eligibleCharacters = eligibleCharacters,
                characterId = initialCharacterId,
            ),
        )
    }

    /**
     * 目标角色选择只保存在提升弹窗中。
     *
     * @param intent 包含用户所选目标角色 ID 的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ChangePromotion::class)
    private fun onChangePromotion(intent: MemoryEditUiIntent.ChangePromotion) {
        val current =
            getOrNull<MemoryEditUiState.Normal>()?.dialog as? MemoryEditDialogState.Promote
                ?: return
        dialog(current.copy(characterId = intent.characterId))
    }

    /** 提升仍保留原会话条目与证据，跨会话条目有独立 UUID。 */
    @UiIntentObserver(MemoryEditUiIntent.ConfirmPromotion::class)
    private fun onConfirmPromotion() = perform { state ->
        val current = state.dialog as? MemoryEditDialogState.Promote ?: return@perform
        mRepository.promote(
            target = state.target,
            ref = current.entry.ref,
            expectedRevision = current.entry.currentRevision,
            characterId = requireNotNull(current.characterId),
        )
        closeDialog()
    }

    /**
     * 立即保存会话级记忆继承与普通召回开关，与父会话设置中的未保存草稿独立。
     *
     * @param intent 包含最新开关状态的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ChangeSettings::class)
    private fun onChangeSettings(intent: MemoryEditUiIntent.ChangeSettings) = perform { state ->
        mRepository.updateSettings(
            target = state.target,
            inherit = intent.inherit,
            recall = intent.recall,
        )
        subscribe(state.target)
    }

    /** 选择器期间锁定导入导出，避免两次结果交叉。 */
    @UiIntentObserver(MemoryEditUiIntent.ImportClick::class)
    private fun onImportClick() {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        if (state.busy || state.choosingDocument) return
        state.copy(choosingDocument = true).setup()
        MemoryEditViewEvent.OpenImporter.tryEmit()
    }

    /** 点击导出时冻结当前目标快照，选择文件期间不会混入后续编辑。 */
    @UiIntentObserver(MemoryEditUiIntent.ExportClick::class)
    private fun onExportClick() = perform { state ->
        mPendingExport = mRepository.exportPackage(state.target)
        getOrNull<MemoryEditUiState.Normal>()?.copy(choosingDocument = true)?.setup()
        MemoryEditViewEvent.OpenExporter.tryEmit()
    }

    /**
     * 文件读取后解析待映射人设与成员并打开确认弹窗，不按外部名称自动匹配本地身份。
     *
     * @param intent 包含系统选择器返回文档 URI 的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ImportResult::class)
    private fun onImportResult(intent: MemoryEditUiIntent.ImportResult) {
        clearPicker()
        val uri = intent.uri ?: return
        perform {
            // 在 IO 层解析完整便携包，并缓存在 ViewModel 私有快照中。
            val data = mTransfer.readPackage(uri)
            mPendingImport = data
            // 提取导入包及其历史修订涉及的全部待映射外部人设与成员标识。
            val requiredPersonas = data.entries
                .flatMap { entry ->
                    listOfNotNull(entry.fields.personaId) +
                        entry.history.mapNotNull { revision -> revision.fields.snapshotPersonaId }
                }
                .distinct()
                .map { sourceId ->
                    MemoryEditImportMappingItem(
                        sourceId = sourceId,
                        label = data.personas[sourceId].orEmpty(),
                    )
                }
            val requiredMembers = data.entries
                .flatMap { entry ->
                    listOfNotNull(entry.privateId) +
                        entry.history.mapNotNull { revision ->
                            revision.fields.snapshotPrivateCharacterId
                        }
                }
                .distinct()
                .map { sourceId ->
                    MemoryEditImportMappingItem(
                        sourceId = sourceId,
                        label = data.owners[sourceId].orEmpty(),
                    )
                }
            // 发布映射确认弹窗状态。
            getOrNull<MemoryEditUiState.Normal>()?.copy(
                dialog = MemoryEditDialogState.Import(
                    entryCount = data.entries.size,
                    requiredPersonas = requiredPersonas,
                    requiredMembers = requiredMembers,
                    isComplete = requiredPersonas.isEmpty() && requiredMembers.isEmpty(),
                ),
            )?.setup()
        }
    }

    /**
     * 导出取消时也销毁快照；进程重启丢失快照则报告失败。
     *
     * @param intent 包含系统保存选择器返回文档 URI 的意图
     */
    @UiIntentObserver(MemoryEditUiIntent.ExportResult::class)
    private fun onExportResult(intent: MemoryEditUiIntent.ExportResult) {
        clearPicker()
        val snapshot = mPendingExport
        mPendingExport = null
        val uri = intent.uri ?: return
        perform {
            mTransfer.writePackage(uri, requireNotNull(snapshot))
            AppViewEvent.PopupToastMessageByResId(R.string.memory_saved).tryEmit()
        }
    }

    /**
     * 记录用户显式选择的人设映射并重算映射完成态，不接受名称推断。
     *
     * @param intent 包含外部人设 ID 与本机目标人设 ID 的映射意图
     */
    @UiIntentObserver(MemoryEditUiIntent.MapPersona::class)
    private fun onMapPersona(intent: MemoryEditUiIntent.MapPersona) {
        val current =
            getOrNull<MemoryEditUiState.Normal>()?.dialog as? MemoryEditDialogState.Import ?: return
        val updatedPersonas = current.personas + (intent.source to intent.target)
        val isComplete = current.requiredPersonas.all { it.sourceId in updatedPersonas } &&
            current.requiredMembers.all { it.sourceId in current.members }
        dialog(current.copy(personas = updatedPersonas, isComplete = isComplete))
    }

    /**
     * 记录用户显式选择的成员映射并重算映射完成态，限定当前会话真实成员。
     *
     * @param intent 包含外部成员 ID 与本机目标成员 ID 的映射意图
     */
    @UiIntentObserver(MemoryEditUiIntent.MapMember::class)
    private fun onMapMember(intent: MemoryEditUiIntent.MapMember) {
        val current =
            getOrNull<MemoryEditUiState.Normal>()?.dialog as? MemoryEditDialogState.Import ?: return
        val updatedMembers = current.members + (intent.source to intent.target)
        val isComplete = current.requiredPersonas.all { it.sourceId in current.personas } &&
            current.requiredMembers.all { it.sourceId in updatedMembers }
        dialog(current.copy(members = updatedMembers, isComplete = isComplete))
    }

    /** 映射确认后整包原子提交，重复 UUID 不增加条目。 */
    @UiIntentObserver(MemoryEditUiIntent.ConfirmImport::class)
    private fun onConfirmImport() = perform { state ->
        val current = state.dialog as? MemoryEditDialogState.Import ?: return@perform
        val data = mPendingImport ?: return@perform
        mRepository.importPackage(
            target = state.target,
            data = data,
            personaMap = current.personas,
            memberMap = current.members,
        )
        closeDialog()
        AppViewEvent.PopupToastMessageByResId(R.string.memory_saved).tryEmit()
    }

    /** 返回优先关闭弹窗；有正文草稿时先确认丢弃。 */
    @UiIntentObserver(MemoryEditUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        if (state.busy) return
        if (state.dialog != MemoryEditDialogState.None) {
            onDismissDialog()
        } else {
            MemoryEditUiState.Finished(state).setup()
        }
    }

    /** 取消不会修改仓库；正文已经改动时保留恢复入口。 */
    @UiIntentObserver(MemoryEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        if (state.busy) return
        val current = state.dialog
        when {
            current is MemoryEditDialogState.Discard -> dialog(current.editor)
            current is MemoryEditDialogState.Edit && current.draft != current.initial -> dialog(
                MemoryEditDialogState.Discard(current),
            )

            else -> closeDialog()
        }
    }

    /** 用户确认后才清除正文草稿。 */
    @UiIntentObserver(MemoryEditUiIntent.ConfirmDiscard::class)
    private fun onConfirmDiscard() = closeDialog()

    private fun clearPicker() {
        getOrNull<MemoryEditUiState.Normal>()?.copy(choosingDocument = false)?.setup()
    }

    private fun closeDialog() {
        mPendingImport = null
        getOrNull<MemoryEditUiState.Normal>()?.copy(dialog = MemoryEditDialogState.None)?.setup()
    }

    private fun dialog(value: MemoryEditDialogState) {
        getOrNull<MemoryEditUiState.Normal>()?.takeUnless { it.busy || it.choosingDocument }
            ?.copy(dialog = value)?.setup()
    }

    private fun draft(entry: MemoryEditEntryItem) = MemoryDraft(
        body = entry.body,
        kind = entry.kind,
        status = entry.status,
        importance = entry.importance,
        pinned = entry.pinned,
        personaId = entry.personaId,
        privateCharacterId = entry.privateCharacterId,
        overrideCharacterMemoryId = entry.overrideCharacterMemoryId,
    )

    private fun MemoryOwner.toUiModel() = MemoryEditOwnerUiModel(id = id, name = name)

    private fun MemoryTargetInfo.toUiModel() = MemoryEditSettingsUiModel(
        title = title,
        characters = characters.map { owner -> owner.toUiModel() },
        personaId = personaId,
        inherit = inherit,
        recall = recall,
    )

    private fun MemoryEntry.toUiModel() = MemoryEditEntryItem(
        ref = ref,
        target = target,
        uuid = fields.uuid,
        body = fields.content.body,
        kind = MemoryKind.valueOf(fields.content.kind),
        status = MemoryStatus.valueOf(fields.content.status),
        importance = fields.content.importance,
        pinned = fields.content.pinned,
        personaId = fields.personaId,
        privateCharacterId = privateCharacterId,
        overrideCharacterMemoryId = overrideCharacterMemoryId,
        currentRevision = fields.currentRevision,
    )

    private fun MemoryRevisionDetail.toUiModel() = MemoryEditRevisionUiModel(
        revision = fields.revision,
        body = fields.content.body,
        kind = MemoryKind.valueOf(fields.content.kind),
        status = MemoryStatus.valueOf(fields.content.status),
        sources = sources.map { source ->
            MemoryEditSourceUiModel(
                sourceKind = source.sourceKind,
                quote = source.quote,
            )
        },
    )

    /** 所有写入互斥执行，异常文本不直接展示给用户。 */
    private fun perform(action: suspend (MemoryEditUiState.Normal) -> Unit) {
        val state = getOrNull<MemoryEditUiState.Normal>() ?: return
        if (state.busy || state.choosingDocument) return
        state.copy(busy = true).setup()
        viewModelScope.launch {
            try {
                action(state)
            } catch (error: Exception) {
                fail(error)
            } finally {
                getOrNull<MemoryEditUiState.Normal>()?.copy(busy = false)?.setup()
            }
        }
    }

    /** 外部取消继续传播；已删除目标提示并关闭，冲突保留草稿。 */
    private fun fail(error: Throwable) {
        // 协程外部取消必须直接传播，不得吞掉。
        if (error is CancellationException) throw error
        // 将受控业务异常映射为本地化提示资源，不暴露内部异常原文。
        val resource = when (error) {
            is MemoryTargetMissingException -> R.string.memory_target_missing
            is MemoryConflictException -> R.string.memory_conflict
            else -> R.string.memory_failed
        }
        AppViewEvent.PopupToastMessageByResId(resource).tryEmit()
        // 目标已删除时安全结束页面，防止重复包裹 Finished 状态。
        if (error is MemoryTargetMissingException) {
            val currentState = uiStateFlow.value
            if (currentState !is MemoryEditUiState.Finished) {
                MemoryEditUiState.Finished(currentState).setup()
            }
            return
        }
        // 首次加载阶段失败时切换为可重试错误态，保留已有草稿。
        getOrNull<MemoryEditUiState.Normal>()?.let { state ->
            if (state.content.loading) {
                state.copy(
                    content = MemoryEditContentState(
                        loading = false,
                        failed = true,
                    ),
                ).setup()
            }
        }
    }
}
