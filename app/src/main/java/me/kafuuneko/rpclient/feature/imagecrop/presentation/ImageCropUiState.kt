package me.kafuuneko.rpclient.feature.imagecrop.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.imagecrop.model.ImageCropTransform

/** 头像裁切蒙版预览形态。 */
enum class CropMaskShape {
    Squircle,
    Circle
}

/** 图片裁剪页状态树。 */
sealed class ImageCropUiState {
    data object None : ImageCropUiState()
    data object Loading : ImageCropUiState()

    data class Normal(
        /** 已经加载或配置、可供界面使用的图像。 */
        val image: ImageBitmap,
        /** 当前图像的缩放、平移、旋转和翻转参数。 */
        val transform: ImageCropTransform,
        /** 裁剪界面覆盖在图像上的遮罩形状。 */
        val maskShape: CropMaskShape = CropMaskShape.Squircle,
        /** 当前页面是否正在保存数据。 */
        val saving: Boolean = false
    ) : ImageCropUiState()

    data class Failed(val previous: ImageCropUiState) : ImageCropUiState()
    data class Finished(val previous: ImageCropUiState) : ImageCropUiState()
}
