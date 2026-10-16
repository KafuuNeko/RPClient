package me.kafuuneko.rpclient.feature.appearance

import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiIntent
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiState
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceViewEvent
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundAdjustment
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorDialogState
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorState
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.resolveWith
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 外观设置的 MVI 状态持有者。
 * - 主题选择立即生效，背景设置保留独立草稿，仅应用时提交。
 * - 预览任务可取消，源图暂存随取消或页面销毁回收。
 */
class AppearanceViewModel : CoreViewModelWithEvent<AppearanceUiIntent, AppearanceUiState>(
    AppearanceUiState.Normal()
), KoinComponent {
    private val mThemes by inject<AppThemeManager>()
    private val mBackgrounds by inject<PageBackgroundRepository>()
    private val mRuntime by inject<BackgroundImageRuntime>()
    private val mOwner = UUID.randomUUID().toString()
    private var mPrepared: PreparedFile? = null
    private var mPreviewJob: Job? = null
    private var mWorkJob: Job? = null
    private var mSaving = false
    private var mOriginalSettings: PageBackground? = null
    private var mGeneralSettings = PageBackground(BackgroundPage.General)

    init {
        viewModelScope.launch {
            combine(mThemes.paletteFlow, mThemes.themeModeFlow) { palette, mode -> palette to mode }
                .collect { (palette, mode) ->
                    val state = getOrNull<AppearanceUiState.Normal>() ?: return@collect
                    state.copy(palette = palette, mode = mode).setup()
                }
        }
    }

    /** 独立编辑对话框，不占用页面背景草稿或图片资源。 */
    @UiIntentObserver(AppearanceUiIntent.EditDialogAppearance::class)
    private fun onEditDialogAppearance() {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        if (state.editor == null) AppearanceViewEvent.OpenDialogAppearance.tryEmit()
    }

    /** 切换配色不修改背景草稿。 */
    @UiIntentObserver(AppearanceUiIntent.SelectPalette::class)
    private fun onSelectPalette(intent: AppearanceUiIntent.SelectPalette) {
        if (getOrNull<AppearanceUiState.Normal>() == null) return
        mThemes.setPalette(intent.palette)
    }

    /** 保存明暗偏好，当前编辑预览同步使用实际主题。 */
    @UiIntentObserver(AppearanceUiIntent.SelectMode::class)
    private fun onSelectMode(intent: AppearanceUiIntent.SelectMode) {
        if (getOrNull<AppearanceUiState.Normal>() == null) return
        mThemes.setThemeMode(intent.mode)
    }

    /** 加载页面的已提交背景，编辑过程与其他内容页隔离。 */
    @UiIntentObserver(AppearanceUiIntent.EditBackground::class)
    private fun onEditBackground(intent: AppearanceUiIntent.EditBackground) {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        if (state.editor != null) return
        // 加载期间禁用编辑，避免默认参数抢先覆盖已有配置。
        val initial = PageBackground(intent.page)
        mOriginalSettings = initial
        state.copy(editor = BackgroundEditorState(initial, working = true)).setup()
        mWorkJob = viewModelScope.launch {
            try {
                val settings = mBackgrounds.observe(intent.page).first()
                mGeneralSettings = mBackgrounds.observe(BackgroundPage.General).first()
                mOriginalSettings = settings
                updateEditor { it.copy(settings = settings, working = false) }
                requestPreview(false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(working = false, error = R.string.appearance_background_error) } }
        }
    }

    /** 跟随开关只改变解析策略，保留用户以前的独立参数供关闭后恢复。 */
    @UiIntentObserver(AppearanceUiIntent.SetFollowGeneral::class)
    private fun onSetFollowGeneral(intent: AppearanceUiIntent.SetFollowGeneral) {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working || editor.settings.page == BackgroundPage.General) return
        updateEditor { it.copy(settings = it.settings.copy(followGeneral = intent.enabled)) }
        requestPreview(false)
    }

    /** 聊天页按角色选择背景，开关与图片继承、参数继承相互独立。 */
    @UiIntentObserver(AppearanceUiIntent.SetUseCharacterBackground::class)
    private fun onSetUseCharacterBackground(intent: AppearanceUiIntent.SetUseCharacterBackground) {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working || editor.settings.page != BackgroundPage.Chat) return
        updateEditor { it.copy(settings = it.settings.copy(useCharacterBackground = intent.enabled)) }
    }

    /** 发出系统选图事件，已有导入或保存尚未完成时忽略重复操作。 */
    @UiIntentObserver(AppearanceUiIntent.PickImage::class)
    private fun onPickImage() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (!editor.working) AppearanceViewEvent.PickImage.tryEmit()
    }

    /** 完成校验后才替换草稿；失败时保留原背景供重试。 */
    @UiIntentObserver(AppearanceUiIntent.ImageSelected::class)
    private fun onImageSelected(intent: AppearanceUiIntent.ImageSelected) {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working) return
        mPreviewJob?.cancel()
        updateEditor { it.copy(working = true, error = null) }
        mWorkJob = viewModelScope.launch {
            // 导入拥有独立生命周期，取消不会影响已经保存的文件。
            try {
                val prepared = mRuntime.prepare(mOwner, intent.uri)
                mRuntime.discard(mPrepared)
                mPrepared = prepared
                updateEditor { it.copy(settings = it.settings.copy(fileUuid = prepared.file.uuid), working = false) }
                requestPreview(false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(working = false, error = R.string.appearance_image_error) } }
            catch (_: OutOfMemoryError) { updateEditor { it.copy(working = false, error = R.string.appearance_image_error) } }
        }
    }

    /** 高频滑块只更新草稿，并取消过期的小图预览任务。 */
    @UiIntentObserver(AppearanceUiIntent.ChangeAdjustment::class)
    private fun onChangeAdjustment(intent: AppearanceUiIntent.ChangeAdjustment) {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working || !intent.value.isFinite() ||
            (editor.settings.page != BackgroundPage.General && editor.settings.followGeneral)) return
        // UI 使用透明度百分比，持久化与颜色渲染统一使用不透明度。
        val value = intent.value
        val settings = when (intent.field) {
            BackgroundAdjustment.Blur -> editor.settings.copy(blur = value.roundToInt())
            BackgroundAdjustment.TopBarBlur -> editor.settings.copy(topBarBlur = value.roundToInt())
            BackgroundAdjustment.BottomBarBlur -> editor.settings.copy(bottomBarBlur = value.roundToInt())
            BackgroundAdjustment.BubbleBlur -> editor.settings.copy(bubbleBlur = value.roundToInt())
            BackgroundAdjustment.ContentBlur -> editor.settings.copy(contentBlur = value.roundToInt())
            BackgroundAdjustment.Scrim -> editor.settings.copy(scrim = value)
            BackgroundAdjustment.ContentTransparency -> editor.settings.copy(opacity = 1f - value)
            BackgroundAdjustment.TopBarTransparency -> editor.settings.copy(topBarOpacity = 1f - value)
            BackgroundAdjustment.BottomBarTransparency -> editor.settings.copy(bottomBarOpacity = 1f - value)
            BackgroundAdjustment.BubbleTransparency -> editor.settings.copy(bubbleOpacity = 1f - value)
            BackgroundAdjustment.Horizontal -> editor.settings.copy(focalX = value)
            BackgroundAdjustment.Vertical -> editor.settings.copy(focalY = value)
        }.normalized()
        updateEditor { it.copy(settings = settings) }
        if (intent.field.isBlur || intent.field.isTransparency) requestPreview(true)
    }

    /** 用户松开滑块后生成最终尺寸，位移与遮罩变化复用已有图片缓存。 */
    @UiIntentObserver(AppearanceUiIntent.FinishAdjustment::class)
    private fun onFinishAdjustment() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (!editor.working) requestPreview(false)
    }

    /** 移除独立图片仅改变草稿；页面可回退通用图片，保存后才释放旧文件。 */
    @UiIntentObserver(AppearanceUiIntent.ResetBackground::class)
    private fun onResetBackground() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working) return
        mPreviewJob?.cancel()
        mRuntime.discard(mPrepared)
        mPrepared = null
        updateEditor { it.copy(settings = it.settings.copy(fileUuid = null), image = null, surfaceImages = emptyMap(), rendering = false, error = null) }
        requestPreview(false)
    }

    /** 文件与配置成功提交后再关闭编辑器，失败时仍可重试或取消。 */
    @UiIntentObserver(AppearanceUiIntent.ApplyBackground::class)
    private fun onApplyBackground() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working) return
        mSaving = true
        mPreviewJob?.cancel()
        updateEditor { it.copy(working = true, error = null, dialogState = BackgroundEditorDialogState.None) }
        mWorkJob = viewModelScope.launch {
            // 文件仓库负责发布和回收，UI 不先行删除旧引用。
            try {
                mBackgrounds.save(editor.settings, mPrepared)
                mPrepared = null
                mOriginalSettings = null
                getOrNull<AppearanceUiState.Normal>()?.copy(editor = null)?.setup()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(working = false, error = R.string.appearance_background_error) } }
            finally { mSaving = false }
        }
    }

    /** 有未提交修改时先询问保存；无修改则沿用直接返回。 */
    @UiIntentObserver(AppearanceUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        if (mSaving) return
        val editor = state.editor
        if (editor != null && editor.settings != mOriginalSettings) {
            updateEditor { it.copy(dialogState = BackgroundEditorDialogState.UnsavedChanges) }
            return
        }
        if (editor != null) closeEditor() else AppearanceUiState.Finished(state).setup()
    }

    /** 明确选择放弃后才释放临时图片，已提交配置保持不变。 */
    @UiIntentObserver(AppearanceUiIntent.DiscardBackgroundChanges::class)
    private fun onDiscardBackgroundChanges() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (mSaving || editor.dialogState != BackgroundEditorDialogState.UnsavedChanges) return
        closeEditor()
    }

    /** 关闭询问只恢复编辑，不更改草稿或取消仍在进行的预览。 */
    @UiIntentObserver(AppearanceUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (mSaving || editor.dialogState == BackgroundEditorDialogState.None) return
        updateEditor { it.copy(dialogState = BackgroundEditorDialogState.None) }
    }

    /** 返回外观列表并结束草稿生命周期，取消的后台任务不能再覆盖下一次编辑。 */
    private fun closeEditor() {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        mWorkJob?.cancel()
        mPreviewJob?.cancel()
        mRuntime.discard(mPrepared)
        mPrepared = null
        mOriginalSettings = null
        state.copy(editor = null).setup()
    }

    /** 预览请求只由外观参数触发，不参与聊天或正文的高频状态流。 */
    private fun requestPreview(preview: Boolean) {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        mPreviewJob?.cancel()
        val prepared = mPrepared
        updateEditor { it.copy(rendering = true, error = null) }
        mPreviewJob = viewModelScope.launch {
            try {
                if (preview) delay(80)
                // 底图与栏位派生图一起发布，避免同一次预览出现不同处理版本。
                val image = mRuntime.render(editor.effectiveSettings, prepared, preview)?.asImageBitmap()
                val surfaces = mRuntime.renderSurfaces(editor.effectiveSettings, prepared, preview)
                    .mapValues { it.value.asImageBitmap() }
                updateEditor { it.copy(image = image, surfaceImages = surfaces, rendering = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(rendering = false, error = R.string.appearance_background_error) } }
            catch (_: OutOfMemoryError) { updateEditor { it.copy(rendering = false, error = R.string.appearance_image_error) } }
        }
    }

    /** 每次更新基于最新父状态，避免主题流与预览结果互相覆盖。 */
    private fun updateEditor(change: (BackgroundEditorState) -> BackgroundEditorState) {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        val editor = state.editor ?: return
        val updated = change(editor)
        state.copy(editor = updated.copy(effectiveSettings = updated.settings.resolveWith(mGeneralSettings))).setup()
    }

    /** 页面销毁后释放未提交源图，配置旋转由保留的 ViewModel 继续承载。 */
    override fun onCleared() {
        mRuntime.discard(mPrepared)
        super.onCleared()
    }
}
