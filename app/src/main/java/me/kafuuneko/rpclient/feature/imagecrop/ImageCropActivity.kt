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
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropViewEvent
import me.kafuuneko.rpclient.feature.imagecrop.ui.ImageCropLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 正方形头像裁剪页面宿主，负责接收临时 URI 并将文件 UUID 返回调用页面。 */
class ImageCropActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ImageCropViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(uiState) {
            if (uiState is ImageCropUiState.Finished) finish()
        }
        ImageCropLayout(uiState = uiState, emit = { mViewModel.emit(this) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(ImageCropUiIntent.Init(intent.data))
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        if (viewEvent is ImageCropViewEvent.FinishWithResult) {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_FILE_UUID, viewEvent.fileUuid),
            )
            finish()
        }
    }

    companion object {
        private const val EXTRA_FILE_UUID = "extra_file_uuid"

        fun createIntent(context: Context, sourceUri: Uri) =
            Intent(context, ImageCropActivity::class.java).apply {
                data = sourceUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        fun getResultFileUuid(intent: Intent?): String? =
            intent?.getStringExtra(EXTRA_FILE_UUID)?.takeIf { it.isNotBlank() }
    }
}
