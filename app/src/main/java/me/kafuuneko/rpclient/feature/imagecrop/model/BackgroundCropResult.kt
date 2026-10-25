package me.kafuuneko.rpclient.feature.imagecrop.model

import android.net.Uri
import me.kafuuneko.rpclient.model.ImageCropSelection

/** 背景裁剪只返回源 URI 与选区，最终编码和暂存仍由调用页的背景管线负责。 */
data class BackgroundCropResult(val sourceUri: Uri, val selection: ImageCropSelection)
