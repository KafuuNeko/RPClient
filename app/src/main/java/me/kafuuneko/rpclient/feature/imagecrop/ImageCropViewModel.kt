package me.kafuuneko.rpclient.feature.imagecrop

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagecrop.model.BackgroundCropResult
import me.kafuuneko.rpclient.feature.imagecrop.model.ImageCropTransform
import me.kafuuneko.rpclient.feature.imagecrop.presentation.CropMaskShape
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropAspectRatio
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropPurpose
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiIntent
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.media.ImageCropCodec
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 通用裁剪页的状态持有者。
 * - 共用矩形选区与变换逻辑，头像保存方形文件，背景交回选区供源图管线处理。
 * - 后台解码可取消，加载时返回不会在解码完成后重新打开编辑界面。
 */
class ImageCropViewModel : CoreViewModelWithEvent<ImageCropUiIntent, ImageCropUiState>(
    initStatus = ImageCropUiState.None
), KoinComponent {
    private val mImageCropCodec by inject<ImageCropCodec>()
    private val mFileRepository by inject<FileRepository>()
    private var mSourceBitmap: Bitmap? = null
    private var mSourceUri: Uri? = null
    private var mScreenAspectRatio = 1f
    private var mLoadingJob: Job? = null

    /**
     * 异步解码图片，按头像或背景用途构建初始裁剪框。
     */
    @UiIntentObserver(ImageCropUiIntent.Init::class)
    private fun onInit(intent: ImageCropUiIntent.Init) {
        if (!isStateOf<ImageCropUiState.None>()) return
        val sourceUri = intent.sourceUri
        // 校验输入 URI 是否有效
        if (sourceUri == null) {
            ImageCropUiState.Failed(ImageCropUiState.None).setup()
            return
        }
        val loading = ImageCropUiState.Loading(intent.purpose)
        loading.setup()
        mSourceUri = sourceUri
        mScreenAspectRatio = intent.screenAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        // 独立任务让返回意图能够取消加载，避免长时间读取云端相册占用意图队列。
        mLoadingJob = viewModelScope.launch {
            try {
                val bitmap = mImageCropCodec.loadBitmapForCrop(sourceUri)
                if (bitmap == null) {
                    ImageCropUiState.Failed(loading).setup()
                    return@launch
                }
                mSourceBitmap = bitmap
                val isBackground = intent.purpose == ImageCropPurpose.Background
                ImageCropUiState.Normal(
                    image = bitmap.asImageBitmap(),
                    transform = ImageCropTransform(
                        sourceAspectRatio = bitmap.width.toFloat() / bitmap.height,
                        cropAspectRatio = if (isBackground) mScreenAspectRatio else 1f,
                    ),
                    maskShape = if (isBackground) CropMaskShape.Rectangle else CropMaskShape.Squircle,
                    purpose = intent.purpose,
                ).setup()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ImageCropUiState.Failed(loading).setup()
            } catch (_: OutOfMemoryError) {
                ImageCropUiState.Failed(loading).setup()
            }
        }
    }

    /** 手势位移分别按裁剪框宽高归一化，保证矩形与方形采用相同的边界限制。 */
    @UiIntentObserver(ImageCropUiIntent.Transform::class)
    private fun onTransform(intent: ImageCropUiIntent.Transform) {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        uiState.copy(
            transform = uiState.transform.update(
                intent.panX,
                intent.panY,
                intent.zoomChange
            )
        ).setup()
    }

    /** 切换背景构图比例时恢复完整选区，保留已选择的旋转和镜像方向。 */
    @UiIntentObserver(ImageCropUiIntent.SelectAspectRatio::class)
    private fun onSelectAspectRatio(intent: ImageCropUiIntent.SelectAspectRatio) {
        val state = getOrNull<ImageCropUiState.Normal>() ?: return
        if (state.saving || state.purpose != ImageCropPurpose.Background) return
        val ratio =
            if (intent.value == ImageCropAspectRatio.Screen) mScreenAspectRatio else state.transform.effectiveAspectRatio
        state.copy(aspectRatio = intent.value, transform = state.transform.withAspectRatio(ratio))
            .setup()
    }

    /** 顺时针旋转图片，原图比例模式同步旋转裁剪框。 */
    @UiIntentObserver(ImageCropUiIntent.RotateRight::class)
    private fun onRotateRight() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        val rotated = uiState.transform.rotateRight()
        val transform =
            if (uiState.purpose == ImageCropPurpose.Background && uiState.aspectRatio == ImageCropAspectRatio.Original) {
                rotated.withAspectRatio(rotated.effectiveAspectRatio)
            } else rotated
        uiState.copy(transform = transform).setup()
    }

    /** 水平翻转图片，保留裁剪框内的有效覆盖范围。 */
    @UiIntentObserver(ImageCropUiIntent.FlipHorizontal::class)
    private fun onFlipHorizontal() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        uiState.copy(transform = uiState.transform.flipHorizontal()).setup()
    }

    /** 头像允许切换显示蒙版，导出的文件仍为正方形。 */
    @UiIntentObserver(ImageCropUiIntent.ToggleMaskShape::class)
    private fun onToggleMaskShape() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving || uiState.purpose != ImageCropPurpose.Avatar) return
        val nextShape = if (uiState.maskShape == CropMaskShape.Squircle) {
            CropMaskShape.Circle
        } else {
            CropMaskShape.Squircle
        }
        uiState.copy(maskShape = nextShape).setup()
    }

    /** 恢复当前用途的默认构图与图片方向。 */
    @UiIntentObserver(ImageCropUiIntent.Reset::class)
    private fun onReset() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        val reset = uiState.transform.reset()
        val transform = if (uiState.purpose == ImageCropPurpose.Background) reset.withAspectRatio(
            mScreenAspectRatio
        ) else reset
        uiState.copy(transform = transform, aspectRatio = ImageCropAspectRatio.Screen).setup()
    }

    /**
     * 确认选区；背景不生成额外文件，头像继续使用独立文件索引。
     */
    @UiIntentObserver(ImageCropUiIntent.Confirm::class)
    private suspend fun onConfirm() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        val bitmap = mSourceBitmap ?: return
        if (uiState.saving) return
        if (uiState.purpose == ImageCropPurpose.Background) {
            val sourceUri = mSourceUri ?: return
            ImageCropViewEvent.FinishWithBackground(
                BackgroundCropResult(sourceUri, uiState.transform.toSelection())
            ).emitAndAwait()
            ImageCropUiState.Finished(uiState).setup()
            return
        }
        // 标记保存中状态
        uiState.copy(saving = true).setup()
        // 执行像素裁剪与文件持久化
        val fileUuid = runCatching {
            val encoded = mImageCropCodec.encodeAvatarCrop(bitmap, uiState.transform.toSelection())
            mFileRepository.saveFile(encoded.bytes, encoded.mimeType)
        }.getOrElse {
            uiState.copy(saving = false).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.image_crop_save_failed).tryEmit()
            return
        }
        // 发送成功事件并关闭页面
        ImageCropViewEvent.FinishWithResult(fileUuid).emitAndAwait()
        ImageCropUiState.Finished(uiState.copy(saving = false)).setup()
    }

    /** 保存期间保护文件提交，其余状态允许退出并取消尚未完成的解码。 */
    @UiIntentObserver(ImageCropUiIntent.Back::class)
    private fun onBack() {
        val uiState = uiStateFlow.value
        if (uiState is ImageCropUiState.Finished) return
        if (uiState is ImageCropUiState.Normal && uiState.saving) return
        mLoadingJob?.cancel()
        ImageCropUiState.Finished(uiState).setup()
    }

    override fun onCleared() {
        mSourceBitmap?.takeIf { !it.isRecycled }?.recycle()
        mSourceBitmap = null
        super.onCleared()
    }
}
