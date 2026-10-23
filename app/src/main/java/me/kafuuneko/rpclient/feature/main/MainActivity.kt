package me.kafuuneko.rpclient.feature.main

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.imagecrop.ImageCropActivity
import me.kafuuneko.rpclient.feature.main.model.Route
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainViewEvent
import me.kafuuneko.rpclient.feature.main.ui.MainLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.GetContentWithMimeTypes
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 应用主页面宿主，承载首页与全局设置。 */
class MainActivity : CoreActivityWithEvent() {
    companion object {
        /** 启动路由使用枚举名称传递，未指定时进入主页。 */
        const val EXTRA_ROUTE = "route"
    }

    private val mViewModel by viewModels<MainViewModel>()

    /** 用户头像选择结果只用于打开裁剪页，Activity 不直接持久化 URI 或位图。 */
    private val mUserAvatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uri -> mImageCropLauncher.launch(ImageCropActivity.createIntent(this, uri)) }
        }

    /** 裁剪页完成文件保存后，将方形头像 UUID 交回设置状态。 */
    private val mImageCropLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        ImageCropActivity.getResultFileUuid(result.data)?.let { text ->
            mViewModel.emit(MainUiIntent.UserAvatarCropped(text))
        }
    }

    /** 对话文件只在 ViewModel 完成解析并由用户选择角色后才会写入数据库。 */
    private val mChatImportLauncher = registerForActivityResult(
        contract = GetContentWithMimeTypes(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        mViewModel.emit(MainUiIntent.ImportChatResult(uri))
    }

    /** 只将本次授权目录回传 ViewModel，不自动保存长期 URI 授权。 */
    private val mChatImageDirectoryLauncher = registerForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> mViewModel.emit(MainUiIntent.ChatImageDirectoryResult(uri)) }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is MainUiState.Finished) finish()
        }

        MainLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val routeName = intent.getStringExtra(EXTRA_ROUTE)
        val route = Route.entries.firstOrNull { it.name == routeName } ?: Route.Main
        mViewModel.emit(MainUiIntent.Init(route = route))
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(MainUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            MainViewEvent.OpenUserAvatarPicker -> mUserAvatarPickerLauncher.launch("image/*")
            MainViewEvent.OpenChatImageDirectory -> mChatImageDirectoryLauncher.launch(input = null)
            MainViewEvent.OpenChatImporter -> mChatImportLauncher.launch(
                input = arrayOf(
                    "application/x-ndjson",
                    "application/json",
                    "text/plain",
                    "application/octet-stream",
                ),
            )
        }
    }
}
