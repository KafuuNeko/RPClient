package me.kafuuneko.rpclient.feature.imagecrop.presentation

import android.net.Uri

/** 图片裁剪页的初始化、手势变换、旋转、翻转、形态切换、重置、确认与退出意图。 */
sealed class ImageCropUiIntent {
    data class Init(
        val sourceUri: Uri?,
        val purpose: ImageCropPurpose = ImageCropPurpose.Avatar,
        val screenAspectRatio: Float = 1f,
    ) : ImageCropUiIntent()

    data class SelectAspectRatio(val value: ImageCropAspectRatio) : ImageCropUiIntent()

    data object Back : ImageCropUiIntent()

    data class Transform(
        val panX: Float,
        val panY: Float,
        val zoomChange: Float
    ) : ImageCropUiIntent()

    data object RotateRight : ImageCropUiIntent()

    data object FlipHorizontal : ImageCropUiIntent()

    data object ToggleMaskShape : ImageCropUiIntent()

    data object Reset : ImageCropUiIntent()

    data object Confirm : ImageCropUiIntent()
}
