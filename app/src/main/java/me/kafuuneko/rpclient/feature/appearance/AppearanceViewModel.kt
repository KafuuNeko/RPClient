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
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorState
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.PageBackground
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

    init {
        viewModelScope.launch {
            combine(mThemes.paletteFlow, mThemes.themeModeFlow) { palette, mode -> palette to mode }
                .collect { (palette, mode) ->
                    val state = getOrNull<AppearanceUiState.Normal>() ?: return@collect
                    state.copy(palette = palette, mode = mode).setup()
                }
        }
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
        state.copy(editor = BackgroundEditorState(PageBackground(intent.page), working = true)).setup()
        mWorkJob = viewModelScope.launch {
            try {
                val settings = mBackgrounds.observe(intent.page).first()
                updateEditor { it.copy(settings = settings, working = false) }
                requestPreview(false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(working = false, error = R.string.appearance_background_error) } }
        }
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
        if (editor.working || !intent.value.isFinite()) return
        // UI 使用透明度百分比，持久化与颜色渲染统一使用不透明度。
        val value = intent.value
        val settings = when (intent.field) {
            BackgroundAdjustment.Blur -> editor.settings.copy(blur = value.roundToInt())
            BackgroundAdjustment.Scrim -> editor.settings.copy(scrim = value)
            BackgroundAdjustment.ContentTransparency -> editor.settings.copy(opacity = 1f - value)
            BackgroundAdjustment.TopBarTransparency -> editor.settings.copy(topBarOpacity = 1f - value)
            BackgroundAdjustment.BottomBarTransparency -> editor.settings.copy(bottomBarOpacity = 1f - value)
            BackgroundAdjustment.BubbleTransparency -> editor.settings.copy(bubbleOpacity = 1f - value)
            BackgroundAdjustment.Horizontal -> editor.settings.copy(focalX = value)
            BackgroundAdjustment.Vertical -> editor.settings.copy(focalY = value)
        }.normalized()
        updateEditor { it.copy(settings = settings) }
        if (intent.field == BackgroundAdjustment.Blur) requestPreview(true)
    }

    /** 用户松开滑块后生成最终尺寸，位移与遮罩变化复用已有图片缓存。 */
    @UiIntentObserver(AppearanceUiIntent.FinishAdjustment::class)
    private fun onFinishAdjustment() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (!editor.working) requestPreview(false)
    }

    /** 恢复主题底色仅改变草稿，直到应用后才释放已提交文件。 */
    @UiIntentObserver(AppearanceUiIntent.ResetBackground::class)
    private fun onResetBackground() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working) return
        mPreviewJob?.cancel()
        mRuntime.discard(mPrepared)
        mPrepared = null
        updateEditor { it.copy(settings = it.settings.copy(fileUuid = null), image = null, rendering = false, error = null) }
    }

    /** 文件与配置成功提交后再关闭编辑器，失败时仍可重试或取消。 */
    @UiIntentObserver(AppearanceUiIntent.ApplyBackground::class)
    private fun onApplyBackground() {
        val editor = getOrNull<AppearanceUiState.Normal>()?.editor ?: return
        if (editor.working) return
        mSaving = true
        mPreviewJob?.cancel()
        updateEditor { it.copy(working = true, error = null) }
        mWorkJob = viewModelScope.launch {
            // 文件仓库负责发布和回收，UI 不先行删除旧引用。
            try {
                mBackgrounds.save(editor.settings, mPrepared)
                mPrepared = null
                getOrNull<AppearanceUiState.Normal>()?.copy(editor = null)?.setup()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(working = false, error = R.string.appearance_background_error) } }
            finally { mSaving = false }
        }
    }

    /** 返回取消后台预览与导入；正在提交的短事务不被重复返回打断。 */
    @UiIntentObserver(AppearanceUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        if (mSaving) return
        mWorkJob?.cancel()
        mPreviewJob?.cancel()
        mRuntime.discard(mPrepared)
        mPrepared = null
        if (state.editor != null) state.copy(editor = null).setup()
        else AppearanceUiState.Finished(state).setup()
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
                val image = mRuntime.render(editor.settings, prepared, preview)?.asImageBitmap()
                updateEditor { it.copy(image = image, rendering = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { updateEditor { it.copy(rendering = false, error = R.string.appearance_background_error) } }
            catch (_: OutOfMemoryError) { updateEditor { it.copy(rendering = false, error = R.string.appearance_image_error) } }
        }
    }

    /** 每次更新基于最新父状态，避免主题流与预览结果互相覆盖。 */
    private fun updateEditor(change: (BackgroundEditorState) -> BackgroundEditorState) {
        val state = getOrNull<AppearanceUiState.Normal>() ?: return
        val editor = state.editor ?: return
        state.copy(editor = change(editor)).setup()
    }

    /** 页面销毁后释放未提交源图，配置旋转由保留的 ViewModel 继续承载。 */
    override fun onCleared() {
        mRuntime.discard(mPrepared)
        super.onCleared()
    }
}
