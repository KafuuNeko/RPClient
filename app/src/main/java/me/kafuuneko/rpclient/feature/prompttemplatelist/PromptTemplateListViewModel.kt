package me.kafuuneko.rpclient.feature.prompttemplatelist

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.PromptPresetActivity
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateContentState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateDialogState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateItem
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiIntent
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateMetadataMode
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetConflictException
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 整套系统提示词预设模板的管理状态持有者。
 * - 订阅数据库列表，统一映射显示名称、覆盖项数量与激活状态。
 * - 通过 Repository 完成事务写入，失败时保留用户输入草稿。
 */
class PromptTemplateListViewModel :
    CoreViewModelWithEvent<PromptTemplateListUiIntent, PromptTemplateListUiState>(
        PromptTemplateListUiState.None
    ), KoinComponent {
    private val mRepository by inject<PromptPresetRepository>()
    private val mContext by inject<Context>()
    private var mSubscription: Job? = null
    private var mItems: List<PromptTemplateItem> = emptyList()

    /** 首次进入后启动唯一列表订阅。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<PromptTemplateListUiState.None>()) return
        PromptTemplateListUiState.Normal().setup()
        subscribe()
    }

    /** 读取失败时重新订阅，避免将错误展示为空列表。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Retry::class)
    private fun onRetry() {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        state.copy(contentState = PromptTemplateContentState.Loading).setup()
        subscribe()
    }

    /** 保存期间禁止离开，结束时保留最后的渲染状态。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        if (state.busy) return
        PromptTemplateListUiState.finished(state).setup()
    }

    /** 新建空覆盖的默认配置，只需填写名称和简介。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Create::class)
    private fun onCreate() {
        showDialog(
            PromptTemplateDialogState.Metadata(
                mode = PromptTemplateMetadataMode.Create,
                sourceId = PromptTemplateSnapshot.BUILTIN_ID,
            )
        )
    }

    /** 跳转详情，页面参数只包含稳定模板 ID。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Open::class)
    private fun onOpen(intent: PromptTemplateListUiIntent.Open) {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        if (state.busy) return
        openDetail(intent.id)
    }

    /** 启用完成后下一次生成立即使用新选择。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Activate::class)
    private fun onActivate(intent: PromptTemplateListUiIntent.Activate) = perform {
        mRepository.activate(intent.id)
        AppViewEvent.PopupToastMessageByResId(R.string.prompt_template_activated).tryEmit()
    }

    /** 复制仅创建草稿，确认名称前不写入数据库。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Copy::class)
    private fun onCopy(intent: PromptTemplateListUiIntent.Copy) {
        val item = mItems.find { presetItem -> presetItem.id == intent.id } ?: return
        showDialog(
            PromptTemplateDialogState.Metadata(
                mode = PromptTemplateMetadataMode.Copy,
                sourceId = item.id,
                sourceName = item.name,
                name = mContext.getString(R.string.prompt_template_copy_name, item.name),
                description = item.description,
            )
        )
    }

    /** 使用当前 revision 编辑元数据，避免覆盖另一页面的修改。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Rename::class)
    private fun onRename(intent: PromptTemplateListUiIntent.Rename) {
        val item = mItems.find { presetItem ->
            presetItem.id == intent.id && !presetItem.isBuiltin
        } ?: return
        showDialog(
            PromptTemplateDialogState.Metadata(
                mode = PromptTemplateMetadataMode.Edit,
                sourceId = item.id,
                targetId = item.id,
                revision = item.revision,
                name = item.name,
                description = item.description,
            )
        )
    }

    /** 删除当前模板时在弹窗明确展示切回默认的后果。 */
    @UiIntentObserver(PromptTemplateListUiIntent.Delete::class)
    private fun onDelete(intent: PromptTemplateListUiIntent.Delete) {
        val item = mItems.find { presetItem ->
            presetItem.id == intent.id && !presetItem.isBuiltin
        } ?: return
        showDialog(PromptTemplateDialogState.Delete(item))
    }

    /** 更新名称草稿。 */
    @UiIntentObserver(PromptTemplateListUiIntent.ChangeName::class)
    private fun onChangeName(intent: PromptTemplateListUiIntent.ChangeName) {
        updateMetadata { copy(name = intent.value) }
    }

    /** 更新简介草稿。 */
    @UiIntentObserver(PromptTemplateListUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: PromptTemplateListUiIntent.ChangeDescription) {
        updateMetadata { copy(description = intent.value) }
    }

    /** 原子创建或保存元数据，成功后再关闭草稿。 */
    @UiIntentObserver(PromptTemplateListUiIntent.SaveMetadata::class)
    private fun onSaveMetadata() {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        val draft = state.dialogState as? PromptTemplateDialogState.Metadata ?: return
        if (draft.name.isBlank()) return
        // 草稿保留到数据库提交成功，随后再关闭弹窗并按模式决定是否进入详情。
        perform {
            if (draft.mode == PromptTemplateMetadataMode.Edit && draft.targetId != null) {
                mRepository.rename(
                    id = draft.targetId,
                    revision = draft.revision,
                    name = draft.name,
                    description = draft.description,
                )
                closeDialog()
            } else {
                val id = mRepository.create(
                    sourceId = draft.sourceId,
                    name = draft.name,
                    description = draft.description,
                )
                closeDialog()
                openDetail(id)
            }
        }
    }

    /** 删除与默认切换交给同一 Repository 事务。 */
    @UiIntentObserver(PromptTemplateListUiIntent.ConfirmDelete::class)
    private fun onConfirmDelete() {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptTemplateDialogState.Delete ?: return
        perform {
            mRepository.delete(dialog.item.id)
            closeDialog()
        }
    }

    /** 取消弹窗不会保存新模板或修改现有内容。 */
    @UiIntentObserver(PromptTemplateListUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        if (!state.busy) closeDialog()
    }

    /** 订阅带覆盖项统计的列表流，避免激活态与卡片来自不同事务。 */
    private fun subscribe() {
        mSubscription?.cancel()
        mSubscription = viewModelScope.launch {
            mRepository.observePresets().catch { error ->
                if (error is CancellationException) throw error
                getOrNull<PromptTemplateListUiState.Normal>()
                    ?.copy(contentState = PromptTemplateContentState.Failed)
                    ?.setup()
            }.collect { rows ->
                // 名称与时间在 UI 之外映射，系统默认名称随界面语言变化。
                val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                mItems = rows.map { row ->
                    val preset = row.preset
                    val isBuiltin = preset.id == PromptTemplateSnapshot.BUILTIN_ID
                    PromptTemplateItem(
                        id = preset.id,
                        name = if (isBuiltin) {
                            mContext.getString(R.string.prompt_template_default)
                        } else {
                            preset.name
                        },
                        description = preset.description,
                        isBuiltin = isBuiltin,
                        isActive = row.isActive,
                        revision = preset.revision,
                        updatedTime = if (isBuiltin) "" else formatter.format(Date(preset.updatedAt)),
                        overrideCount = row.overrideCount,
                    )
                }
                publishItems()
            }
        }
    }

    /** 校验唯一有效启用项后发布列表，损坏状态不伪装成默认模板。 */
    private fun publishItems() {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        val active = mItems.singleOrNull { presetItem -> presetItem.isActive }
        if (active == null) {
            state.copy(contentState = PromptTemplateContentState.Failed).setup()
            return
        }
        state.copy(
            contentState = PromptTemplateContentState.Ready(items = mItems)
        ).setup()
    }

    /** 统一异步写入错误处理，保留弹窗草稿，不暴露异常原文。 */
    private fun perform(block: suspend () -> Unit) {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        if (state.busy) return
        state.copy(busy = true).setup()
        // 不恢复操作前的整页快照，保留订阅期间收到的最新列表。
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if (error is PromptPresetConflictException) {
                    R.string.prompt_template_conflict
                } else {
                    R.string.prompt_template_failed
                }
                AppViewEvent.PopupToastMessageByResId(message).tryEmit()
            } finally {
                getOrNull<PromptTemplateListUiState.Normal>()?.copy(busy = false)?.setup()
            }
        }
    }

    private fun showDialog(dialog: PromptTemplateDialogState) {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        if (!state.busy) state.copy(dialogState = dialog).setup()
    }

    private fun updateMetadata(
        update: PromptTemplateDialogState.Metadata.() -> PromptTemplateDialogState.Metadata,
    ) {
        val state = getOrNull<PromptTemplateListUiState.Normal>() ?: return
        val draft = state.dialogState as? PromptTemplateDialogState.Metadata ?: return
        if (!state.busy) state.copy(dialogState = draft.update()).setup()
    }

    private fun closeDialog() {
        getOrNull<PromptTemplateListUiState.Normal>()
            ?.copy(dialogState = PromptTemplateDialogState.None)
            ?.setup()
    }

    /** 页面跳转只携带模板 ID，详情由目标 ViewModel 读取。 */
    private fun openDetail(id: Long) {
        AppViewEvent.StartActivity(
            PromptPresetActivity::class.java,
            Bundle().apply { putLong(PromptPresetActivity.EXTRA_PRESET_ID, id) },
        ).tryEmit()
    }
}
