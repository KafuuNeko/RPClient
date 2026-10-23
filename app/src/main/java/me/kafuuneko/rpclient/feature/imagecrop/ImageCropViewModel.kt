package me.kafuuneko.rpclient.feature.imagecrop

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagecrop.model.ImageCropTransform
import me.kafuuneko.rpclient.feature.imagecrop.presentation.CropMaskShape
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiIntent
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 协调裁剪图片解码、交互变换和方形头像文件生成。 */
class ImageCropViewModel : CoreViewModelWithEvent<ImageCropUiIntent, ImageCropUiState>(
    initStatus = ImageCropUiState.None
), KoinComponent {
    private val mFileRepository by inject<FileRepository>()
    private var mSourceBitmap: Bitmap? = null

    /**
     * 处理初始化意图，异步解码目标图片并构建初始正方形裁剪变换。
     */
    @UiIntentObserver(ImageCropUiIntent.Init::class)
    private suspend fun onInit(intent: ImageCropUiIntent.Init) {
        if (!isStateOf<ImageCropUiState.None>()) return
        val sourceUri = intent.sourceUri
        // 校验输入 URI 是否有效
        if (sourceUri == null) {
            ImageCropUiState.Failed(ImageCropUiState.None).setup()
            return
        }
        ImageCropUiState.Loading.setup()
        // 异步解码图片 Bitmap 并做大图缩放优化
        val bitmap = runCatching {
            mFileRepository.loadBitmapForCrop(sourceUri)
        }.getOrNull()
        if (bitmap == null) {
            ImageCropUiState.Failed(ImageCropUiState.Loading).setup()
            return
        }
        mSourceBitmap = bitmap
        // 构建初始变换状态并切入正常编辑界面
        ImageCropUiState.Normal(
            image = bitmap.asImageBitmap(),
            transform = ImageCropTransform(bitmap.width.toFloat() / bitmap.height.toFloat()),
        ).setup()
    }

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

    @UiIntentObserver(ImageCropUiIntent.RotateRight::class)
    private fun onRotateRight() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        uiState.copy(transform = uiState.transform.rotateRight()).setup()
    }

    @UiIntentObserver(ImageCropUiIntent.FlipHorizontal::class)
    private fun onFlipHorizontal() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        uiState.copy(transform = uiState.transform.flipHorizontal()).setup()
    }

    @UiIntentObserver(ImageCropUiIntent.ToggleMaskShape::class)
    private fun onToggleMaskShape() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        val nextShape = if (uiState.maskShape == CropMaskShape.Squircle) {
            CropMaskShape.Circle
        } else {
            CropMaskShape.Squircle
        }
        uiState.copy(maskShape = nextShape).setup()
    }

    @UiIntentObserver(ImageCropUiIntent.Reset::class)
    private fun onReset() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        if (uiState.saving) return
        uiState.copy(transform = uiState.transform.reset()).setup()
    }

    /**
     * 处理裁剪确认意图，持久化正方形头像文件并通知调用方。
     */
    @UiIntentObserver(ImageCropUiIntent.Confirm::class)
    private suspend fun onConfirm() {
        val uiState = getOrNull<ImageCropUiState.Normal>() ?: return
        val bitmap = mSourceBitmap ?: return
        if (uiState.saving) return
        // 标记保存中状态
        uiState.copy(saving = true).setup()
        // 执行像素裁剪与文件持久化
        val fileUuid = runCatching {
            mFileRepository.saveSquareCrop(bitmap, uiState.transform.toSelection())
        }.getOrElse {
            uiState.copy(saving = false).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.image_crop_save_failed).tryEmit()
            return
        }
        // 发送成功事件并关闭页面
        ImageCropViewEvent.FinishWithResult(fileUuid).emitAndAwait()
        ImageCropUiState.Finished(uiState.copy(saving = false)).setup()
    }

    @UiIntentObserver(ImageCropUiIntent.Back::class)
    private fun onBack() {
        val uiState = uiStateFlow.value
        if (uiState is ImageCropUiState.Finished) return
        if (uiState is ImageCropUiState.Normal && uiState.saving) return
        ImageCropUiState.Finished(uiState).setup()
    }

    override fun onCleared() {
        mSourceBitmap?.takeIf { !it.isRecycled }?.recycle()
        mSourceBitmap = null
        super.onCleared()
    }
}
