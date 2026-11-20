package me.kafuuneko.rpclient.feature.memorycenter

import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterContentState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterDialogState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterSettingsState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterUiIntent
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterUiState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCharacterItem
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryRecallLimitField
import me.kafuuneko.rpclient.feature.memoryedit.MemoryEditActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 角色记忆目录状态持有者。
 * - 目录直接观察聚合数量，不查询正文或证据。
 * - 仅为可见卡片读取下采样头像，离开视口的位图及时释放。
 * - 召回额度通过数值弹窗校验并持久化到全局配置。
 */
class MemoryCenterViewModel :
    CoreViewModelWithEvent<MemoryCenterUiIntent, MemoryCenterUiState>(MemoryCenterUiState.None),
    KoinComponent {
    private val mRepository by inject<MemoryRepository>()
    private val mFiles by inject<FileRepository>()
    private var mSubscription: Job? = null
    private var mAvatars: Job? = null
    private var mVisible = emptySet<Long>()

    /** 初始化全局额度配置后启动唯一目录订阅。 */
    @UiIntentObserver(MemoryCenterUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<MemoryCenterUiState.None>()) return
        MemoryCenterUiState.Normal(
            settings = MemoryCenterSettingsState(
                maxItems = AppModel.normalMemoryMaxItems,
                maxTokens = AppModel.normalMemoryMaxTokens,
            ),
        ).setup()
        subscribe()
    }

    /** 读取失败可重试，不能把失败展示为空目录。 */
    @UiIntentObserver(MemoryCenterUiIntent.Retry::class)
    private fun onRetry() {
        val state = getOrNull<MemoryCenterUiState.Normal>() ?: return
        state.copy(content = state.content.copy(loading = true, failed = false)).setup()
        subscribe()
    }

    /** 聚合包含零条角色和全部状态、人设，不以可召回条数替代保存总数。 */
    private fun subscribe() {
        mSubscription?.cancel()
        mSubscription = viewModelScope.launch {
            // 订阅聚合查询并在异常时切到可重试的失败状态。
            mRepository.observeCharacters().catch { error ->
                if (error is CancellationException) throw error
                getOrNull<MemoryCenterUiState.Normal>()?.copy(
                    content = MemoryCenterContentState(
                        loading = false,
                        failed = true,
                    ),
                )?.setup()
            }.collect { rows ->
                val state = getOrNull<MemoryCenterUiState.Normal>() ?: return@collect
                // 刷新目录列表并保留当前已解码头像，避免重新加载时闪烁。
                val existingImages = state.content.items.associate { it.id to it.image }
                state.copy(
                    content = MemoryCenterContentState(
                        loading = false,
                        items = rows.map { row ->
                            MemoryCharacterItem(
                                id = row.id,
                                name = row.name,
                                count = row.memoryCount,
                                avatarUuid = row.avatar,
                                image = existingImages[row.id],
                            )
                        },
                    ),
                ).setup()
                loadAvatars()
            }
        }
    }

    /**
     * 导航至角色记忆编辑器，仅传递角色真实 ID，改名或同名卡片不会打开错误目标。
     *
     * @param intent 包含目标角色主键的打开意图
     */
    @UiIntentObserver(MemoryCenterUiIntent.OpenCharacter::class)
    private fun onOpenCharacter(intent: MemoryCenterUiIntent.OpenCharacter) {
        if (intent.id <= 0) return
        AppViewEvent.StartActivity(
            MemoryEditActivity::class.java,
            MemoryEditActivity.extras(MemoryTarget.Character(intent.id)),
        ).tryEmit()
    }

    /**
     * 可见范围变化时记录当前视口角色并取消过时的头像加载。
     *
     * @param intent 包含当前视口可见角色主键集合的意图
     */
    @UiIntentObserver(MemoryCenterUiIntent.VisibleCharactersChanged::class)
    private fun onVisibleCharactersChanged(intent: MemoryCenterUiIntent.VisibleCharactersChanged) {
        mVisible = intent.ids
        loadAvatars()
    }

    /** 下采样读取放在文件仓库 IO 路径，列表只保留当前视口位图。 */
    private fun loadAvatars() {
        mAvatars?.cancel()
        mAvatars = viewModelScope.launch {
            val state = getOrNull<MemoryCenterUiState.Normal>() ?: return@launch
            // 仅为当前视口内存在头像的角色解码缩略图。
            val images = state.content.items
                .filter { it.id in mVisible && it.avatarUuid.isNotBlank() }
                .associate { item ->
                    val image = try {
                        mFiles.loadSampledBitmap(item.avatarUuid, 144, 144)?.asImageBitmap()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        null
                    }
                    item.id to image
                }
            // 将最新视口位图写回状态，离开视口的项自动释放引用。
            val current = getOrNull<MemoryCenterUiState.Normal>() ?: return@launch
            current.copy(
                content = current.content.copy(
                    items = current.content.items.map { item ->
                        item.copy(image = images[item.id])
                    },
                ),
            ).setup()
        }
    }

    /**
     * 打开对应额度项的数值编辑弹窗。
     *
     * @param intent 包含待编辑额度字段的点击意图
     */
    @UiIntentObserver(MemoryCenterUiIntent.EditLimitClick::class)
    private fun onEditLimitClick(intent: MemoryCenterUiIntent.EditLimitClick) {
        val state = getOrNull<MemoryCenterUiState.Normal>() ?: return
        val currentValue = when (intent.field) {
            MemoryRecallLimitField.MaxItems -> state.settings.maxItems
            MemoryRecallLimitField.MaxTokens -> state.settings.maxTokens
        }
        state.copy(
            dialog = MemoryCenterDialogState.EditLimit(
                field = intent.field,
                draft = currentValue.toString(),
            ),
        ).setup()
    }

    /**
     * 更新额度编辑弹窗中的草稿原文。
     *
     * @param intent 包含最新数值输入草稿的意图
     */
    @UiIntentObserver(MemoryCenterUiIntent.ChangeLimitDraft::class)
    private fun onChangeLimitDraft(intent: MemoryCenterUiIntent.ChangeLimitDraft) {
        val state = getOrNull<MemoryCenterUiState.Normal>() ?: return
        val dialog = state.dialog as? MemoryCenterDialogState.EditLimit ?: return
        state.copy(dialog = dialog.copy(draft = intent.value)).setup()
    }

    /** 校验并保存普通回复召回额度，非法范围提示并保留弹窗输入。 */
    @UiIntentObserver(MemoryCenterUiIntent.ConfirmLimit::class)
    private fun onConfirmLimit() {
        val state = getOrNull<MemoryCenterUiState.Normal>() ?: return
        val dialog = state.dialog as? MemoryCenterDialogState.EditLimit ?: return
        // 解析整数并按字段范围校验。
        val parsed = dialog.draft.trim().toIntOrNull()
        val validRange = when (dialog.field) {
            MemoryRecallLimitField.MaxItems -> 0..MAX_RECALL_ITEMS
            MemoryRecallLimitField.MaxTokens -> 0..MAX_RECALL_TOKENS
        }
        if (parsed == null || parsed !in validRange) {
            AppViewEvent.PopupToastMessageByResId(R.string.memory_limits_invalid).tryEmit()
            return
        }
        // 持久化合法额度并关闭编辑弹窗。
        val updatedSettings = when (dialog.field) {
            MemoryRecallLimitField.MaxItems -> {
                AppModel.normalMemoryMaxItems = parsed
                state.settings.copy(maxItems = parsed)
            }

            MemoryRecallLimitField.MaxTokens -> {
                AppModel.normalMemoryMaxTokens = parsed
                state.settings.copy(maxTokens = parsed)
            }
        }
        state.copy(
            settings = updatedSettings,
            dialog = MemoryCenterDialogState.None,
        ).setup()
    }

    /** 关闭当前数值编辑弹窗。 */
    @UiIntentObserver(MemoryCenterUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<MemoryCenterUiState.Normal>() ?: return
        state.copy(dialog = MemoryCenterDialogState.None).setup()
    }

    /** 优先关闭弹窗，否则保留最后一帧供宿主结束动画使用。 */
    @UiIntentObserver(MemoryCenterUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<MemoryCenterUiState.Normal>() ?: return
        if (state.dialog != MemoryCenterDialogState.None) {
            onDismissDialog()
            return
        }
        MemoryCenterUiState.Finished(state).setup()
    }

    companion object {
        private const val MAX_RECALL_ITEMS = 100
        private const val MAX_RECALL_TOKENS = 32768
    }
}
