package me.kafuuneko.rpclient.feature.imagecrop.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.imagecrop.model.ImageCropTransform

/** 头像裁切蒙版预览形态。 */
enum class CropMaskShape {
    Squircle,

    Circle,

    Rectangle
}

/** 裁剪用途决定输出协议和可用构图工具，头像始终保存方形文件。 */
enum class ImageCropPurpose {
    Avatar,

    Background
}

/** 背景既可按当前屏幕构图，也可保留整张原图的宽高比。 */
enum class ImageCropAspectRatio {
    Screen,

    Original
}

/** 图片裁剪页状态树。 */
sealed class ImageCropUiState {
    data object None : ImageCropUiState()

    data class Loading(val purpose: ImageCropPurpose = ImageCropPurpose.Avatar) : ImageCropUiState()

    data class Normal(
        /** 已经加载或配置、可供界面使用的图像。 */
        val image: ImageBitmap,
        /** 当前图像的缩放、平移、旋转和翻转参数。 */
        val transform: ImageCropTransform,
        /** 裁剪界面覆盖在图像上的遮罩形状。 */
        val maskShape: CropMaskShape = CropMaskShape.Squircle,
        /** 当前页面是否正在保存数据。 */
        val saving: Boolean = false,
        val purpose: ImageCropPurpose = ImageCropPurpose.Avatar,
        val aspectRatio: ImageCropAspectRatio = ImageCropAspectRatio.Screen,
    ) : ImageCropUiState()

    data class Failed(val previous: ImageCropUiState) : ImageCropUiState()

    data class Finished(val previous: ImageCropUiState) : ImageCropUiState()
}
