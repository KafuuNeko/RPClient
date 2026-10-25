package me.kafuuneko.rpclient.feature.imagecrop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiIntent
import me.kafuuneko.rpclient.feature.imagecrop.model.BackgroundCropResult
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropPurpose
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropViewEvent
import me.kafuuneko.rpclient.feature.imagecrop.ui.ImageCropLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.model.ImageCropSelection

/** 通用图片裁剪宿主，头像返回文件 UUID，背景返回源 URI 与归一化选区。 */
class ImageCropActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ImageCropViewModel>()

    /** 裁剪画布始终保持独立暗房底色，避免背景模糊干扰选区预览。 */
    override fun usesGeneralBackground(): Boolean = false

    /** 提供裁剪页需要宿主处理的结果事件。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 绑定裁剪状态，并在返回结果或取消后结束宿主。 */
    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(uiState) {
            if (uiState is ImageCropUiState.Finished) finish()
        }
        ImageCropLayout(uiState = uiState, emit = { mViewModel.emit(this) })
    }

    /** 将输入 URI 和用途交给 ViewModel，宿主不参与图片解码。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用调用窗口的比例，配置旋转后由保留的 ViewModel 维持同一构图。
        val purpose = if (intent.getBooleanExtra(EXTRA_BACKGROUND, false)) {
            ImageCropPurpose.Background
        } else {
            ImageCropPurpose.Avatar
        }
        mViewModel.emit(
            ImageCropUiIntent.Init(
                sourceUri = intent.data,
                purpose = purpose,
                screenAspectRatio = intent.getFloatExtra(EXTRA_SCREEN_ASPECT_RATIO, 1f),
            )
        )
    }

    /** 分发头像文件结果和背景选区结果，由系统结果 API 返回调用页。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        // 背景选区只携带轻量坐标，保留原 URI 的临时读取权限供调用页导入。
        when (viewEvent) {
            is ImageCropViewEvent.FinishWithResult -> {
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_FILE_UUID, viewEvent.fileUuid)
                )
                finish()
            }

            is ImageCropViewEvent.FinishWithBackground -> {
                val selection = viewEvent.result.selection
                val result = Intent().apply {
                    data = viewEvent.result.sourceUri
                    putExtra(
                        EXTRA_SELECTION, floatArrayOf(
                            selection.centerX, selection.centerY,
                            selection.widthFraction, selection.heightFraction,
                        )
                    )
                    putExtra(EXTRA_ROTATION, selection.rotationDegrees)
                    putExtra(EXTRA_FLIPPED, selection.isFlippedHorizontal)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_UUID = "extra_file_uuid"
        private const val EXTRA_BACKGROUND = "extra_background"
        private const val EXTRA_SCREEN_ASPECT_RATIO = "extra_screen_aspect_ratio"
        private const val EXTRA_SELECTION = "extra_selection"
        private const val EXTRA_ROTATION = "extra_rotation"
        private const val EXTRA_FLIPPED = "extra_flipped"

        /** 打开固定正方形头像裁剪，兼容现有个人头像与角色头像入口。 */
        fun createIntent(context: Context, sourceUri: Uri) =
            Intent(context, ImageCropActivity::class.java).apply {
                data = sourceUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        /** 打开矩形背景裁剪，屏幕构图使用当前窗口的宽高比。 */
        fun createBackgroundIntent(context: Context, sourceUri: Uri): Intent {
            val configuration = context.resources.configuration
            val aspectRatio =
                configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
            return createIntent(context, sourceUri)
                .putExtra(EXTRA_BACKGROUND, true)
                .putExtra(EXTRA_SCREEN_ASPECT_RATIO, aspectRatio)
        }

        /** 读取头像裁剪成功后的文件索引。 */
        fun getResultFileUuid(intent: Intent?): String? =
            intent?.getStringExtra(EXTRA_FILE_UUID)?.takeIf { it.isNotBlank() }

        /** 校验背景结果中的归一化选区，取消或无效结果不修改调用页草稿。 */
        fun getBackgroundResult(intent: Intent?): BackgroundCropResult? {
            // 所有字段完整且数值有效时才交付选区，避免不完整结果覆盖旧草稿。
            val uri = intent?.data ?: return null
            val values =
                intent.getFloatArrayExtra(EXTRA_SELECTION)?.takeIf { it.size == 4 } ?: return null
            return runCatching {
                BackgroundCropResult(
                    sourceUri = uri,
                    selection = ImageCropSelection(
                        centerX = values[0],
                        centerY = values[1],
                        widthFraction = values[2],
                        heightFraction = values[3],
                        rotationDegrees = intent.getIntExtra(EXTRA_ROTATION, 0),
                        isFlippedHorizontal = intent.getBooleanExtra(EXTRA_FLIPPED, false),
                    ),
                )
            }.getOrNull()
        }
    }
}
